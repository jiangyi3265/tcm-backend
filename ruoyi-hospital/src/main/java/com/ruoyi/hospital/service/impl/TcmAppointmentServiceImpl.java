package com.ruoyi.hospital.service.impl;

import java.time.DayOfWeek;
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
        appointment.setCreateTime(DateUtils.getNowDate());
        return appointmentMapper.insertTcmAppointment(appointment);
    }

    @Override
    public int updateTcmAppointment(TcmAppointment appointment)
    {
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
        if (intervalSetting != null && intervalSetting.getSettingValue() != null)
        {
            try
            {
                int intervalMinutes = Integer.parseInt(intervalSetting.getSettingValue().replace("\"", ""));
                if (intervalMinutes > 0 && !overlapping.isEmpty())
                {
                    for (TcmAppointment apt : overlapping)
                    {
                        if (apt.getPractitionerId() != null && apt.getPractitionerId().equals(practitionerId))
                        {
                            practitionerConflict = true;
                            conflicts.add("Practitioner interval must be at least " + intervalMinutes + " minutes");
                            break;
                        }
                    }
                }
            }
            catch (NumberFormatException ignored)
            {
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
