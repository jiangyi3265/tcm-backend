package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.hospital.domain.*;
import com.ruoyi.hospital.mapper.*;
import com.ruoyi.hospital.service.impl.TcmAppointmentServiceImpl;
import com.ruoyi.system.mapper.SysUserMapper;

/** Real service/controller boundary checks; only persistence is mocked. */
class TcmPublicBookingDripBoundaryTest
{
    private final LocalDate day = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(1);
    private final TcmAppointmentMapper appointments = mock(TcmAppointmentMapper.class);
    private final TcmClinicSettingMapper settings = mock(TcmClinicSettingMapper.class);
    private final TcmRoomMapper rooms = mock(TcmRoomMapper.class);
    private final TcmServiceTypeMapper services = mock(TcmServiceTypeMapper.class);
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final TcmAppointmentServiceImpl service = new TcmAppointmentServiceImpl();
    private final TcmPublicBookingController controller = new TcmPublicBookingController();
    private final List<TcmAppointment> existing = new ArrayList<>();
    private final Map<Long, JSONObject> profiles = new LinkedHashMap<>();

    @BeforeEach void setup()
    {
        ReflectionTestUtils.setField(service, "appointmentMapper", appointments);
        ReflectionTestUtils.setField(service, "settingMapper", settings);
        ReflectionTestUtils.setField(service, "roomMapper", rooms);
        ReflectionTestUtils.setField(service, "serviceTypeMapper", services);
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(controller, "appointmentService", service);
        ReflectionTestUtils.setField(controller, "clinicSettingMapper", settings);
        ReflectionTestUtils.setField(controller, "userMapper", users);
        when(rooms.selectTcmRoomList(any())).thenReturn(Collections.emptyList());
        // Return both doctors so the real service must keep their booking anchors separate.
        when(appointments.selectAppointmentsInRange(any(), any(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> existing);
        when(users.selectActiveUserIds()).thenAnswer(invocation -> new ArrayList<>(profiles.keySet()));
        when(users.selectUserById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            JSONObject profile = profiles.get(id);
            if (profile == null) return null;
            SysRole role = new SysRole(); role.setRoleKey("practitioner");
            SysUser user = new SysUser(); user.setUserId(id); user.setStatus("0");
            user.setNickName("Synthetic doctor " + id); user.setRoles(List.of(role));
            user.setRemark(profile.toJSONString()); return user;
        });
        for (String key : List.of("short", "long"))
        {
            TcmServiceType type = new TcmServiceType(); type.setServiceKey(key);
            type.setDuration("short".equals(key) ? 30 : 60);
            type.setPractitionerTime("30"); type.setRoomRequired(0);
            when(services.selectTcmServiceTypeByKey(key)).thenReturn(type);
        }
        setting("publicBookingAdvanceDays", "30");
        setting("publicBookingDripWindowDays", "14");
        setting("publicBookingDripMinutes", "60");
    }

    @Test void twoDoctorsReleaseIndependentlyBeforeAggregation()
    {
        doctor(42L, "09:00", "16:00"); doctor(99L, "09:00", "16:00");
        booked("42", day + " 10:00:00", day + " 11:00:00");
        booked("42", day + " 12:00:00", day + " 13:00:00");

        assertEquals(List.of("11:00"), starts("short", "42"));
        assertEquals(List.of("15:30"), starts("short", "99"));
        List<Map<String, Object>> shortSlots = slots("short", null);
        assertEquals(List.of("11:00", "15:30"), times(shortSlots));
        assertDoctors(shortSlots.get(0), "42");
        assertDoctors(shortSlots.get(1), "99");

        List<Map<String, Object>> longSlots = slots("long", null);
        assertEquals(List.of("11:00", "15:00"), times(longSlots));
        assertDoctors(longSlots.get(0), "42");
        assertDoctors(longSlots.get(1), "99");
        String serialized = JSONObject.toJSONString(controller.schedule(day.toString(), null, "short", null, null));
        assertFalse(serialized.contains("bookingRanges"));
        assertFalse(serialized.contains("workingBlocks"));
        assertFalse(serialized.contains("synthetic-patient"));
    }

    @Test void equalReleasedTimesMergeBothDoctorsWithoutDuplicateSlots()
    {
        doctor(42L, "09:00", "16:00"); doctor(99L, "09:00", "16:00");
        for (String id : List.of("42", "99"))
            booked(id, day + " 11:30:00", day + " 13:00:00");
        List<Map<String, Object>> shortSlots = slots("short", null);
        assertEquals(List.of("11:00", "13:00"), times(shortSlots));
        shortSlots.forEach(slot -> assertDoctors(slot, "42", "99"));
        List<Map<String, Object>> longSlots = slots("long", null);
        assertEquals(List.of("10:30", "13:00"), times(longSlots));
        longSlots.forEach(slot -> assertDoctors(slot, "42", "99"));
    }

    @Test void appointmentStartingPreviousDayAnchorsAtItsActualEnd()
    {
        doctor(42L, "09:00", "12:00");
        booked("42", day.minusDays(1) + " 23:00:00", day + " 10:00:00");
        assertEquals(List.of("10:00"), starts("short", "42"));
        assertEquals(List.of("10:00"), starts("long", "42"));
    }

    @Test void appointmentEndingNextDayOnlyReleasesBeforeItsActualStart()
    {
        doctor(42L, "09:00", "12:00");
        booked("42", day + " 11:00:00", day.plusDays(1) + " 01:00:00");
        assertEquals(List.of("10:30"), starts("short", "42"));
        assertEquals(List.of("10:00"), starts("long", "42"));
    }

    private void doctor(long id, String start, String end)
    {
        JSONObject profile = new JSONObject().fluentPut("serviceKeys", List.of("short", "long"))
                .fluentPut("dripEnabled", true);
        profile.put("workingHours", Map.of(day.getDayOfWeek().name().toLowerCase(Locale.ROOT),
                List.of(Map.of("start", start, "end", end))));
        profiles.put(id, profile);
    }

    private void booked(String practitionerId, String start, String end)
    {
        TcmAppointment appointment = new TcmAppointment();
        appointment.setId("synthetic-" + existing.size()); appointment.setPatientId("synthetic-patient");
        appointment.setPractitionerId(practitionerId); appointment.setStatus("booked");
        appointment.setStartTime(start); appointment.setEndTime(end); existing.add(appointment);
    }

    private void setting(String key, String value)
    {
        TcmClinicSetting setting = new TcmClinicSetting(); setting.setSettingKey(key); setting.setSettingValue(value);
        when(settings.selectSettingByKey(key)).thenReturn(setting);
    }

    @SuppressWarnings("unchecked") private List<Map<String, Object>> slots(String key, String practitionerId)
    {
        Map<String, Object> schedule = controller.schedule(day.toString(), null, key, practitionerId, null);
        List<Map<String, Object>> days = (List<Map<String, Object>>) schedule.get("days");
        Map<String, Object> selectedDay = days.stream().filter(d -> day.toString().equals(d.get("date")))
                .findFirst().orElseThrow();
        return (List<Map<String, Object>>) selectedDay.get("slots");
    }

    private List<String> starts(String key, String practitionerId)
    {
        return times(slots(key, practitionerId));
    }

    private List<String> times(List<Map<String, Object>> slots)
    {
        return slots.stream().map(slot -> slot.get("startTime").toString().substring(11, 16))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked") private void assertDoctors(Map<String, Object> slot, String... ids)
    {
        List<String> actualIds = (List<String>) slot.get("availablePractitionerIds");
        assertEquals(new HashSet<>(Arrays.asList(ids)), new HashSet<>(actualIds));
        assertEquals(ids.length, actualIds.size());
        assertEquals(ids.length, slot.get("availableCount"));
        assertTrue(actualIds.contains(slot.get("assignedPractitionerId")));
    }
}
