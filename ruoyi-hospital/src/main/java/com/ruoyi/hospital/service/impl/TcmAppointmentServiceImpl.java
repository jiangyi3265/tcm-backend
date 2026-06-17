package com.ruoyi.hospital.service.impl;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmClinicSetting;
import com.ruoyi.hospital.domain.TcmRoom;
import com.ruoyi.hospital.domain.TcmServiceType;
import com.ruoyi.hospital.mapper.TcmAppointmentMapper;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.mapper.TcmRoomMapper;
import com.ruoyi.hospital.mapper.TcmServiceTypeMapper;
import com.ruoyi.hospital.service.ITcmAppointmentService;
import com.ruoyi.hospital.utils.PayloadUtils;
import com.ruoyi.system.mapper.SysUserMapper;

@Service
public class TcmAppointmentServiceImpl implements ITcmAppointmentService
{
    private static final DateTimeFormatter MYSQL_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId CLINIC_ZONE = ZoneId.of("America/Toronto");
    private static final List<String> VALID_STATUSES = Arrays.asList("booked", "confirmed", "completed", "cancelled");
    private static final int SLOT_MINUTES = 30;
    private static final int DEFAULT_SLOT_STEP_MINUTES = 10;

    @Autowired
    private TcmAppointmentMapper appointmentMapper;

    @Autowired
    private TcmClinicSettingMapper settingMapper;

    @Autowired
    private TcmRoomMapper roomMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private TcmServiceTypeMapper serviceTypeMapper;

    @Override
    public List<TcmAppointment> selectTcmAppointmentList(TcmAppointment appointment)
    {
        return appointmentMapper.selectTcmAppointmentList(appointment);
    }

    @Override
    public TcmAppointment selectTcmAppointmentById(String id)
    {
        return appointmentMapper.selectTcmAppointmentById(id);
    }

    @Override
    public int insertTcmAppointment(TcmAppointment appointment)
    {
        if (appointment.getId() == null || appointment.getId().isEmpty())
        {
            appointment.setId(java.util.UUID.randomUUID().toString());
        }
        if (isTimeBlock(appointment))
        {
            // Time blocks skip scheduling logic (no room assignment, no slot availability check)
            validateTimeBlockFields(appointment);
        }
        else
        {
            prepareAppointmentScheduling(appointment, null);
            ensureSlotAvailable(appointment, null);
        }
        appointment.setCreateTime(DateUtils.getNowDate());
        return appointmentMapper.insertTcmAppointment(appointment);
    }

    @Override
    public int updateTcmAppointment(TcmAppointment appointment)
    {
        TcmAppointment existing = appointment != null && appointment.getId() != null
                ? appointmentMapper.selectTcmAppointmentById(appointment.getId())
                : null;
        if (isTimeBlock(appointment))
        {
            validateTimeBlockFields(appointment);
        }
        else if (hasSchedulingChanged(existing, appointment))
        {
            prepareAppointmentScheduling(appointment, appointment.getId());
            ensureSlotAvailable(appointment, appointment.getId());
        }
        return appointmentMapper.updateTcmAppointment(appointment);
    }

    @Override
    public TcmAppointment updateStatus(String id, String status)
    {
        if (!VALID_STATUSES.contains(status))
        {
            throw new ServiceException("invalid appointment status: " + status);
        }
        TcmAppointment existing = appointmentMapper.selectTcmAppointmentById(id);
        if (existing == null)
        {
            throw new ServiceException("appointment not found");
        }
        existing.setStatus(status);
        appointmentMapper.updateTcmAppointment(existing);
        return existing;
    }

    @Override
    public Map<String, Object> checkSlot(
            String practitionerId,
            String roomId,
            String serviceType,
            String startTime,
            String endTime,
            String excludeId)
    {
        String normalizedStart = normalizeDateTime(startTime);
        String normalizedEnd = normalizeDateTime(endTime);
        LocalDateTime start = parseDateTime(normalizedStart);
        LocalDateTime end = parseDateTime(normalizedEnd);
        ServiceWindow window = resolveServiceWindow(serviceType);
        ServiceWindow effectiveWindow = withFullPractitionerTimeWhenSingleRoom(
                window,
                resolveRoomCandidates(window, roomId));
        LocalDateTime practitionerEnd = resolveRequestedPractitionerEnd(start, end, effectiveWindow);

        Map<String, Object> result = new HashMap<>();
        List<String> conflicts = new ArrayList<>();
        boolean practitionerConflict = false;
        boolean roomConflict = false;

        String timeRangeConflict = validateAppointmentTimeRange(normalizedStart, normalizedEnd);
        if (timeRangeConflict != null)
        {
            conflicts.add(timeRangeConflict);
            result.put("available", false);
            result.put("conflicts", conflicts);
            result.put("practitionerConflict", true);
            result.put("roomConflict", false);
            return result;
        }

        if (practitionerId != null && !practitionerId.isEmpty())
        {
            String workingHoursConflict = validateWorkingHours(
                    practitionerId,
                    normalizedStart,
                    formatDateTime(practitionerEnd != null ? practitionerEnd : end));
            if (workingHoursConflict != null)
            {
                practitionerConflict = true;
                conflicts.add(workingHoursConflict);
            }
        }

        for (TcmAppointment apt : findAppointmentConflicts(
                practitionerId,
                null,
                start,
                practitionerEnd != null ? practitionerEnd : end,
                excludeId,
                effectiveWindow,
                true))
        {
            practitionerConflict = true;
            conflicts.add("Practitioner time conflict: " + apt.getStartTime() + " - " + apt.getEndTime());
        }

        for (TcmAppointment apt : findAppointmentConflicts(
                null,
                roomId,
                start,
                end,
                excludeId,
                effectiveWindow,
                false))
        {
            roomConflict = true;
            conflicts.add("Room time conflict: " + apt.getStartTime() + " - " + apt.getEndTime());
        }

        result.put("available", conflicts.isEmpty());
        result.put("conflicts", conflicts);
        result.put("practitionerConflict", practitionerConflict);
        result.put("roomConflict", roomConflict);
        if (conflicts.isEmpty())
        {
            result.put("assignedPractitionerId", practitionerId);
            result.put("assignedRoomId", roomId);
        }
        return result;
    }

    @Override
    public Map<String, Object> getAvailability(
            String date,
            String serviceType,
            String practitionerId,
            String roomId,
            String excludeId)
    {
        LocalDate targetDate = parseDate(date);
        if (targetDate == null)
        {
            throw new ServiceException("invalid date");
        }
        ServiceWindow window = requireServiceWindow(serviceType);
        boolean hasSelectedPractitioner = practitionerId != null && !practitionerId.trim().isEmpty();
        PractitionerCandidate selectedPractitioner = hasSelectedPractitioner
                ? findPractitionerCandidate(practitionerId)
                : null;
        List<PractitionerCandidate> practitioners = hasSelectedPractitioner
                ? (selectedPractitioner == null ? new ArrayList<>() : new ArrayList<>(Collections.singletonList(selectedPractitioner)))
                : listPractitionerCandidates(serviceType);
        ScheduleContext scheduleContext = buildScheduleContext(
                practitioners,
                targetDate,
                targetDate,
                window,
                roomId,
                excludeId);
        int slotStepMinutes = hasSelectedPractitioner
                ? resolveSlotStepMinutes(window, practitionerId, scheduleContext.roomCandidates)
                : resolveAggregatedSlotStepMinutes(window, practitioners, scheduleContext.roomCandidates);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", targetDate.toString());
        result.put("serviceType", serviceType);
        result.put("duration", window.durationMinutes);
        result.put("practitionerBusyMinutes", window.practitionerBusyMinutes);
        result.put("slotStepMinutes", slotStepMinutes);

        if (hasSelectedPractitioner)
        {
            result.put("slots", selectedPractitioner == null
                    ? new ArrayList<>()
                    : buildPractitionerAvailability(selectedPractitioner, targetDate, window, roomId, excludeId, slotStepMinutes, scheduleContext));
            return result;
        }

        result.put("slots", buildAggregatedAvailability(
                practitioners,
                targetDate,
                window,
                roomId,
                excludeId,
                slotStepMinutes,
                scheduleContext));
        return result;
    }

