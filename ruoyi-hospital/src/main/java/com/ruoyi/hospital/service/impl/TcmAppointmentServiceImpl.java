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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import com.ruoyi.hospital.domain.TcmServiceType;
import com.ruoyi.hospital.mapper.TcmAppointmentMapper;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.mapper.TcmServiceTypeMapper;
import com.ruoyi.hospital.service.ITcmAppointmentService;
import com.ruoyi.system.mapper.SysUserMapper;

@Service
public class TcmAppointmentServiceImpl implements ITcmAppointmentService
{
    private static final DateTimeFormatter MYSQL_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<String> VALID_STATUSES = Arrays.asList("booked", "confirmed", "completed", "cancelled");
    private static final int SLOT_MINUTES = 30;

    @Autowired
    private TcmAppointmentMapper appointmentMapper;

    @Autowired
    private TcmClinicSettingMapper settingMapper;

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
        prepareAppointmentScheduling(appointment, null);
        ensureSlotAvailable(appointment, null);
        appointment.setCreateTime(DateUtils.getNowDate());
        return appointmentMapper.insertTcmAppointment(appointment);
    }

    @Override
    public int updateTcmAppointment(TcmAppointment appointment)
    {
        TcmAppointment existing = appointment != null && appointment.getId() != null
                ? appointmentMapper.selectTcmAppointmentById(appointment.getId())
                : null;
        if (hasSchedulingChanged(existing, appointment))
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
    public Map<String, Object> checkSlot(String practitionerId, String roomId, String startTime, String endTime, String excludeId)
    {
        String normalizedStart = normalizeDateTime(startTime);
        String normalizedEnd = normalizeDateTime(endTime);

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
            String workingHoursConflict = validateWorkingHours(practitionerId, normalizedStart, normalizedEnd);
            if (workingHoursConflict != null)
            {
                practitionerConflict = true;
                conflicts.add(workingHoursConflict);
            }
        }

        List<TcmAppointment> overlapping = appointmentMapper.selectOverlappingAppointments(
                practitionerId, roomId, normalizedStart, normalizedEnd, excludeId);
        for (TcmAppointment apt : overlapping)
        {
            if (apt.getPractitionerId() != null && apt.getPractitionerId().equals(practitionerId))
            {
                practitionerConflict = true;
                conflicts.add("Practitioner time conflict: " + apt.getStartTime() + " - " + apt.getEndTime());
            }
            if (roomId != null && apt.getRoomId() != null && apt.getRoomId().equals(roomId))
            {
                roomConflict = true;
                conflicts.add("Room time conflict: " + apt.getStartTime() + " - " + apt.getEndTime());
            }
        }

        TcmClinicSetting intervalSetting = settingMapper.selectSettingByKey("practitionerInterval");
        int intervalMinutes = resolvePractitionerInterval(practitionerId, intervalSetting);
        if (intervalMinutes > 0 && practitionerId != null && !practitionerId.isEmpty())
        {
            String intervalConflict = validatePractitionerInterval(
                    practitionerId,
                    normalizedStart,
                    normalizedEnd,
                    excludeId,
                    intervalMinutes);
            if (intervalConflict != null)
            {
                practitionerConflict = true;
                conflicts.add(intervalConflict);
            }
        }

        result.put("available", conflicts.isEmpty());
        result.put("conflicts", conflicts);
        result.put("practitionerConflict", practitionerConflict);
        result.put("roomConflict", roomConflict);
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
        TcmServiceType serviceTypeConfig = requireServiceType(serviceType);
        if (isRoomRequired(serviceTypeConfig) && (roomId == null || roomId.trim().isEmpty()))
        {
            throw new ServiceException("room is required for the selected service");
        }
        int duration = serviceTypeConfig.getDuration() != null ? serviceTypeConfig.getDuration() : 0;
        if (duration <= 0)
        {
            throw new ServiceException("service duration is invalid");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", targetDate.toString());
        result.put("serviceType", serviceType);
        result.put("duration", duration);

        if (practitionerId != null && !practitionerId.trim().isEmpty())
        {
            PractitionerCandidate practitioner = findPractitionerCandidate(practitionerId);
            result.put("slots", practitioner == null
                    ? new ArrayList<>()
                    : buildPractitionerAvailability(practitioner, targetDate, serviceType, roomId, excludeId, duration));
            return result;
        }

        Map<String, Map<String, Object>> aggregated = new LinkedHashMap<>();
        for (PractitionerCandidate practitioner : listPractitionerCandidates(serviceType))
        {
            List<Map<String, Object>> slots = buildPractitionerAvailability(
                    practitioner,
                    targetDate,
                    serviceType,
                    roomId,
                    excludeId,
                    duration);
            for (Map<String, Object> slot : slots)
            {
                String start = String.valueOf(slot.get("startTime"));
                @SuppressWarnings("unchecked")
                List<String> availablePractitionerIds = (List<String>) slot.get("availablePractitionerIds");
                Map<String, Object> existing = aggregated.get(start);
                if (existing == null)
                {
                    existing = new LinkedHashMap<>(slot);
                    aggregated.put(start, existing);
                    continue;
                }
                @SuppressWarnings("unchecked")
                List<String> existingIds = (List<String>) existing.get("availablePractitionerIds");
                for (String id : availablePractitionerIds)
                {
                    if (!existingIds.contains(id))
                    {
                        existingIds.add(id);
                    }
                }
                if (existing.get("assignedPractitionerId") == null && !existingIds.isEmpty())
                {
                    existing.put("assignedPractitionerId", existingIds.get(0));
                }
            }
        }
        result.put("slots", new ArrayList<>(aggregated.values()));
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

        TcmServiceType serviceTypeConfig = requireServiceType(serviceType);
        if (isRoomRequired(serviceTypeConfig) && (roomId == null || roomId.trim().isEmpty()))
        {
            throw new ServiceException("room is required for the selected service");
        }

        int duration = serviceTypeConfig.getDuration() != null ? serviceTypeConfig.getDuration() : 0;
        if (duration <= 0)
        {
            throw new ServiceException("service duration is invalid");
        }

        LocalDate weekStart = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", targetDate.toString());
        result.put("weekStart", weekStart.toString());
        result.put("weekEnd", weekStart.plusDays(6).toString());
        result.put("serviceType", serviceType);
        result.put("roomId", roomId);
        result.put("duration", duration);
        result.put("slotMinutes", SLOT_MINUTES);

        List<Map<String, Object>> days = new ArrayList<>();
        if (practitionerId != null && !practitionerId.trim().isEmpty())
        {
            PractitionerCandidate practitioner = findPractitionerCandidate(practitionerId);
            if (practitioner == null)
            {
                throw new ServiceException("practitioner not found");
            }
            if (!supportsService(practitioner.profile, serviceType))
            {
                throw new ServiceException("selected practitioner cannot provide this service");
            }
            result.put("practitionerId", practitionerId);
            for (int offset = 0; offset < 7; offset++)
            {
                days.add(buildWeeklyScheduleDay(practitioner, weekStart.plusDays(offset), roomId, duration));
            }
        }
        else
        {
            List<PractitionerCandidate> practitioners = listPractitionerCandidates(serviceType);
            result.put("practitionerId", null);
            for (int offset = 0; offset < 7; offset++)
            {
                days.add(buildAggregatedWeeklyScheduleDay(practitioners, weekStart.plusDays(offset), roomId, duration));
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
        String serviceType = appointment.getServiceType();
        TcmServiceType serviceTypeConfig = requireServiceType(serviceType);
        if (isRoomRequired(serviceTypeConfig)
                && (appointment.getRoomId() == null || appointment.getRoomId().trim().isEmpty()))
        {
            throw new ServiceException("room is required for the selected service");
        }
        String timeRangeConflict = validateAppointmentTimeRange(appointment.getStartTime(), appointment.getEndTime());
        if (timeRangeConflict != null)
        {
            throw new ServiceException(timeRangeConflict);
        }
        if (appointment.getPractitionerId() == null || appointment.getPractitionerId().trim().isEmpty())
        {
            String assignedPractitionerId = resolveAssignedPractitioner(
                    serviceType,
                    appointment.getStartTime(),
                    appointment.getEndTime(),
                    appointment.getRoomId(),
                    excludeId);
            if (assignedPractitionerId == null)
            {
                throw new ServiceException("no practitioner is available for the selected slot");
            }
            appointment.setPractitionerId(assignedPractitionerId);
            return;
        }

        PractitionerCandidate practitioner = findPractitionerCandidate(appointment.getPractitionerId());
        if (practitioner == null)
        {
            throw new ServiceException("practitioner not found");
        }
        if (!supportsService(practitioner.profile, serviceType))
        {
            throw new ServiceException("selected practitioner cannot provide this service");
        }
    }

    private String resolveAssignedPractitioner(
            String serviceType,
            String startTime,
            String endTime,
            String roomId,
            String excludeId)
    {
        for (PractitionerCandidate practitioner : listPractitionerCandidates(serviceType))
        {
            Map<String, Object> slotCheck = checkSlot(
                    practitioner.id,
                    roomId,
                    startTime,
                    endTime,
                    excludeId);
            if (Boolean.TRUE.equals(slotCheck.get("available")))
            {
                return practitioner.id;
            }
        }
        return null;
    }

    private List<Map<String, Object>> buildPractitionerAvailability(
            PractitionerCandidate practitioner,
            LocalDate targetDate,
            String serviceType,
            String roomId,
            String excludeId,
            int duration)
    {
        List<Map<String, Object>> slots = new ArrayList<>();
        if (practitioner == null || !supportsService(practitioner.profile, serviceType))
        {
            return slots;
        }

        for (TimeRange range : extractWorkingRanges(practitioner.profile, toWeekdayKey(targetDate.getDayOfWeek())))
        {
            LocalDateTime start = targetDate.atTime(range.start);
            LocalDateTime lastStart = targetDate.atTime(range.end).minusMinutes(duration);
            if (lastStart.isBefore(start))
            {
                continue;
            }
            for (LocalDateTime current = start; !current.isAfter(lastStart); current = current.plusMinutes(SLOT_MINUTES))
            {
                LocalDateTime slotEnd = current.plusMinutes(duration);
                Map<String, Object> slotCheck = checkSlot(
                        practitioner.id,
                        roomId,
                        formatDateTime(current),
                        formatDateTime(slotEnd),
                        excludeId);
                if (!Boolean.TRUE.equals(slotCheck.get("available")))
                {
                    continue;
                }
                Map<String, Object> slot = new LinkedHashMap<>();
                slot.put("label", current.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                slot.put("startTime", formatDateTime(current));
                slot.put("endTime", formatDateTime(slotEnd));
                slot.put("practitionerId", practitioner.id);
                slot.put("assignedPractitionerId", practitioner.id);
                slot.put("availablePractitionerIds", new ArrayList<>(Arrays.asList(practitioner.id)));
                slots.add(slot);
            }
        }

        slots.sort(Comparator.comparing(slot -> String.valueOf(slot.get("startTime"))));
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
            String roomId,
            int duration)
    {
        List<Map<String, Object>> slots = new ArrayList<>();
        List<TimeRange> ranges = extractWorkingRanges(practitioner.profile, toWeekdayKey(day.getDayOfWeek()));

        for (int minute = 0; minute < 24 * 60; minute += SLOT_MINUTES)
        {
            LocalDateTime slotStart = day.atStartOfDay().plusMinutes(minute);
            LocalDateTime slotEnd = slotStart.plusMinutes(SLOT_MINUTES);
            boolean working = isWithinWorkingRanges(ranges, day, slotStart, slotEnd);
            boolean occupied = false;
            boolean available = false;
            String status = "off";
            if (working)
            {
                Map<String, Object> slotCheck = checkSlot(
                        practitioner.id,
                        roomId,
                        formatDateTime(slotStart),
                        formatDateTime(slotStart.plusMinutes(duration)),
                        null);
                available = Boolean.TRUE.equals(slotCheck.get("available"));
                occupied = !available && hasTimeConflict(slotCheck);
                status = available ? "available" : occupied ? "booked" : "working";
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
            String roomId,
            int duration)
    {
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int minute = 0; minute < 24 * 60; minute += SLOT_MINUTES)
        {
            LocalDateTime slotStart = day.atStartOfDay().plusMinutes(minute);
            LocalDateTime slotEnd = slotStart.plusMinutes(SLOT_MINUTES);
            List<String> availablePractitionerIds = new ArrayList<>();

            for (PractitionerCandidate practitioner : practitioners)
            {
                SlotState state = evaluateSlot(practitioner, day, slotStart, roomId, duration);
                if (state.available)
                {
                    availablePractitionerIds.add(practitioner.id);
                }
            }

            if (availablePractitionerIds.isEmpty())
            {
                continue;
            }
            String assignedPractitionerId = availablePractitionerIds.get(0);

            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("time", slotStart.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            slot.put("startTime", formatDateTime(slotStart));
            slot.put("endTime", formatDateTime(slotEnd));
            slot.put("available", true);
            slot.put("assignedPractitionerId", assignedPractitionerId);
            slot.put("availablePractitionerIds", availablePractitionerIds);
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

    @SuppressWarnings("unchecked")
    private boolean hasTimeConflict(Map<String, Object> slotCheck)
    {
        Object conflictsObj = slotCheck != null ? slotCheck.get("conflicts") : null;
        if (!(conflictsObj instanceof List))
        {
            return false;
        }
        for (Object conflict : (List<Object>) conflictsObj)
        {
            if (conflict == null)
            {
                continue;
            }
            String message = String.valueOf(conflict);
            if (message.contains("time conflict") || message.contains("Room time conflict"))
            {
                return true;
            }
        }
        return false;
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

    private SlotState evaluateSlot(
            PractitionerCandidate practitioner,
            LocalDate day,
            LocalDateTime slotStart,
            String roomId,
            int duration)
    {
        if (practitioner == null)
        {
            return new SlotState(false, false, false);
        }
        List<TimeRange> ranges = extractWorkingRanges(practitioner.profile, toWeekdayKey(day.getDayOfWeek()));
        LocalDateTime slotEnd = slotStart.plusMinutes(SLOT_MINUTES);
        if (!isWithinWorkingRanges(ranges, day, slotStart, slotEnd))
        {
            return new SlotState(false, false, false);
        }

        Map<String, Object> slotCheck = checkSlot(
                practitioner.id,
                roomId,
                formatDateTime(slotStart),
                formatDateTime(slotStart.plusMinutes(duration)),
                null);
        boolean available = Boolean.TRUE.equals(slotCheck.get("available"));
        boolean occupied = !available && hasTimeConflict(slotCheck);
        return new SlotState(true, available, occupied);
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
        Map<String, Object> slot = checkSlot(
                appointment.getPractitionerId(),
                appointment.getRoomId(),
                appointment.getStartTime(),
                appointment.getEndTime(),
                excludeId);
        if (Boolean.FALSE.equals(slot.get("available")))
        {
            Object conflicts = slot.get("conflicts");
            if (conflicts instanceof List && !((List<?>) conflicts).isEmpty())
            {
                List<String> messages = new ArrayList<>();
                for (Object conflict : (List<?>) conflicts)
                {
                    if (conflict != null)
                    {
                        messages.add(String.valueOf(conflict));
                    }
                }
                if (!messages.isEmpty())
                {
                    throw new ServiceException(String.join(", ", messages));
                }
            }
            throw new ServiceException("appointment slot is unavailable");
        }
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

    private static final class SlotState
    {
        private final boolean working;
        private final boolean available;
        private final boolean occupied;

        private SlotState(boolean working, boolean available, boolean occupied)
        {
            this.working = working;
            this.available = available;
            this.occupied = occupied;
        }
    }
}
