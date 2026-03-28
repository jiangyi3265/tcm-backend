package com.ruoyi.hospital.service.impl;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmClinicSetting;
import com.ruoyi.hospital.mapper.TcmAppointmentMapper;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.service.ITcmAppointmentService;
import com.ruoyi.system.service.ISysUserService;

@Service
public class TcmAppointmentServiceImpl implements ITcmAppointmentService
{
    private static final DateTimeFormatter MYSQL_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<String> VALID_STATUSES = Arrays.asList("booked", "confirmed", "completed", "cancelled");

    @Autowired
    private TcmAppointmentMapper appointmentMapper;

    @Autowired
    private TcmClinicSettingMapper settingMapper;

    @Autowired
    private ISysUserService userService;

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
    public TcmAppointment selectTcmAppointmentByIntakeToken(String intakeToken)
    {
        return appointmentMapper.selectTcmAppointmentByIntakeToken(intakeToken);
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
                List<?> rawConflicts = (List<?>) conflicts;
                List<String> messages = new ArrayList<>();
                for (Object conflict : rawConflicts)
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
                || !Objects.equals(existing.getRoomId(), appointment.getRoomId());
    }

    private String validateWorkingHours(String practitionerId, String startTime, String endTime)
    {
        LocalDateTime start = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);
        if (start == null || end == null)
        {
            return null;
        }

        SysUser practitioner = findPractitioner(practitionerId);
        if (practitioner == null || practitioner.getRemark() == null || practitioner.getRemark().trim().isEmpty())
        {
            return null;
        }

        JSONObject profile = parseProfile(practitioner.getRemark());
        JSONObject workingHours = profile.getJSONObject("workingHours");
        if (workingHours == null || workingHours.isEmpty())
        {
            return null;
        }

        String weekdayKey = toWeekdayKey(start.getDayOfWeek());
        JSONObject dayRange = workingHours.getJSONObject(weekdayKey);
        if (dayRange == null)
        {
            return "Practitioner is not available on the selected day";
        }

        LocalTime rangeStart = parseLocalTime(dayRange.getString("start"));
        LocalTime rangeEnd = parseLocalTime(dayRange.getString("end"));
        if (rangeStart == null || rangeEnd == null)
        {
            return "Practitioner working hours are not configured correctly";
        }
        if (start.toLocalTime().isBefore(rangeStart) || end.toLocalTime().isAfter(rangeEnd))
        {
            return "Selected time is outside practitioner working hours";
        }
        return null;
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

    private SysUser findPractitioner(String practitionerId)
    {
        try
        {
            return userService.selectUserById(Long.valueOf(practitionerId));
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private JSONObject parseProfile(String remark)
    {
        try
        {
            return JSON.parseObject(remark);
        }
        catch (Exception e)
        {
            return new JSONObject();
        }
    }

    private String normalizeDateTime(String value)
    {
        LocalDateTime dateTime = parseDateTime(value);
        return dateTime == null ? value : dateTime.format(MYSQL_DATETIME);
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
                return LocalDateTime.parse(value);
            }
            catch (Exception ignored)
            {
                return null;
            }
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
}