    @Override
    public Map<String, Object> getWeeklySchedule(String date, String serviceType, String practitionerId, String roomId)
    {
        LocalDate targetDate = parseDate(date);
        if (targetDate == null)
        {
            throw new ServiceException("invalid date");
        }

        ServiceWindow window = requireServiceWindow(serviceType);
        boolean hasSelectedPractitioner = practitionerId != null && !practitionerId.trim().isEmpty();
        PractitionerCandidate selectedPractitioner = hasSelectedPractitioner
                ? findPractitionerCandidate(practitionerId)
                : null;
        if (hasSelectedPractitioner)
        {
            if (selectedPractitioner == null)
            {
                throw new ServiceException("practitioner not found");
            }
            if (!supportsService(selectedPractitioner.profile, serviceType))
            {
                throw new ServiceException("selected practitioner cannot provide this service");
            }
        }
        List<PractitionerCandidate> practitioners = hasSelectedPractitioner
                ? new ArrayList<>(Collections.singletonList(selectedPractitioner))
                : listPractitionerCandidates(serviceType);
        LocalDate weekStart = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        ScheduleContext scheduleContext = buildScheduleContext(
                practitioners,
                weekStart,
                weekStart.plusDays(6),
                window,
                roomId,
                null);
        int slotStepMinutes = hasSelectedPractitioner
                ? resolveSlotStepMinutes(window, practitionerId, scheduleContext.roomCandidates)
                : resolveAggregatedSlotStepMinutes(window, practitioners, scheduleContext.roomCandidates);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", targetDate.toString());
        result.put("weekStart", weekStart.toString());
        result.put("weekEnd", weekStart.plusDays(6).toString());
        result.put("serviceType", serviceType);
        result.put("roomId", roomId);
        result.put("duration", window.durationMinutes);
        result.put("practitionerBusyMinutes", window.practitionerBusyMinutes);
        result.put("slotMinutes", SLOT_MINUTES);
        result.put("slotStepMinutes", slotStepMinutes);

        List<Map<String, Object>> days = new ArrayList<>();
        if (hasSelectedPractitioner)
        {
            result.put("practitionerId", practitionerId);
            for (int offset = 0; offset < 7; offset++)
            {
                days.add(buildWeeklyScheduleDay(
                        selectedPractitioner,
                        weekStart.plusDays(offset),
                        window,
                        roomId,
                        slotStepMinutes,
                        scheduleContext));
            }
        }
        else
        {
            result.put("practitionerId", null);
            for (int offset = 0; offset < 7; offset++)
            {
                days.add(buildAggregatedWeeklyScheduleDay(
                        practitioners,
                        weekStart.plusDays(offset),
                        window,
                        roomId,
                        slotStepMinutes,
                        scheduleContext));
            }
        }
        result.put("days", days);
        return result;
    }

    @Override
    public TcmAppointment selectTcmAppointmentByIntakeToken(String intakeToken)
    {
        return appointmentMapper.selectTcmAppointmentByIntakeToken(intakeToken);
    }

    private void prepareAppointmentScheduling(TcmAppointment appointment, String excludeId)
    {
        if (appointment == null)
        {
            return;
        }
        ServiceWindow window = requireServiceWindow(appointment.getServiceType());
        String timeRangeConflict = validateAppointmentTimeRange(appointment.getStartTime(), appointment.getEndTime());
        if (timeRangeConflict != null)
        {
            throw new ServiceException(timeRangeConflict);
        }
        SlotAssignment assignment = resolveAppointmentAssignment(
                window,
                appointment.getPractitionerId(),
                appointment.getRoomId(),
                appointment.getStartTime(),
                excludeId);
        if (assignment == null)
        {
            if (appointment.getPractitionerId() == null || appointment.getPractitionerId().trim().isEmpty())
            {
                throw new ServiceException("no practitioner is available for the selected slot");
            }
            throw new ServiceException("appointment slot is unavailable");
        }

        if ((appointment.getPractitionerId() == null || appointment.getPractitionerId().trim().isEmpty())
                && assignment.practitionerId != null)
        {
            appointment.setPractitionerId(assignment.practitionerId);
        }
        if ((appointment.getRoomId() == null || appointment.getRoomId().trim().isEmpty())
                && assignment.roomId != null)
        {
            appointment.setRoomId(assignment.roomId);
        }
    }

