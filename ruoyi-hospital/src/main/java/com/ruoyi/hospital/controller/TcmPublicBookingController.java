package com.ruoyi.hospital.controller;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmClinicSetting;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.domain.TcmRoom;
import com.ruoyi.hospital.domain.TcmServiceType;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.service.ITcmAppointmentService;
import com.ruoyi.hospital.service.ITcmAppointmentNotificationService;
import com.ruoyi.hospital.service.ITcmPatientService;
import com.ruoyi.hospital.service.ITcmRoomService;
import com.ruoyi.hospital.service.ITcmServiceTypeService;
import com.ruoyi.hospital.utils.PayloadUtils;
import com.ruoyi.system.mapper.SysUserMapper;

@Anonymous
@RestController
@RequestMapping("/api/public-booking")
public class TcmPublicBookingController
{
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter MYSQL_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_LABEL = DateTimeFormatter.ofPattern("HH:mm");
    private static final int DEFAULT_PUBLIC_ADVANCE_DAYS = 15;
    private static final int DEFAULT_PUBLIC_DRIP_WINDOW_DAYS = 7;
    private static final int DEFAULT_PUBLIC_DRIP_MINUTES = 60;
    private static final int MIN_RELEASE_MINUTES = 10;

    @Autowired
    private ITcmAppointmentService appointmentService;

    @Autowired
    private ITcmPatientService patientService;

    @Autowired
    private ITcmRoomService roomService;

    @Autowired
    private ITcmServiceTypeService serviceTypeService;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private TcmClinicSettingMapper clinicSettingMapper;

    @Autowired
    private ITcmAppointmentNotificationService appointmentNotificationService;