    private List<Map<String, Object>> buildPractitionerAvailability(
            PractitionerCandidate practitioner,
            LocalDate targetDate,
            ServiceWindow window,
            String roomId,
            String excludeId,
            int slotStepMinutes,
            ScheduleContext scheduleContext)
    {
        List<Map<String, Object>> slots = new ArrayList<>();
        if (practitioner == null || window == null || !supportsService(practitioner.profile, window.serviceType))
        {
            return slots;
        }

        int scanStep = Math.max(1, slotStepMinutes);
        ServiceWindow effectiveWindow = withFullPractitionerTimeWhenSingleRoom(
                window,
                scheduleContext != null ? scheduleContext.roomCandidates : resolveRoomCandidates(window, roomId));
        int practBusy = effectiveWindow.resolvePractitionerBusyMinutes(practitioner.profile);

        for (TimeRange range : extractWorkingRanges(practitioner.profile, toWeekdayKey(targetDate.getDayOfWeek())))
        {
            LocalDateTime start = targetDate.atTime(range.start);
            LocalDateTime lastStart = targetDate.atTime(range.end).minusMinutes(practBusy);
            if (lastStart.isBefore(start))
            {
                continue;
            }
            for (LocalDateTime current = start; !current.isAfter(lastStart); current = current.plusMinutes(scanStep))
            {
                SlotEvaluation slotEvaluation = evaluateServiceSlot(
                        window,
                        practitioner,
                        roomId,
                        current,
                        excludeId,
                        scheduleContext);
                if (!slotEvaluation.available)
                {
                    continue;
                }
                Map<String, Object> slot = new LinkedHashMap<>();
                slot.put("label", current.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                slot.put("startTime", formatDateTime(current));
                slot.put("endTime", formatDateTime(current.plusMinutes(window.durationMinutes)));
                slot.put("practitionerId", practitioner.id);
                slot.put("assignedPractitionerId", practitioner.id);
                slot.put("roomId", slotEvaluation.roomId);
                slot.put("availablePractitionerIds", new ArrayList<>(Collections.singletonList(practitioner.id)));
                slot.put("availableCount", 1);
                slots.add(slot);
            }
        }

        slots.sort(Comparator.comparing(slot -> String.valueOf(slot.get("startTime"))));
        return slots;
    }

    private List<Map<String, Object>> buildAggregatedAvailability(
            List<PractitionerCandidate> practitioners,
            LocalDate day,
            ServiceWindow window,
            String roomId,
            String excludeId,
            int slotStepMinutes,
            ScheduleContext scheduleContext)
    {
        List<Map<String, Object>> slots = new ArrayList<>();
        if (window == null || practitioners == null || practitioners.isEmpty())
        {
            return slots;
        }
        int scanStep = Math.max(1, slotStepMinutes);
        for (int minute = 0; minute < 24 * 60; minute += scanStep)
        {
            LocalDateTime slotStart = day.atStartOfDay().plusMinutes(minute);
            LinkedHashSet<String> availablePractitionerIds = new LinkedHashSet<>();
            String assignedPractitionerId = null;
            String assignedRoomId = null;

            for (PractitionerCandidate practitioner : practitioners)
            {
                SlotEvaluation evaluation = evaluateServiceSlot(
                        window,
                        practitioner,
                        roomId,
                        slotStart,
                        excludeId,
                        scheduleContext);
                if (!evaluation.available)
                {
                    continue;
                }
                availablePractitionerIds.add(practitioner.id);
                if (assignedPractitionerId == null)
                {
                    assignedPractitionerId = practitioner.id;
                    assignedRoomId = evaluation.roomId;
                }
            }

            if (availablePractitionerIds.isEmpty())
            {
                continue;
            }

            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("label", slotStart.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            slot.put("startTime", formatDateTime(slotStart));
            slot.put("endTime", formatDateTime(slotStart.plusMinutes(window.durationMinutes)));
            slot.put("available", true);
            slot.put("assignedPractitionerId", assignedPractitionerId);
            slot.put("roomId", assignedRoomId);
            slot.put("availablePractitionerIds", new ArrayList<>(availablePractitionerIds));
            slot.put("availableCount", availablePractitionerIds.size());
            slot.put("status", "available");
            slot.put("state", "bookable");
            slots.add(slot);
        }
        return slots;
    }

    private List<PractitionerCandidate> listPractitionerCandidates(String serviceType)
    {
        List<PractitionerCandidate> practitioners = new ArrayList<>();
        List<Long> userIds = userMapper.selectActiveUserIds();
        for (Long userId : userIds)
        {
            if (userId == null || userId < 1L)
            {
                continue;
            }
            SysUser practitioner = userMapper.selectUserById(userId);
            if (practitioner == null || !isActiveUser(practitioner) || !hasPractitionerRole(practitioner))
            {
                continue;
            }
            JSONObject profile = parseProfile(practitioner.getRemark());
            if (!supportsService(profile, serviceType))
            {
                continue;
            }
            practitioners.add(new PractitionerCandidate(
                    String.valueOf(practitioner.getUserId()),
                    practitioner.getNickName(),
                    profile,
                    resolveSortOrder(profile, practitioner.getUserId())));
        }
        practitioners.sort(Comparator
                .comparingInt((PractitionerCandidate candidate) -> candidate.sortOrder)
                .thenComparing(candidate -> candidate.name == null ? "" : candidate.name)
                .thenComparing(candidate -> candidate.id));
        return practitioners;
    }

    private Map<String, Object> buildWeeklyScheduleDay(
            PractitionerCandidate practitioner,
            LocalDate day,
            ServiceWindow window,
            String roomId,
            int slotStepMinutes,
            ScheduleContext scheduleContext)
    {
        List<Map<String, Object>> slots = new ArrayList<>();
        List<TimeRange> ranges = extractWorkingRanges(practitioner.profile, toWeekdayKey(day.getDayOfWeek()));
        int scanStep = Math.max(1, slotStepMinutes);
        ServiceWindow effectiveWindow = withFullPractitionerTimeWhenSingleRoom(
                window,
                scheduleContext != null ? scheduleContext.roomCandidates : resolveRoomCandidates(window, roomId));

        for (int minute = 0; minute < 24 * 60; minute += scanStep)
        {
            LocalDateTime slotStart = day.atStartOfDay().plusMinutes(minute);
            LocalDateTime slotEnd = slotStart.plusMinutes(window.durationMinutes);
            int busyMinutes = effectiveWindow.resolvePractitionerBusyMinutes(practitioner.profile);
            LocalDateTime practitionerEnd = slotStart.plusMinutes(busyMinutes);
            boolean working = isWithinWorkingRanges(ranges, day, slotStart, practitionerEnd);
            boolean occupied = false;
            boolean available = false;
            String status = "off";
            String assignedRoomId = null;
            if (working)
            {
                SlotEvaluation slotEvaluation = evaluateServiceSlot(
                        window,
                        practitioner,
                        roomId,
                        slotStart,
                        null,
                        scheduleContext);
                available = slotEvaluation.available;
                occupied = slotEvaluation.occupied;
                status = available ? "available" : occupied ? "booked" : "working";
                assignedRoomId = slotEvaluation.roomId;
            }

            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("time", slotStart.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            slot.put("startTime", formatDateTime(slotStart));
            slot.put("endTime", formatDateTime(slotEnd));
            slot.put("working", working);
            slot.put("occupied", occupied);
            slot.put("available", available);
            slot.put("status", status);
            slot.put("state", !working ? "off" : occupied ? "occupied" : available ? "bookable" : "working");
            slot.put("roomId", available ? assignedRoomId : null);
            slots.add(slot);
        }

        Map<String, Object> dayResult = new LinkedHashMap<>();
        dayResult.put("date", day.toString());
        dayResult.put("weekday", toWeekdayKey(day.getDayOfWeek()));
        dayResult.put("slots", slots);
        return dayResult;
    }

    private Map<String, Object> buildAggregatedWeeklyScheduleDay(
            List<PractitionerCandidate> practitioners,
            LocalDate day,
            ServiceWindow window,
            String roomId,
            int slotStepMinutes,
            ScheduleContext scheduleContext)
    {
        List<Map<String, Object>> slots = new ArrayList<>();
        int scanStep = Math.max(1, slotStepMinutes);
        for (int minute = 0; minute < 24 * 60; minute += scanStep)
        {
            LocalDateTime slotStart = day.atStartOfDay().plusMinutes(minute);
            LocalDateTime slotEnd = slotStart.plusMinutes(window.durationMinutes);
            Set<String> availablePractitionerIds = new LinkedHashSet<>();
            String assignedPractitionerId = null;
            String assignedRoomId = null;

            for (PractitionerCandidate practitioner : practitioners)
            {
                SlotEvaluation evaluation = evaluateServiceSlot(
                        window,
                        practitioner,
                        roomId,
                        slotStart,
                        null,
                        scheduleContext);
                if (evaluation.available)
                {
                    availablePractitionerIds.add(practitioner.id);
                    if (assignedPractitionerId == null)
                    {
                        assignedPractitionerId = practitioner.id;
                        assignedRoomId = evaluation.roomId;
                    }
                }
            }

            if (availablePractitionerIds.isEmpty())
            {
                continue;
            }

            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("time", slotStart.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            slot.put("startTime", formatDateTime(slotStart));
            slot.put("endTime", formatDateTime(slotEnd));
            slot.put("available", true);
            slot.put("assignedPractitionerId", assignedPractitionerId);
            slot.put("roomId", assignedRoomId);
            slot.put("availablePractitionerIds", new ArrayList<>(availablePractitionerIds));
            slot.put("availableCount", availablePractitionerIds.size());
            slot.put("status", "available");
            slot.put("state", "bookable");
            slots.add(slot);
        }

        Map<String, Object> dayResult = new LinkedHashMap<>();
        dayResult.put("date", day.toString());
        dayResult.put("weekday", toWeekdayKey(day.getDayOfWeek()));
        dayResult.put("slots", slots);
        return dayResult;
    }

    private boolean isWithinWorkingRanges(List<TimeRange> ranges, LocalDate day, LocalDateTime start, LocalDateTime end)
    {
        if (ranges == null || ranges.isEmpty() || day == null || start == null || end == null)
        {
            return false;
        }
        if (!day.equals(start.toLocalDate()) || !day.equals(end.toLocalDate()))
        {
            return false;
        }
        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = end.toLocalTime();
        for (TimeRange range : ranges)
        {
            if (!startTime.isBefore(range.start) && !endTime.isAfter(range.end))
            {
                return true;
            }
        }
        return false;
    }

    private PractitionerCandidate findPractitionerCandidate(String practitionerId)
    {
        if (practitionerId == null || practitionerId.trim().isEmpty())
        {
            return null;
        }
        try
        {
            SysUser practitioner = userMapper.selectUserById(Long.valueOf(practitionerId));
            if (practitioner == null || !isActiveUser(practitioner) || !hasPractitionerRole(practitioner))
            {
                return null;
            }
            JSONObject profile = parseProfile(practitioner.getRemark());
            return new PractitionerCandidate(
                    String.valueOf(practitioner.getUserId()),
                    practitioner.getNickName(),
                    profile,
                    resolveSortOrder(profile, practitioner.getUserId()));
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private boolean supportsService(JSONObject profile, String serviceType)
    {
        if (serviceType == null || serviceType.trim().isEmpty())
        {
            return true;
        }
        List<String> serviceKeys = extractStringList(profile != null ? profile.get("serviceKeys") : null);
        return serviceKeys.isEmpty() || serviceKeys.contains(serviceType);
    }

    private int resolveSortOrder(JSONObject profile, Long userId)
    {
        Integer explicitSortOrder = parseIntValue(profile != null ? profile.get("practitionerSortOrder") : null);
        if (explicitSortOrder != null)
        {
            return explicitSortOrder;
        }
        return userId != null ? userId.intValue() : Integer.MAX_VALUE;
    }

    private void ensureSlotAvailable(TcmAppointment appointment, String excludeId)
    {
        if (appointment == null)
        {
            return;
        }
        ServiceWindow window = requireServiceWindow(appointment.getServiceType());
        SlotAssignment assignment = resolveAppointmentAssignment(
                window,
                appointment.getPractitionerId(),
                appointment.getRoomId(),
                appointment.getStartTime(),
                excludeId);
        if (assignment == null)
        {
            throw new ServiceException("appointment slot is unavailable");
        }
        if ((appointment.getPractitionerId() == null || appointment.getPractitionerId().trim().isEmpty())
                && assignment.practitionerId != null)
        {
            appointment.setPractitionerId(assignment.practitionerId);
        }
        if ((appointment.getRoomId() == null || appointment.getRoomId().trim().isEmpty())
                && assignment.roomId != null)
        {
            appointment.setRoomId(assignment.roomId);
        }
    }

    private ServiceWindow requireServiceWindow(String serviceType)
    {
        TcmServiceType config = requireServiceType(serviceType);
        int duration = config.getDuration() != null ? config.getDuration() : 0;
        if (duration <= 0)
        {
            throw new ServiceException("service duration is invalid");
        }
        String rawPractitionerTime = config.getPractitionerTime();
        int practitionerBusyMinutes = normalizePractitionerBusyMinutes(config, duration, null);
        String requiredTag = config.getRequiredTag();
        if (requiredTag != null)
        {
            requiredTag = requiredTag.trim();
            if (requiredTag.isEmpty())
            {
                requiredTag = null;
            }
        }
        return new ServiceWindow(serviceType, duration, practitionerBusyMinutes, isRoomRequired(config), requiredTag, rawPractitionerTime);
    }

    private ServiceWindow resolveServiceWindow(String serviceType)
    {
        if (serviceType == null || serviceType.trim().isEmpty())
        {
            return null;
        }
        return requireServiceWindow(serviceType);
    }

    private int normalizePractitionerBusyMinutes(TcmServiceType config, int durationMinutes)
    {
        return normalizePractitionerBusyMinutes(config, durationMinutes, null);
    }

    private int normalizePractitionerBusyMinutes(TcmServiceType config, int durationMinutes, JSONObject practitionerProfile)
    {
        String configured = config != null ? config.getPractitionerTime() : null;
        if (configured == null || configured.trim().isEmpty())
        {
            return durationMinutes;
        }
        configured = configured.trim();
        if ("overlap1".equals(configured))
        {
            int overlap1 = practitionerProfile != null ? parseIntOrDefault(practitionerProfile.get("overlap1"), 20) : 20;
            return Math.min(overlap1, durationMinutes);
        }
        if ("overlap2".equals(configured))
        {
            int overlap2 = practitionerProfile != null ? parseIntOrDefault(practitionerProfile.get("overlap2"), 10) : 10;
            return Math.min(overlap2, durationMinutes);
        }
        try
        {
            int value = Integer.parseInt(configured);
            if (value <= 0 || value > durationMinutes)
            {
                return durationMinutes;
            }
            return value;
        }
        catch (NumberFormatException e)
        {
            return durationMinutes;
        }
    }

    private int parseIntOrDefault(Object value, int defaultValue)
    {
        if (value == null)
        {
            return defaultValue;
        }
        try
        {
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            return parsed > 0 ? parsed : defaultValue;
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    private int resolveSlotStepMinutes(ServiceWindow window, String practitionerId, List<TcmRoom> roomCandidates)
    {
        ServiceWindow effectiveWindow = withFullPractitionerTimeWhenSingleRoom(window, roomCandidates);
        if (effectiveWindow == null)
        {
            return DEFAULT_SLOT_STEP_MINUTES;
        }
        if (effectiveWindow.forceFullPractitionerTime)
        {
            return Math.max(1, effectiveWindow.durationMinutes);
        }
        PractitionerCandidate practitioner = findPractitionerCandidate(practitionerId);
        return Math.max(1, effectiveWindow.resolvePractitionerBusyMinutes(
                practitioner != null ? practitioner.profile : null));
    }

    private int resolveAggregatedSlotStepMinutes(
            ServiceWindow window,
            List<PractitionerCandidate> practitioners,
            List<TcmRoom> roomCandidates)
    {
        ServiceWindow effectiveWindow = withFullPractitionerTimeWhenSingleRoom(window, roomCandidates);
        if (effectiveWindow == null)
        {
            return DEFAULT_SLOT_STEP_MINUTES;
        }
        if (effectiveWindow.forceFullPractitionerTime)
        {
            return Math.max(1, effectiveWindow.durationMinutes);
        }
        int resolved = 0;
        if (practitioners != null)
        {
            for (PractitionerCandidate practitioner : practitioners)
            {
                if (practitioner == null || !supportsService(practitioner.profile, effectiveWindow.serviceType))
                {
                    continue;
                }
                int busyMinutes = effectiveWindow.resolvePractitionerBusyMinutes(practitioner.profile);
                if (busyMinutes > 0)
                {
                    resolved = resolved == 0 ? busyMinutes : Math.min(resolved, busyMinutes);
                }
            }
        }
        return Math.max(1, resolved > 0 ? resolved : effectiveWindow.practitionerBusyMinutes);
    }

    private List<TcmRoom> resolveRoomCandidates(ServiceWindow window, String preferredRoomId)
    {
        List<TcmRoom> rooms = new ArrayList<>();
        if (window == null)
        {
            return rooms;
        }
        if (preferredRoomId != null && !preferredRoomId.trim().isEmpty())
        {
            TcmRoom room = roomMapper.selectTcmRoomById(preferredRoomId);
            if (isActiveRoom(room) && supportsRequiredTag(room, window.requiredTag))
            {
                rooms.add(room);
            }
            return rooms;
        }
        if (!window.roomRequired)
        {
            return rooms;
        }
        List<TcmRoom> allRooms = roomMapper.selectTcmRoomList(new TcmRoom());
        for (TcmRoom room : allRooms)
        {
            if (isActiveRoom(room) && supportsRequiredTag(room, window.requiredTag))
            {
                rooms.add(room);
            }
        }
        rooms.sort(Comparator.comparing((TcmRoom room) -> room.getName() == null ? "" : room.getName())
                .thenComparing(room -> room.getId() == null ? "" : room.getId()));
        return rooms;
    }

    private ServiceWindow withFullPractitionerTimeWhenSingleRoom(ServiceWindow window, List<TcmRoom> roomCandidates)
    {
        if (window == null || !window.roomRequired || window.forceFullPractitionerTime)
        {
            return window;
        }
        if (roomCandidates != null && roomCandidates.size() == 1)
        {
            return window.withFullPractitionerTime();
        }
        return window;
    }

    private boolean isActiveRoom(TcmRoom room)
    {
        return room != null && room.getId() != null && !room.getId().trim().isEmpty()
                && (room.getIsActive() == null || room.getIsActive() == 1);
    }

    private boolean supportsRequiredTag(TcmRoom room, String requiredTag)
    {
        if (requiredTag == null || requiredTag.isEmpty())
        {
            return true;
        }
        if (room == null)
        {
            return false;
        }
        List<String> supportTags = PayloadUtils.parseStringList(room.getSupportTags());
        for (String tag : supportTags)
        {
            if (requiredTag.equalsIgnoreCase(tag))
            {
                return true;
            }
        }
        return false;
    }

    private SlotAssignment resolveAppointmentAssignment(
            ServiceWindow window,
            String practitionerId,
            String roomId,
            String startTime,
            String excludeId)
    {
        LocalDateTime start = parseDateTime(startTime);
        if (window == null || start == null)
        {
            return null;
        }

        List<PractitionerCandidate> practitioners = new ArrayList<>();
        if (practitionerId != null && !practitionerId.trim().isEmpty())
        {
            PractitionerCandidate selected = findPractitionerCandidate(practitionerId);
            if (selected != null)
            {
                practitioners.add(selected);
            }
        }
        else
        {
            practitioners.addAll(listPractitionerCandidates(window.serviceType));
        }

        for (PractitionerCandidate practitioner : practitioners)
        {
            if (practitioner == null || !supportsService(practitioner.profile, window.serviceType))
            {
                continue;
            }
            SlotEvaluation evaluation = evaluateServiceSlot(window, practitioner, roomId, start, excludeId);
            if (evaluation.available)
            {
                return new SlotAssignment(evaluation.practitionerId, evaluation.roomId);
            }
        }
        return null;
    }

    private SlotEvaluation evaluateServiceSlot(
            ServiceWindow window,
            PractitionerCandidate practitioner,
            String preferredRoomId,
            LocalDateTime slotStart,
            String excludeId)
    {
        return evaluateServiceSlot(window, practitioner, preferredRoomId, slotStart, excludeId, null);
    }

    private SlotEvaluation evaluateServiceSlot(
            ServiceWindow window,
            PractitionerCandidate practitioner,
            String preferredRoomId,
            LocalDateTime slotStart,
            String excludeId,
            ScheduleContext scheduleContext)
    {
        if (window == null || practitioner == null || slotStart == null)
        {
            return SlotEvaluation.unavailable("invalid slot", false);
        }
        if (!supportsService(practitioner.profile, window.serviceType))
        {
            return SlotEvaluation.unavailable("selected practitioner cannot provide this service", false);
        }

        List<TcmRoom> rooms = scheduleContext != null && scheduleContext.roomCandidates != null
                ? scheduleContext.roomCandidates
                : resolveRoomCandidates(window, preferredRoomId);
        ServiceWindow effectiveWindow = withFullPractitionerTimeWhenSingleRoom(window, rooms);
        LocalDateTime roomEnd = slotStart.plusMinutes(window.durationMinutes);
        int actualBusyMinutes = effectiveWindow.resolvePractitionerBusyMinutes(practitioner.profile);
        LocalDateTime practitionerEnd = slotStart.plusMinutes(actualBusyMinutes);
        if (scheduleContext != null && scheduleContext.appointmentsPreloaded)
        {
            List<TimeRange> workingRanges = extractWorkingRanges(
                    practitioner.profile,
                    toWeekdayKey(slotStart.getDayOfWeek()));
            if (!isWithinWorkingRanges(workingRanges, slotStart.toLocalDate(), slotStart, practitionerEnd))
            {
                return SlotEvaluation.unavailable("Selected time is outside practitioner working hours", false);
            }
            if (hasAppointmentConflict(
                    scheduleContext.practitionerAppointments.get(practitioner.id),
                    slotStart,
                    practitionerEnd,
                    excludeId,
                    effectiveWindow,
                    true))
            {
                return SlotEvaluation.unavailable("Practitioner time conflict", true);
            }
            if (!window.roomRequired && (preferredRoomId == null || preferredRoomId.trim().isEmpty()))
            {
                return SlotEvaluation.available(practitioner.id, null);
            }

            for (TcmRoom room : rooms)
            {
                if (!supportsRequiredTag(room, window.requiredTag))
                {
                    continue;
                }
                if (!hasAppointmentConflict(
                        scheduleContext.roomAppointments.get(room.getId()),
                        slotStart,
                        roomEnd,
                        excludeId,
                        effectiveWindow,
                        false))
                {
                    return SlotEvaluation.available(practitioner.id, room.getId());
                }
            }
            return SlotEvaluation.unavailable(
                    window.requiredTag != null ? "no room is available for the selected tag" : "no room is available for the selected slot",
                    false);
        }

        String workingHoursConflict = validateWorkingHours(
                practitioner.id,
                formatDateTime(slotStart),
                formatDateTime(practitionerEnd));
        if (workingHoursConflict != null)
        {
            return SlotEvaluation.unavailable(workingHoursConflict, false);
        }
        if (hasAppointmentConflict(practitioner.id, null, slotStart, practitionerEnd, excludeId, effectiveWindow, true))
        {
            return SlotEvaluation.unavailable("Practitioner time conflict", true);
        }

        if (!window.roomRequired && (preferredRoomId == null || preferredRoomId.trim().isEmpty()))
        {
            return SlotEvaluation.available(practitioner.id, null);
        }

        for (TcmRoom room : rooms)
        {
            if (!supportsRequiredTag(room, window.requiredTag))
            {
                continue;
            }
            if (!hasAppointmentConflict(null, room.getId(), slotStart, roomEnd, excludeId, effectiveWindow, false))
            {
                return SlotEvaluation.available(practitioner.id, room.getId());
            }
        }
        return SlotEvaluation.unavailable(
                window.requiredTag != null ? "no room is available for the selected tag" : "no room is available for the selected slot",
                false);
    }

    private boolean hasAppointmentConflict(
            String practitionerId,
            String roomId,
            LocalDateTime start,
            LocalDateTime end,
            String excludeId,
            ServiceWindow requestedWindow,
            boolean practitionerConflict)
    {
        return !findAppointmentConflicts(
                practitionerId,
                roomId,
                start,
                end,
                excludeId,
                requestedWindow,
                practitionerConflict).isEmpty();
    }

    private boolean hasAppointmentConflict(
            List<TcmAppointment> appointments,
            LocalDateTime start,
            LocalDateTime end,
            String excludeId,
            ServiceWindow requestedWindow,
            boolean practitionerConflict)
    {
        return !findAppointmentConflicts(
                appointments,
                start,
                end,
                excludeId,
                requestedWindow,
                practitionerConflict).isEmpty();
    }

    private boolean hasSchedulingChanged(TcmAppointment existing, TcmAppointment appointment)
    {
        if (appointment == null)
        {
            return false;
        }
        if (existing == null)
        {
            return true;
        }
        return !Objects.equals(normalizeDateTime(existing.getStartTime()), normalizeDateTime(appointment.getStartTime()))
                || !Objects.equals(normalizeDateTime(existing.getEndTime()), normalizeDateTime(appointment.getEndTime()))
                || !Objects.equals(existing.getPractitionerId(), appointment.getPractitionerId())
                || !Objects.equals(existing.getRoomId(), appointment.getRoomId())
                || !Objects.equals(existing.getServiceType(), appointment.getServiceType());
    }

    private String validateWorkingHours(String practitionerId, String startTime, String endTime)
    {
        LocalDateTime start = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);
        if (start == null || end == null)
        {
            return null;
        }
        String timeRangeConflict = validateAppointmentTimeRange(start, end);
        if (timeRangeConflict != null)
        {
            return timeRangeConflict;
        }

        PractitionerCandidate practitioner = findPractitionerCandidate(practitionerId);
        if (practitioner == null)
        {
            return "Practitioner not found";
        }

        List<TimeRange> ranges = extractWorkingRanges(practitioner.profile, toWeekdayKey(start.getDayOfWeek()));
        if (ranges.isEmpty())
        {
            return "Practitioner is not available on the selected day";
        }
        for (TimeRange range : ranges)
        {
            if (!start.toLocalTime().isBefore(range.start) && !end.toLocalTime().isAfter(range.end))
            {
                return null;
            }
        }
        return "Selected time is outside practitioner working hours";
    }

    private LocalDateTime resolveRequestedPractitionerEnd(LocalDateTime start, LocalDateTime end, ServiceWindow requestedWindow)
    {
        if (start == null)
        {
            return null;
        }
        if (requestedWindow == null)
        {
            return end;
        }
        int actualDuration = resolveDurationMinutes(start, end);
        int busyMinutes = requestedWindow.practitionerBusyMinutes;
        if (requestedWindow.forceFullPractitionerTime && actualDuration > 0)
        {
            busyMinutes = actualDuration;
        }
        if (actualDuration > 0)
        {
            busyMinutes = Math.min(busyMinutes, actualDuration);
        }
        return start.plusMinutes(Math.max(1, busyMinutes));
    }

    private List<TcmAppointment> findAppointmentConflicts(
            String practitionerId,
            String roomId,
            LocalDateTime start,
            LocalDateTime end,
            String excludeId,
            ServiceWindow requestedWindow,
            boolean practitionerConflict)
    {
        List<TcmAppointment> conflicts = new ArrayList<>();
        if (start == null || end == null)
        {
            return conflicts;
        }
        List<TcmAppointment> overlapping = appointmentMapper.selectOverlappingAppointments(
                practitionerId,
                roomId,
                formatDateTime(start),
                formatDateTime(end),
                excludeId);
        if (overlapping == null)
        {
            return conflicts;
        }
        for (TcmAppointment appointment : overlapping)
        {
            if (appointment == null
                    || "cancelled".equalsIgnoreCase(appointment.getStatus())
                    || (excludeId != null && excludeId.equals(appointment.getId())))
            {
                continue;
            }
            if (practitionerConflict && practitionerId != null && !Objects.equals(practitionerId, appointment.getPractitionerId()))
            {
                continue;
            }
            if (!practitionerConflict && roomId != null && !Objects.equals(roomId, appointment.getRoomId()))
            {
                continue;
            }
            LocalDateTime existingStart = parseDateTime(appointment.getStartTime());
            LocalDateTime existingEnd = practitionerConflict
                    ? resolveExistingPractitionerEnd(appointment, requestedWindow)
                    : parseDateTime(appointment.getEndTime());
            if (!isTimeOverlap(start, end, existingStart, existingEnd))
            {
                continue;
            }
            conflicts.add(appointment);
        }
        return conflicts;
    }

    private List<TcmAppointment> findAppointmentConflicts(
            List<TcmAppointment> appointments,
            LocalDateTime start,
            LocalDateTime end,
            String excludeId,
            ServiceWindow requestedWindow,
            boolean practitionerConflict)
    {
        List<TcmAppointment> conflicts = new ArrayList<>();
        if (appointments == null || start == null || end == null)
        {
            return conflicts;
        }
        for (TcmAppointment appointment : appointments)
        {
            if (appointment == null
                    || "cancelled".equalsIgnoreCase(appointment.getStatus())
                    || (excludeId != null && excludeId.equals(appointment.getId())))
            {
                continue;
            }
            LocalDateTime existingStart = parseDateTime(appointment.getStartTime());
            LocalDateTime existingEnd = practitionerConflict
                    ? resolveExistingPractitionerEnd(appointment, requestedWindow)
                    : parseDateTime(appointment.getEndTime());
            if (!isTimeOverlap(start, end, existingStart, existingEnd))
            {
                continue;
            }
            conflicts.add(appointment);
        }
        return conflicts;
    }

    private ScheduleContext buildScheduleContext(
            List<PractitionerCandidate> practitioners,
            LocalDate startDate,
            LocalDate endDate,
            ServiceWindow window,
            String preferredRoomId,
            String excludeId)
    {
        List<TcmRoom> roomCandidates = resolveRoomCandidates(window, preferredRoomId);
        Set<String> practitionerIds = new LinkedHashSet<>();
        if (practitioners != null)
        {
            for (PractitionerCandidate practitioner : practitioners)
            {
                if (practitioner != null && practitioner.id != null && !practitioner.id.trim().isEmpty())
                {
                    practitionerIds.add(practitioner.id);
                }
            }
        }
        Set<String> roomIds = new LinkedHashSet<>();
        for (TcmRoom room : roomCandidates)
        {
            if (room != null && room.getId() != null && !room.getId().trim().isEmpty())
            {
                roomIds.add(room.getId());
            }
        }

        LocalDateTime rangeStart = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime rangeEnd = endDate != null ? endDate.plusDays(1).atStartOfDay() : null;
        if (rangeStart == null || rangeEnd == null)
        {
            return new ScheduleContext(false, new HashMap<>(), new HashMap<>(), roomCandidates);
        }
        if (practitionerIds.isEmpty() && roomIds.isEmpty())
        {
            return new ScheduleContext(true, new HashMap<>(), new HashMap<>(), roomCandidates);
        }

        List<TcmAppointment> appointments = appointmentMapper.selectAppointmentsInRange(
                new ArrayList<>(practitionerIds),
                new ArrayList<>(roomIds),
                formatDateTime(rangeStart),
                formatDateTime(rangeEnd),
                excludeId);
        if (appointments == null)
        {
            return new ScheduleContext(false, new HashMap<>(), new HashMap<>(), roomCandidates);
        }

        Map<String, List<TcmAppointment>> practitionerAppointments = new HashMap<>();
        Map<String, List<TcmAppointment>> roomAppointments = new HashMap<>();
        for (TcmAppointment appointment : appointments)
        {
            if (appointment == null
                    || "cancelled".equalsIgnoreCase(appointment.getStatus())
                    || (excludeId != null && excludeId.equals(appointment.getId())))
            {
                continue;
            }
            LocalDateTime existingStart = parseDateTime(appointment.getStartTime());
            LocalDateTime existingEnd = parseDateTime(appointment.getEndTime());
            if (!isTimeOverlap(rangeStart, rangeEnd, existingStart, existingEnd))
            {
                continue;
            }
            if (appointment.getPractitionerId() != null && practitionerIds.contains(appointment.getPractitionerId()))
            {
                practitionerAppointments.computeIfAbsent(appointment.getPractitionerId(), key -> new ArrayList<>())
                        .add(appointment);
            }
            if (appointment.getRoomId() != null && roomIds.contains(appointment.getRoomId()))
            {
                roomAppointments.computeIfAbsent(appointment.getRoomId(), key -> new ArrayList<>())
                        .add(appointment);
            }
        }
        return new ScheduleContext(true, practitionerAppointments, roomAppointments, roomCandidates);
    }

    private LocalDateTime resolveExistingPractitionerEnd(TcmAppointment appointment, ServiceWindow requestedWindow)
    {
        LocalDateTime existingStart = parseDateTime(appointment != null ? appointment.getStartTime() : null);
        LocalDateTime existingEnd = parseDateTime(appointment != null ? appointment.getEndTime() : null);
        if (existingStart == null || existingEnd == null)
        {
            return existingEnd;
        }

        int actualDuration = resolveDurationMinutes(existingStart, existingEnd);
        int busyMinutes = actualDuration;
        if (requestedWindow != null && requestedWindow.forceFullPractitionerTime)
        {
            busyMinutes = actualDuration;
        }
        else if (appointment != null && appointment.getServiceType() != null && !appointment.getServiceType().trim().isEmpty())
        {
            TcmServiceType existingType = serviceTypeMapper.selectTcmServiceTypeByKey(appointment.getServiceType());
            if (existingType != null)
            {
                // Resolve overlap per practitioner
                JSONObject practitionerProfile = null;
                if (appointment.getPractitionerId() != null && !appointment.getPractitionerId().trim().isEmpty())
                {
                    PractitionerCandidate pc = findPractitionerCandidate(appointment.getPractitionerId());
                    if (pc != null) practitionerProfile = pc.profile;
                }
                busyMinutes = normalizePractitionerBusyMinutes(existingType, actualDuration, practitionerProfile);
            }
        }
        else if (requestedWindow != null)
        {
            busyMinutes = Math.min(actualDuration, requestedWindow.practitionerBusyMinutes);
        }
        return existingStart.plusMinutes(Math.max(1, busyMinutes));
    }

    private int resolveDurationMinutes(LocalDateTime start, LocalDateTime end)
    {
        if (start == null || end == null || !end.isAfter(start))
        {
            return 0;
        }
        return (int) Duration.between(start, end).toMinutes();
    }

    private boolean isTimeOverlap(LocalDateTime start, LocalDateTime end, LocalDateTime existingStart, LocalDateTime existingEnd)
    {
        return start != null
                && end != null
                && existingStart != null
                && existingEnd != null
                && end.isAfter(start)
                && existingEnd.isAfter(existingStart)
                && start.isBefore(existingEnd)
                && end.isAfter(existingStart);
    }

    private int resolvePractitionerInterval(String practitionerId, TcmClinicSetting globalSetting)
    {
        int defaultInterval = parseIntervalValue(globalSetting != null ? globalSetting.getSettingValue() : null);
        if (practitionerId == null || practitionerId.isEmpty())
        {
            return defaultInterval;
        }

        TcmClinicSetting practitionerIntervalsSetting = settingMapper.selectSettingByKey("practitionerIntervals");
        if (practitionerIntervalsSetting == null || practitionerIntervalsSetting.getSettingValue() == null)
        {
            return defaultInterval;
        }
        try
        {
            JSONObject data = JSON.parseObject(practitionerIntervalsSetting.getSettingValue());
            if (data == null)
            {
                return defaultInterval;
            }
            int practitionerInterval = parseIntervalValue(data.get(practitionerId));
            return practitionerInterval > 0 ? practitionerInterval : defaultInterval;
        }
        catch (Exception e)
        {
            return defaultInterval;
        }
    }

    private int parseIntervalValue(Object value)
    {
        if (value == null)
        {
            return 0;
        }
        try
        {
            return Integer.parseInt(String.valueOf(value).replace("\"", "").trim());
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    private String validatePractitionerInterval(
            String practitionerId,
            String startTime,
            String endTime,
            String excludeId,
            int intervalMinutes)
    {
        LocalDateTime start = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);
        if (start == null || end == null)
        {
            return null;
        }

        TcmAppointment query = new TcmAppointment();
        query.setPractitionerId(practitionerId);
        List<TcmAppointment> appointments = appointmentMapper.selectTcmAppointmentList(query);
        for (TcmAppointment appointment : appointments)
        {
            if (appointment == null
                    || "cancelled".equals(appointment.getStatus())
                    || !Objects.equals(practitionerId, appointment.getPractitionerId())
                    || (excludeId != null && excludeId.equals(appointment.getId())))
            {
                continue;
            }
            LocalDateTime existingStart = parseDateTime(appointment.getStartTime());
            LocalDateTime existingEnd = parseDateTime(appointment.getEndTime());
            if (existingStart == null || existingEnd == null)
            {
                continue;
            }
            if (hasIntervalConflict(start, end, existingStart, existingEnd, intervalMinutes))
            {
                return "Practitioner interval must be at least " + intervalMinutes + " minutes";
            }
        }
        return null;
    }

    private boolean hasIntervalConflict(
            LocalDateTime start,
            LocalDateTime end,
            LocalDateTime existingStart,
            LocalDateTime existingEnd,
            int intervalMinutes)
    {
        if (start.isBefore(existingEnd) && end.isAfter(existingStart))
        {
            return false;
        }
        if (!start.isBefore(existingEnd))
        {
            return Duration.between(existingEnd, start).toMinutes() < intervalMinutes;
        }
        if (!existingStart.isBefore(end))
        {
            return Duration.between(end, existingStart).toMinutes() < intervalMinutes;
        }
        return false;
    }

    private String validateAppointmentTimeRange(String startTime, String endTime)
    {
        return validateAppointmentTimeRange(parseDateTime(startTime), parseDateTime(endTime));
    }

    private String validateAppointmentTimeRange(LocalDateTime start, LocalDateTime end)
    {
        if (start == null || end == null)
        {
            return null;
        }
        if (!Objects.equals(start.toLocalDate(), end.toLocalDate()))
        {
            return "appointment must start and end on the same day";
        }
        if (!end.isAfter(start))
        {
            return "appointment end time must be after start time";
        }
        return null;
    }

    private TcmServiceType requireServiceType(String serviceType)
    {
        if (serviceType == null || serviceType.trim().isEmpty())
        {
            throw new ServiceException("service type is required");
        }
        TcmServiceType config = serviceTypeMapper.selectTcmServiceTypeByKey(serviceType);
        if (config == null)
        {
            throw new ServiceException("service type not found");
        }
        return config;
    }

    private boolean isRoomRequired(TcmServiceType config)
    {
        return config != null && config.getRoomRequired() != null && config.getRoomRequired() == 1;
    }

    private boolean isTimeBlock(TcmAppointment appointment)
    {
        return appointment != null && "time_block".equals(appointment.getServiceType());
    }

    private void validateTimeBlockFields(TcmAppointment appointment)
    {
        if (appointment.getPractitionerId() == null || appointment.getPractitionerId().trim().isEmpty())
        {
            throw new ServiceException("practitioner is required for time block");
        }
        String timeRangeConflict = validateAppointmentTimeRange(appointment.getStartTime(), appointment.getEndTime());
        if (timeRangeConflict != null)
        {
            throw new ServiceException(timeRangeConflict);
        }
    }

    private boolean isActiveUser(SysUser user)
    {
        return user != null && "0".equals(user.getStatus());
    }

    private boolean hasPractitionerRole(SysUser user)
    {
        if (user == null || user.getRoles() == null)
        {
            return false;
        }
        for (SysRole role : user.getRoles())
        {
            if (role == null || role.getRoleKey() == null)
            {
                continue;
            }
            String roleKey = role.getRoleKey().trim().toLowerCase();
            if ("practitioner".equals(roleKey) || "doctor".equals(roleKey))
            {
                return true;
            }
        }
        return false;
    }

    private JSONObject parseProfile(String remark)
    {
        try
        {
            return remark == null || remark.trim().isEmpty() ? new JSONObject() : JSON.parseObject(remark);
        }
        catch (Exception e)
        {
            return new JSONObject();
        }
    }

    private List<String> extractStringList(Object value)
    {
        List<String> values = new ArrayList<>();
        if (!(value instanceof List<?>))
        {
            return values;
        }
        for (Object item : (List<?>) value)
        {
            if (item == null)
            {
                continue;
            }
            String normalized = String.valueOf(item).trim();
            if (!normalized.isEmpty() && !values.contains(normalized))
            {
                values.add(normalized);
            }
        }
        return values;
    }

    private List<TimeRange> extractWorkingRanges(JSONObject profile, String weekdayKey)
    {
        List<TimeRange> ranges = new ArrayList<>();
        if (profile == null || weekdayKey == null)
        {
            return ranges;
        }
        JSONObject workingHours = profile.getJSONObject("workingHours");
        if (workingHours == null || workingHours.isEmpty())
        {
            return ranges;
        }
        Object rawRanges = workingHours.get(weekdayKey);
        if (rawRanges instanceof Map)
        {
            TimeRange single = toTimeRange((Map<?, ?>) rawRanges);
            if (single != null)
            {
                ranges.add(single);
            }
            return ranges;
        }
        if (!(rawRanges instanceof List<?>))
        {
            return ranges;
        }
        for (Object rawRange : (List<?>) rawRanges)
        {
            if (!(rawRange instanceof Map))
            {
                continue;
            }
            TimeRange range = toTimeRange((Map<?, ?>) rawRange);
            if (range != null)
            {
                ranges.add(range);
            }
        }
        ranges.sort(Comparator.comparing(range -> range.start));
        return ranges;
    }

    private TimeRange toTimeRange(Map<?, ?> rawRange)
    {
        if (rawRange == null)
        {
            return null;
        }
        LocalTime start = parseLocalTime(rawRange.get("start") != null ? String.valueOf(rawRange.get("start")) : null);
        LocalTime end = parseLocalTime(rawRange.get("end") != null ? String.valueOf(rawRange.get("end")) : null);
        if (start == null || end == null || !start.isBefore(end))
        {
            return null;
        }
        return new TimeRange(start, end);
    }

    private Integer parseIntValue(Object value)
    {
        if (value == null)
        {
            return null;
        }
        try
        {
            return Integer.parseInt(String.valueOf(value).trim());
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private String normalizeDateTime(String value)
    {
        LocalDateTime dateTime = parseDateTime(value);
        return dateTime == null ? value : dateTime.format(MYSQL_DATETIME);
    }

    private String formatDateTime(LocalDateTime value)
    {
        return value.format(MYSQL_DATETIME);
    }

    private LocalDateTime parseDateTime(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return null;
        }
        try
        {
            if (value.contains("T"))
            {
                return ZonedDateTime.parse(value).withZoneSameInstant(CLINIC_ZONE).toLocalDateTime();
            }
            return LocalDateTime.parse(value, MYSQL_DATETIME);
        }
        catch (Exception e)
        {
            try
            {
                return LocalDateTime.parse(value.replace('T', ' '), MYSQL_DATETIME);
            }
            catch (Exception ignored)
            {
                return null;
            }
        }
    }

    private LocalDate parseDate(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return null;
        }
        try
        {
            return LocalDate.parse(value);
        }
        catch (Exception e)
        {
            LocalDateTime dateTime = parseDateTime(value);
            return dateTime != null ? dateTime.toLocalDate() : null;
        }
    }

    private LocalTime parseLocalTime(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return null;
        }
        try
        {
            return LocalTime.parse(value);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private String toWeekdayKey(DayOfWeek dayOfWeek)
    {
        switch (dayOfWeek)
        {
            case MONDAY:
                return "monday";
            case TUESDAY:
                return "tuesday";
            case WEDNESDAY:
                return "wednesday";
            case THURSDAY:
                return "thursday";
            case FRIDAY:
                return "friday";
            case SATURDAY:
                return "saturday";
            case SUNDAY:
            default:
                return "sunday";
        }
    }

    private static final class TimeRange
    {
        private final LocalTime start;
        private final LocalTime end;

        private TimeRange(LocalTime start, LocalTime end)
        {
            this.start = start;
            this.end = end;
        }
    }

    private static final class PractitionerCandidate
    {
        private final String id;
        private final String name;
        private final JSONObject profile;
        private final int sortOrder;

        private PractitionerCandidate(String id, String name, JSONObject profile, int sortOrder)
        {
            this.id = id;
            this.name = name;
            this.profile = profile;
            this.sortOrder = sortOrder;
        }
    }

    private static final class ServiceWindow
    {
        private final String serviceType;
        private final int durationMinutes;
        private final int practitionerBusyMinutes;
        private final boolean roomRequired;
        private final String requiredTag;
        private final String rawPractitionerTime;
        private final boolean forceFullPractitionerTime;

        private ServiceWindow(String serviceType, int durationMinutes, int practitionerBusyMinutes, boolean roomRequired, String requiredTag, String rawPractitionerTime)
        {
            this(serviceType, durationMinutes, practitionerBusyMinutes, roomRequired, requiredTag, rawPractitionerTime, false);
        }

        private ServiceWindow(String serviceType, int durationMinutes, int practitionerBusyMinutes, boolean roomRequired, String requiredTag, String rawPractitionerTime, boolean forceFullPractitionerTime)
        {
            this.serviceType = serviceType;
            this.durationMinutes = durationMinutes;
            this.practitionerBusyMinutes = practitionerBusyMinutes;
            this.roomRequired = roomRequired;
            this.requiredTag = requiredTag;
            this.rawPractitionerTime = rawPractitionerTime;
            this.forceFullPractitionerTime = forceFullPractitionerTime;
        }

        private ServiceWindow withFullPractitionerTime()
        {
            return new ServiceWindow(
                    serviceType,
                    durationMinutes,
                    durationMinutes,
                    roomRequired,
                    requiredTag,
                    rawPractitionerTime,
                    true);
        }

        private int resolvePractitionerBusyMinutes(JSONObject practitionerProfile)
        {
            if (forceFullPractitionerTime)
            {
                return durationMinutes;
            }
            if (rawPractitionerTime == null || rawPractitionerTime.trim().isEmpty())
            {
                return practitionerBusyMinutes;
            }
            String raw = rawPractitionerTime.trim();
            if ("overlap1".equals(raw))
            {
                int overlap1 = 20;
                if (practitionerProfile != null)
                {
                    Object val = practitionerProfile.get("overlap1");
                    if (val != null)
                    {
                        try { overlap1 = Integer.parseInt(String.valueOf(val).trim()); } catch (NumberFormatException ignored) {}
                        if (overlap1 <= 0) overlap1 = 20;
                    }
                }
                return Math.min(overlap1, durationMinutes);
            }
            if ("overlap2".equals(raw))
            {
                int overlap2 = 10;
                if (practitionerProfile != null)
                {
                    Object val = practitionerProfile.get("overlap2");
                    if (val != null)
                    {
                        try { overlap2 = Integer.parseInt(String.valueOf(val).trim()); } catch (NumberFormatException ignored) {}
                        if (overlap2 <= 0) overlap2 = 10;
                    }
                }
                return Math.min(overlap2, durationMinutes);
            }
            return practitionerBusyMinutes;
        }
    }

    private static final class SlotAssignment
    {
        private final String practitionerId;
        private final String roomId;

        private SlotAssignment(String practitionerId, String roomId)
        {
            this.practitionerId = practitionerId;
            this.roomId = roomId;
        }
    }

    private static final class ScheduleContext
    {
        private final boolean appointmentsPreloaded;
        private final Map<String, List<TcmAppointment>> practitionerAppointments;
        private final Map<String, List<TcmAppointment>> roomAppointments;
        private final List<TcmRoom> roomCandidates;

        private ScheduleContext(
                boolean appointmentsPreloaded,
                Map<String, List<TcmAppointment>> practitionerAppointments,
                Map<String, List<TcmAppointment>> roomAppointments,
                List<TcmRoom> roomCandidates)
        {
            this.appointmentsPreloaded = appointmentsPreloaded;
            this.practitionerAppointments = practitionerAppointments;
            this.roomAppointments = roomAppointments;
            this.roomCandidates = roomCandidates;
        }
    }

    private static final class SlotEvaluation
    {
        private final boolean available;
        private final boolean occupied;
        private final String practitionerId;
        private final String roomId;
        private final List<String> conflicts;

        private SlotEvaluation(boolean available, boolean occupied, String practitionerId, String roomId, List<String> conflicts)
        {
            this.available = available;
            this.occupied = occupied;
            this.practitionerId = practitionerId;
            this.roomId = roomId;
            this.conflicts = conflicts;
        }

        private static SlotEvaluation available(String practitionerId, String roomId)
        {
            return new SlotEvaluation(true, false, practitionerId, roomId, Collections.emptyList());
        }

        private static SlotEvaluation unavailable(String conflict, boolean occupied)
        {
            List<String> conflicts = new ArrayList<>();
            if (conflict != null && !conflict.trim().isEmpty())
            {
                conflicts.add(conflict);
            }
            return new SlotEvaluation(false, occupied, null, null, conflicts);
        }
    }
}