    @GetMapping("/options")
    public Map<String, Object> options()
    {
        PublicBookingSettings settings = loadPublicBookingSettings();
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> services = new ArrayList<>();
        for (TcmServiceType serviceType : serviceTypeService.selectAll())
        {
            if (serviceType.getPublicVisible() != null && serviceType.getPublicVisible() == 0)
            {
                continue;
            }
            services.add(PayloadUtils.flattenServiceType(serviceType));
        }
        result.put("serviceTypes", services);
        result.put("rooms", buildRooms());
        result.put("practitioners", buildPractitioners());
        result.put("publicBooking", flattenPublicBookingSettings(settings));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> availability(
            @RequestParam String date,
            @RequestParam String serviceType,
            @RequestParam(required = false) String practitionerId,
            @RequestParam(required = false) String roomId)
    {
        Map<String, Object> schedule = buildPublicSchedule(date, date, serviceType, practitionerId, roomId);
        List<Map<String, Object>> days = toMapList(schedule.get("days"));
        Map<String, Object> matchedDay = null;
        for (Map<String, Object> day : days)
        {
            if (date.equals(trim(day.get("date"))))
            {
                matchedDay = day;
                break;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", date);
        result.put("serviceType", serviceType);
        result.put("slotStepMinutes", schedule.get("slotStepMinutes"));
        result.put("duration", schedule.get("duration"));
        result.put("practitionerBusyMinutes", schedule.get("practitionerBusyMinutes"));
        result.put("publicWindowStart", schedule.get("publicWindowStart"));
        result.put("publicWindowEnd", schedule.get("publicWindowEnd"));
        result.put("slots", matchedDay != null ? toMapList(matchedDay.get("slots")) : new ArrayList<>());
        return result;
    }

    @GetMapping("/schedule")
    public Map<String, Object> schedule(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String weekStart,
            @RequestParam String serviceType,
            @RequestParam(required = false) String practitionerId,
            @RequestParam(required = false) String roomId)
    {
        return buildPublicSchedule(date, weekStart, serviceType, practitionerId, roomId);
    }

    @PostMapping("")
    public Map<String, Object> create(@RequestBody Map<String, Object> body)
    {
        body.put("bookingSource", "public");
        String firstName = trim(body.get("firstName"));
        String lastName = trim(body.get("lastName"));
        String patientName = resolvePatientName(firstName, lastName, trim(body.get("patientName")));
        String phone = trim(body.get("phone"));
        String email = trim(body.get("email"));
        Map<String, Object> intakeSummary = normalizePublicBookingIntake(body);

        TcmPatient patient = findOrCreatePatient(patientName, firstName, lastName, phone, email);
        TcmAppointment appointment = PayloadUtils.toAppointment(body);
        appointment.setPatientId(patient.getId());
        appointment.setStatus("booked");
        appointmentService.insertTcmAppointment(appointment);

        TcmAppointment created = appointmentService.selectTcmAppointmentById(appointment.getId());
        if (patient.getPractitionerId() == null || patient.getPractitionerId().trim().isEmpty())
        {
            patient.setPractitionerId(created.getPractitionerId());
            patientService.updateTcmPatient(patient);
        }
        patientService.savePublicBookingIntakeSummary(patient.getId(), created.getId(), intakeSummary);
        appointmentNotificationService.handleAppointmentCreated(created);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appointment", PayloadUtils.flatten(created));
        result.put("patientId", patient.getId());
        return result;
    }

    @GetMapping("/manage/{token}")
    public Map<String, Object> manageInfo(@org.springframework.web.bind.annotation.PathVariable String token)
    {
        return appointmentNotificationService.getManageInfo(token);
    }

    @PostMapping("/manage/{token}/cancel")
    public Map<String, Object> cancel(
            @org.springframework.web.bind.annotation.PathVariable String token,
            @RequestBody(required = false) Map<String, Object> body)
    {
        String source = body != null && body.get("source") != null
                ? String.valueOf(body.get("source")).trim()
                : "patient_public";
        TcmAppointment appointment = appointmentNotificationService.cancelByManageToken(token, source);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("appointment", PayloadUtils.flatten(appointment));
        return result;
    }

    private Map<String, Object> buildPublicSchedule(
            String date,
            String weekStart,
            String serviceType,
            String practitionerId,
            String roomId)
    {
        String anchor = resolveScheduleAnchor(date, weekStart);
        LocalDate anchorDate = parseDate(anchor);
        if (anchorDate == null)
        {
            throw new ServiceException("invalid date");
        }

        PublicBookingSettings settings = loadPublicBookingSettings();
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        LocalDate publicWindowEnd = today.plusDays(settings.advanceDays - 1L);
        LocalDate normalizedWeekStart = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<Map<String, Object>> practitioners = filterPractitionersForService(buildPractitioners(), serviceType);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", anchorDate.toString());
        result.put("weekStart", normalizedWeekStart.toString());
        result.put("weekEnd", normalizedWeekStart.plusDays(6).toString());
        result.put("serviceType", serviceType);
        result.put("roomId", roomId);
        result.put("publicWindowStart", today.toString());
        result.put("publicWindowEnd", publicWindowEnd.toString());
        result.put("publicBooking", flattenPublicBookingSettings(settings));

        if (practitionerId != null && !practitionerId.trim().isEmpty())
        {
            requirePractitioner(practitioners, practitionerId);
            boolean practitionerDripEnabled = resolvePractitionerDripEnabled(practitioners, practitionerId);
            Map<String, Object> rawSchedule = appointmentService.getWeeklySchedule(anchor, serviceType, practitionerId, roomId);
            int slotStepMinutes = parsePositiveInt(rawSchedule.get("slotStepMinutes"), MIN_RELEASE_MINUTES);
            result.put("practitionerId", practitionerId);
            result.put("duration", rawSchedule.get("duration"));
            result.put("practitionerBusyMinutes", rawSchedule.get("practitionerBusyMinutes"));
            result.put("slotMinutes", rawSchedule.get("slotMinutes"));
            result.put("slotStepMinutes", slotStepMinutes);
            result.put("days", buildPublicPractitionerDays(
                    toMapList(rawSchedule.get("days")),
                    normalizedWeekStart,
                    settings,
                    today,
                    publicWindowEnd,
                    practitionerId,
                    slotStepMinutes,
                    practitionerDripEnabled));
            return result;
        }

        int resolvedStep = MIN_RELEASE_MINUTES;
        result.put("practitionerId", null);
        result.put("slotMinutes", null);
        result.put("duration", null);
        result.put("practitionerBusyMinutes", null);
        Map<LocalDate, Map<String, Object>> weekDays = initPublicWeek(normalizedWeekStart);

        for (Map<String, Object> practitioner : practitioners)
        {
            String currentPractitionerId = trim(practitioner.get("id"));
            if (currentPractitionerId == null || currentPractitionerId.isEmpty())
            {
                continue;
            }

            Map<String, Object> rawSchedule = appointmentService.getWeeklySchedule(anchor, serviceType, currentPractitionerId, roomId);
            int currentStep = parsePositiveInt(rawSchedule.get("slotStepMinutes"), MIN_RELEASE_MINUTES);
            if (resolvedStep == MIN_RELEASE_MINUTES || currentStep < resolvedStep)
            {
                resolvedStep = currentStep;
            }
            if (result.get("slotMinutes") == null)
            {
                result.put("slotMinutes", rawSchedule.get("slotMinutes"));
            }
            if (result.get("duration") == null)
            {
                result.put("duration", rawSchedule.get("duration"));
            }
            if (result.get("practitionerBusyMinutes") == null)
            {
                result.put("practitionerBusyMinutes", rawSchedule.get("practitionerBusyMinutes"));
            }

            List<Map<String, Object>> practitionerDays = buildPublicPractitionerDays(
                    toMapList(rawSchedule.get("days")),
                    normalizedWeekStart,
                    settings,
                    today,
                    publicWindowEnd,
                    currentPractitionerId,
                    currentStep,
                    Boolean.TRUE.equals(practitioner.get("dripEnabled")));
            mergePractitionerWeek(weekDays, practitionerDays);
        }

        result.put("slotStepMinutes", resolvedStep);
        result.put("days", finalizeMergedWeek(weekDays));
        return result;
    }

    private List<Map<String, Object>> buildPublicPractitionerDays(
            List<Map<String, Object>> rawDays,
            LocalDate normalizedWeekStart,
            PublicBookingSettings settings,
            LocalDate today,
            LocalDate publicWindowEnd,
            String practitionerId,
            int slotStepMinutes,
            boolean dripEnabled)
    {
        Map<LocalDate, Map<String, Object>> weekDays = initPublicWeek(normalizedWeekStart);
        for (Map<String, Object> rawDay : rawDays)
        {
            LocalDate day = parseDate(trim(rawDay.get("date")));
            if (day == null)
            {
                continue;
            }
            Map<String, Object> dayResult = weekDays.get(day);
            if (dayResult == null)
            {
                continue;
            }
            List<SlotInfo> slotInfos = toSlotInfos(rawDay.get("slots"));
            List<Map<String, Object>> releasedSlots = buildPublicReleasedSlots(
                    slotInfos,
                    settings,
                    today,
                    publicWindowEnd,
                    day,
                    practitionerId,
                    slotStepMinutes,
                    dripEnabled);
            dayResult.put("slots", releasedSlots);
            dayResult.put("availableCount", releasedSlots.size());
            dayResult.put("releaseMode", dripEnabled && shouldApplyDrip(day, settings, today) ? "drip" : "full");
        }
        return finalizeMergedWeek(weekDays);
    }

    private List<Map<String, Object>> buildPublicReleasedSlots(
            List<SlotInfo> slotInfos,
            PublicBookingSettings settings,
            LocalDate today,
            LocalDate publicWindowEnd,
            LocalDate day,
            String practitionerId,
            int slotStepMinutes,
            boolean dripEnabled)
    {
        List<Map<String, Object>> releasedSlots = new ArrayList<>();
        if (day == null || day.isBefore(today) || day.isAfter(publicWindowEnd))
        {
            return releasedSlots;
        }

        boolean applyDrip = dripEnabled && shouldApplyDrip(day, settings, today);
        LocalDateTime now = LocalDateTime.now(CLINIC_ZONE);
        int resolvedStep = Math.max(MIN_RELEASE_MINUTES, slotStepMinutes);

        for (List<SlotInfo> block : splitWorkingBlocks(slotInfos, resolvedStep))
        {
            List<ReleaseWindow> releaseWindows = buildReleaseWindows(block, settings.dripMinutes, resolvedStep);
            int releasedWindowCount = applyDrip ? resolveReleasedWindowCount(block, releaseWindows) : releaseWindows.size();

            for (SlotInfo slot : block)
            {
                if (!"available".equals(slot.status))
                {
                    continue;
                }
                if (day.equals(today) && !slot.start.isAfter(now))
                {
                    continue;
                }
                if (applyDrip && !isReleasedSlot(slot.start, releaseWindows, releasedWindowCount))
                {
                    continue;
                }
                releasedSlots.add(toPublicSlot(slot, practitionerId));
            }
        }

        return releasedSlots;
    }

    private List<List<SlotInfo>> splitWorkingBlocks(List<SlotInfo> slotInfos, int slotStepMinutes)
    {
        List<List<SlotInfo>> blocks = new ArrayList<>();
        List<SlotInfo> currentBlock = new ArrayList<>();
        SlotInfo previous = null;

        for (SlotInfo slot : slotInfos)
        {
            if (slot == null || "off".equals(slot.status))
            {
                if (!currentBlock.isEmpty())
                {
                    blocks.add(currentBlock);
                    currentBlock = new ArrayList<>();
                }
                previous = null;
                continue;
            }

            if (previous != null)
            {
                long diffMinutes = Duration.between(previous.start, slot.start).toMinutes();
                if (diffMinutes != slotStepMinutes)
                {
                    if (!currentBlock.isEmpty())
                    {
                        blocks.add(currentBlock);
                    }
                    currentBlock = new ArrayList<>();
                }
            }

            currentBlock.add(slot);
            previous = slot;
        }

        if (!currentBlock.isEmpty())
        {
            blocks.add(currentBlock);
        }
        return blocks;
    }

    private List<ReleaseWindow> buildReleaseWindows(List<SlotInfo> block, int dripMinutes, int slotStepMinutes)
    {
        List<ReleaseWindow> windows = new ArrayList<>();
        if (block == null || block.isEmpty())
        {
            return windows;
        }
        LocalDateTime blockStart = block.get(0).start;
        LocalDateTime cursorEnd = block.get(block.size() - 1).start.plusMinutes(slotStepMinutes);
        while (cursorEnd.isAfter(blockStart))
        {
            LocalDateTime cursorStart = cursorEnd.minusMinutes(dripMinutes);
            if (cursorStart.isBefore(blockStart))
            {
                cursorStart = blockStart;
            }
            windows.add(new ReleaseWindow(cursorStart, cursorEnd));
            if (!cursorStart.isBefore(cursorEnd))
            {
                break;
            }
            cursorEnd = cursorStart;
        }
        return windows;
    }

    private int resolveReleasedWindowCount(List<SlotInfo> block, List<ReleaseWindow> windows)
    {
        if (windows == null || windows.isEmpty())
        {
            return 0;
        }
        int releasedCount = 1;
        for (int index = 0; index < windows.size() - 1; index++)
        {
            if (!hasBookedSlot(block, windows.get(index)))
            {
                break;
            }
            releasedCount = index + 2;
        }
        return releasedCount;
    }

    private boolean hasBookedSlot(List<SlotInfo> block, ReleaseWindow window)
    {
        for (SlotInfo slot : block)
        {
            if (slot == null || !"booked".equals(slot.status))
            {
                continue;
            }
            if (!slot.start.isBefore(window.start) && slot.start.isBefore(window.end))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isReleasedSlot(LocalDateTime slotStart, List<ReleaseWindow> windows, int releasedWindowCount)
    {
        for (int index = 0; index < releasedWindowCount && index < windows.size(); index++)
        {
            ReleaseWindow window = windows.get(index);
            if (!slotStart.isBefore(window.start) && slotStart.isBefore(window.end))
            {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> toPublicSlot(SlotInfo slot, String practitionerId)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("time", slot.start.toLocalTime().format(TIME_LABEL));
        result.put("date", slot.start.toLocalDate().toString());
        result.put("startTime", formatDateTime(slot.start));
        result.put("endTime", formatDateTime(slot.end));
        result.put("available", true);
        result.put("status", "available");
        result.put("state", "bookable");
        result.put("roomId", slot.roomId);
        result.put("assignedPractitionerId", practitionerId);
        result.put("availablePractitionerIds", new ArrayList<>(Collections.singletonList(practitionerId)));
        result.put("availableCount", 1);
        return result;
    }

    private void mergePractitionerWeek(Map<LocalDate, Map<String, Object>> mergedWeek, List<Map<String, Object>> practitionerDays)
    {
        for (Map<String, Object> practitionerDay : practitionerDays)
        {
            LocalDate day = parseDate(trim(practitionerDay.get("date")));
            if (day == null || !mergedWeek.containsKey(day))
            {
                continue;
            }
            Map<String, Object> targetDay = mergedWeek.get(day);
            List<Map<String, Object>> targetSlots = toMapList(targetDay.get("slots"));
            List<Map<String, Object>> sourceSlots = toMapList(practitionerDay.get("slots"));

            for (Map<String, Object> sourceSlot : sourceSlots)
            {
                Map<String, Object> targetSlot = findMergedSlot(
                        targetSlots,
                        trim(sourceSlot.get("startTime")),
                        trim(sourceSlot.get("endTime")));
                if (targetSlot == null)
                {
                    Map<String, Object> copy = new LinkedHashMap<>(sourceSlot);
                    targetSlots.add(copy);
                    continue;
                }

                Set<String> practitionerIds = new LinkedHashSet<>(toStringList(targetSlot.get("availablePractitionerIds")));
                practitionerIds.addAll(toStringList(sourceSlot.get("availablePractitionerIds")));
                targetSlot.put("availablePractitionerIds", new ArrayList<>(practitionerIds));
                targetSlot.put("availableCount", practitionerIds.size());
                if (trim(targetSlot.get("assignedPractitionerId")) == null)
                {
                    targetSlot.put("assignedPractitionerId", trim(sourceSlot.get("assignedPractitionerId")));
                }
                if (trim(targetSlot.get("roomId")) == null)
                {
                    targetSlot.put("roomId", trim(sourceSlot.get("roomId")));
                }
            }

            targetSlots.sort((left, right) -> trim(left.get("startTime")).compareTo(trim(right.get("startTime"))));
            targetDay.put("slots", targetSlots);
            targetDay.put("availableCount", targetSlots.size());
            targetDay.put("releaseMode", resolveMergedReleaseMode(
                    trim(targetDay.get("releaseMode")),
                    trim(practitionerDay.get("releaseMode"))));
        }
    }

    private String resolveMergedReleaseMode(String current, String incoming)
    {
        if ("drip".equals(current) || "drip".equals(incoming))
        {
            return "drip";
        }
        return incoming != null ? incoming : current;
    }

    private Map<String, Object> findMergedSlot(List<Map<String, Object>> slots, String startTime, String endTime)
    {
        for (Map<String, Object> slot : slots)
        {
            if (startTime.equals(trim(slot.get("startTime"))) && endTime.equals(trim(slot.get("endTime"))))
            {
                return slot;
            }
        }
        return null;
    }

    private Map<LocalDate, Map<String, Object>> initPublicWeek(LocalDate weekStart)
    {
        Map<LocalDate, Map<String, Object>> weekDays = new LinkedHashMap<>();
        for (int offset = 0; offset < 7; offset++)
        {
            LocalDate day = weekStart.plusDays(offset);
            Map<String, Object> dayResult = new LinkedHashMap<>();
            dayResult.put("date", day.toString());
            dayResult.put("weekday", day.getDayOfWeek().name().toLowerCase());
            dayResult.put("slots", new ArrayList<>());
            dayResult.put("availableCount", 0);
            dayResult.put("releaseMode", "full");
            weekDays.put(day, dayResult);
        }
        return weekDays;
    }

    private List<Map<String, Object>> finalizeMergedWeek(Map<LocalDate, Map<String, Object>> weekDays)
    {
        List<Map<String, Object>> days = new ArrayList<>();
        for (Map<String, Object> day : weekDays.values())
        {
            List<Map<String, Object>> slots = toMapList(day.get("slots"));
            slots.sort((left, right) -> trim(left.get("startTime")).compareTo(trim(right.get("startTime"))));
            day.put("slots", slots);
            day.put("availableCount", slots.size());
            days.add(day);
        }
        return days;
    }

    private List<SlotInfo> toSlotInfos(Object rawSlots)
    {
        List<SlotInfo> slots = new ArrayList<>();
        for (Map<String, Object> rawSlot : toMapList(rawSlots))
        {
            LocalDateTime start = parseDateTime(trim(rawSlot.get("startTime")));
            LocalDateTime end = parseDateTime(trim(rawSlot.get("endTime")));
            if (start == null || end == null)
            {
                continue;
            }
            slots.add(new SlotInfo(
                    start,
                    end,
                    trim(rawSlot.get("status")),
                    trim(rawSlot.get("roomId"))));
        }
        slots.sort((left, right) -> left.start.compareTo(right.start));
        return slots;
    }

    private List<Map<String, Object>> buildPractitioners()
    {
        List<Map<String, Object>> practitioners = new ArrayList<>();
        for (Long userId : userMapper.selectActiveUserIds())
        {
            if (userId == null || userId < 1L)
            {
                continue;
            }
            SysUser user = userMapper.selectUserById(userId);
            if (user == null || !"0".equals(user.getStatus()) || !hasPractitionerRole(user))
            {
                continue;
            }
            JSONObject profile = parseProfile(user.getRemark());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(user.getUserId()));
            item.put("name", user.getNickName());
            item.put("serviceKeys", sanitizeStringList(profile.get("serviceKeys")));
            item.put("practitionerSortOrder", sanitizeInteger(profile.get("practitionerSortOrder")));
            item.put("workingHours", profile.get("workingHours"));
            item.put("dripEnabled", profile.getBooleanValue("dripEnabled", true));
            practitioners.add(item);
        }
        practitioners.sort((left, right) -> {
            Integer leftOrder = (Integer) left.get("practitionerSortOrder");
            Integer rightOrder = (Integer) right.get("practitionerSortOrder");
            int resolvedLeft = leftOrder != null ? leftOrder : Integer.MAX_VALUE;
            int resolvedRight = rightOrder != null ? rightOrder : Integer.MAX_VALUE;
            if (resolvedLeft != resolvedRight)
            {
                return Integer.compare(resolvedLeft, resolvedRight);
            }
            return String.valueOf(left.get("name")).compareTo(String.valueOf(right.get("name")));
        });
        return practitioners;
    }

    private List<Map<String, Object>> filterPractitionersForService(List<Map<String, Object>> practitioners, String serviceType)
    {
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> practitioner : practitioners)
        {
            List<String> serviceKeys = toStringList(practitioner.get("serviceKeys"));
            if (serviceKeys.isEmpty() || serviceKeys.contains(serviceType))
            {
                filtered.add(practitioner);
            }
        }
        return filtered;
    }

    private void requirePractitioner(List<Map<String, Object>> practitioners, String practitionerId)
    {
        for (Map<String, Object> practitioner : practitioners)
        {
            if (practitionerId.equals(trim(practitioner.get("id"))))
            {
                return;
            }
        }
        throw new ServiceException("practitioner not found");
    }

    private boolean resolvePractitionerDripEnabled(List<Map<String, Object>> practitioners, String practitionerId)
    {
        for (Map<String, Object> practitioner : practitioners)
        {
            if (practitionerId.equals(trim(practitioner.get("id"))))
            {
                Object value = practitioner.get("dripEnabled");
                return value == null || Boolean.TRUE.equals(value);
            }
        }
        return true;
    }

    private List<Map<String, Object>> buildRooms()
    {
        List<Map<String, Object>> rooms = new ArrayList<>();
        for (TcmRoom room : roomService.selectTcmRoomList(new TcmRoom()))
        {
            if (room == null || room.getId() == null || room.getId().trim().isEmpty())
            {
                continue;
            }
            if (room.getIsActive() != null && room.getIsActive() != 1)
            {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(PayloadUtils.flattenRoom(room));
            rooms.add(item);
        }
        rooms.sort((left, right) -> String.valueOf(left.get("name")).compareTo(String.valueOf(right.get("name"))));
        return rooms;
    }

    private PublicBookingSettings loadPublicBookingSettings()
    {
        return new PublicBookingSettings(
                resolveSettingInt("publicBookingAdvanceDays", DEFAULT_PUBLIC_ADVANCE_DAYS, 1),
                resolveSettingInt("publicBookingDripWindowDays", DEFAULT_PUBLIC_DRIP_WINDOW_DAYS, 1),
                resolveSettingInt("publicBookingDripMinutes", DEFAULT_PUBLIC_DRIP_MINUTES, MIN_RELEASE_MINUTES));
    }

    private int resolveSettingInt(String key, int fallback, int minimum)
    {
        TcmClinicSetting setting = clinicSettingMapper.selectSettingByKey(key);
        if (setting == null || setting.getSettingValue() == null)
        {
            return fallback;
        }
        try
        {
            return Math.max(minimum, Integer.parseInt(setting.getSettingValue().trim()));
        }
        catch (Exception e)
        {
            return fallback;
        }
    }

    private Map<String, Object> flattenPublicBookingSettings(PublicBookingSettings settings)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("advanceDays", settings.advanceDays);
        result.put("dripWindowDays", settings.dripWindowDays);
        result.put("dripMinutes", settings.dripMinutes);
        return result;
    }

    private boolean shouldApplyDrip(LocalDate day, PublicBookingSettings settings, LocalDate today)
    {
        long daysFromToday = Duration.between(today.atStartOfDay(), day.atStartOfDay()).toDays();
        return daysFromToday >= 0 && daysFromToday < settings.dripWindowDays;
    }

    private Map<String, Object> normalizePublicBookingIntake(Map<String, Object> body)
    {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (body == null)
        {
            return normalized;
        }
        Object rawIntake = body.get("intakeFormData");
        if (rawIntake instanceof Map<?, ?>)
        {
            Map<?, ?> intakeMap = (Map<?, ?>) rawIntake;
            putIfHasText(normalized, "chiefComplaint", intakeMap.get("chiefComplaint"));
            putIfHasText(normalized, "allergies", intakeMap.get("allergies"));
            putIfHasText(normalized, "currentMedications", intakeMap.get("currentMedications"));
            putIfHasText(normalized, "medicalHistory", intakeMap.get("medicalHistory"));
        }
        String notes = trim(body.get("notes"));
        if (notes != null && !notes.isEmpty())
        {
            normalized.put("additionalNotes", notes);
        }
        return normalized;
    }

    private void putIfHasText(Map<String, Object> target, String key, Object value)
    {
        String text = trim(value);
        if (text != null && !text.isEmpty())
        {
            target.put(key, text);
        }
    }

    private String resolveScheduleAnchor(String date, String weekStart)
    {
        String anchor = weekStart != null && !weekStart.trim().isEmpty() ? weekStart.trim() : trim(date);
        if (anchor == null || anchor.isEmpty())
        {
            throw new ServiceException("date or weekStart is required");
        }
        return anchor;
    }

    private String resolvePatientName(String firstName, String lastName, String patientName)
    {
        String joined = joinName(firstName, lastName);
        if (joined != null && !joined.isEmpty())
        {
            return joined;
        }
        return requireText(patientName, "patientName");
    }

    private String joinName(String firstName, String lastName)
    {
        String first = firstName != null ? firstName.trim() : "";
        String last = lastName != null ? lastName.trim() : "";
        return (last + " " + first).trim();
    }

    private TcmPatient findOrCreatePatient(String patientName, String firstName, String lastName, String phone, String email)
    {
        for (TcmPatient patient : patientService.selectTcmPatientList(new TcmPatient()))
        {
            if (email != null && !email.isEmpty() && email.equalsIgnoreCase(trim(patient.getEmail())))
            {
                return mergePatientContact(patient, patientName, firstName, lastName, phone, email);
            }
            if (phone != null && !phone.isEmpty()
                    && phone.equals(trim(patient.getPhone()))
                    && patientName.equals(trim(patient.getName())))
            {
                return mergePatientContact(patient, patientName, firstName, lastName, phone, email);
            }
        }

        TcmPatient patient = new TcmPatient();
        patient.setId(java.util.UUID.randomUUID().toString());
        patient.setName(patientName);
        patient.setFirstName(firstName);
        patient.setLastName(lastName);
        patient.setEmail(email);
        patient.setPhone(phone);
        patient.setIsActive(1);
        patient.setConsentSigned(0);
        JSONObject payload = new JSONObject();
        if (email != null && !email.isEmpty())
        {
            payload.put("emails", java.util.Collections.singletonList(email));
        }
        patient.setPayload(payload.toJSONString());
        patientService.insertTcmPatient(patient);
        return patientService.selectTcmPatientById(patient.getId());
    }

    private TcmPatient mergePatientContact(TcmPatient patient, String patientName, String firstName, String lastName, String phone, String email)
    {
        boolean changed = false;
        if (patientName != null && !patientName.equals(trim(patient.getName())))
        {
            patient.setName(patientName);
            changed = true;
        }
        if (firstName != null && !firstName.isEmpty() && !firstName.equals(trim(patient.getFirstName())))
        {
            patient.setFirstName(firstName);
            changed = true;
        }
        if (lastName != null && !lastName.isEmpty() && !lastName.equals(trim(patient.getLastName())))
        {
            patient.setLastName(lastName);
            changed = true;
        }
        if (phone != null && !phone.isEmpty() && !phone.equals(trim(patient.getPhone())))
        {
            patient.setPhone(phone);
            changed = true;
        }
        if (email != null && !email.isEmpty() && !email.equalsIgnoreCase(trim(patient.getEmail())))
        {
            patient.setEmail(email);
            changed = true;
        }
        JSONObject payload = parseProfile(patient.getPayload());
        List<String> emails = sanitizeStringList(payload.get("emails"));
        if (email != null && !email.isEmpty() && !emails.contains(email))
        {
            emails.add(email);
            payload.put("emails", emails);
            patient.setPayload(payload.toJSONString());
            changed = true;
        }
        if (changed)
        {
            patientService.updateTcmPatient(patient);
            return patientService.selectTcmPatientById(patient.getId());
        }
        return patient;
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

    private List<String> sanitizeStringList(Object value)
    {
        List<String> items = new ArrayList<>();
        if (!(value instanceof List<?>))
        {
            return items;
        }
        for (Object item : (List<?>) value)
        {
            if (item == null)
            {
                continue;
            }
            String normalized = String.valueOf(item).trim();
            if (!normalized.isEmpty() && !items.contains(normalized))
            {
                items.add(normalized);
            }
        }
        return items;
    }

    private Integer sanitizeInteger(Object value)
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

    private String requireText(Object value, String fieldName)
    {
        String text = trim(value);
        if (text == null || text.isEmpty())
        {
            throw new ServiceException(fieldName + " is required");
        }
        return text;
    }

    private String trim(Object value)
    {
        return value == null ? null : String.valueOf(value).trim();
    }

    private LocalDate parseDate(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return null;
        }
        try
        {
            return LocalDate.parse(value.trim());
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private LocalDateTime parseDateTime(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return null;
        }
        try
        {
            return LocalDateTime.parse(value.trim(), MYSQL_DATETIME);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private String formatDateTime(LocalDateTime value)
    {
        return value.format(MYSQL_DATETIME);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toMapList(Object value)
    {
        List<Map<String, Object>> list = new ArrayList<>();
        if (!(value instanceof List<?>))
        {
            return list;
        }
        for (Object item : (List<?>) value)
        {
            if (item instanceof Map<?, ?>)
            {
                list.add((Map<String, Object>) item);
            }
        }
        return list;
    }

    private List<String> toStringList(Object value)
    {
        List<String> list = new ArrayList<>();
        if (!(value instanceof List<?>))
        {
            return list;
        }
        for (Object item : (List<?>) value)
        {
            String text = trim(item);
            if (text != null && !text.isEmpty() && !list.contains(text))
            {
                list.add(text);
            }
        }
        return list;
    }

    private int parsePositiveInt(Object value, int fallback)
    {
        if (value == null)
        {
            return fallback;
        }
        try
        {
            return Math.max(MIN_RELEASE_MINUTES, Integer.parseInt(String.valueOf(value).trim()));
        }
        catch (Exception e)
        {
            return fallback;
        }
    }

    private static final class PublicBookingSettings
    {
        private final int advanceDays;
        private final int dripWindowDays;
        private final int dripMinutes;

        private PublicBookingSettings(int advanceDays, int dripWindowDays, int dripMinutes)
        {
            this.advanceDays = advanceDays;
            this.dripWindowDays = dripWindowDays;
            this.dripMinutes = dripMinutes;
        }
    }

    private static final class SlotInfo
    {
        private final LocalDateTime start;
        private final LocalDateTime end;
        private final String status;
        private final String roomId;

        private SlotInfo(LocalDateTime start, LocalDateTime end, String status, String roomId)
        {
            this.start = start;
            this.end = end;
            this.status = status != null ? status : "";
            this.roomId = roomId;
        }
    }

    private static final class ReleaseWindow
    {
        private final LocalDateTime start;
        private final LocalDateTime end;

        private ReleaseWindow(LocalDateTime start, LocalDateTime end)
        {
            this.start = start;
            this.end = end;
        }
    }
}
