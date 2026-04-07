package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmClinicSetting;
import com.ruoyi.hospital.domain.TcmServiceType;
import com.ruoyi.hospital.mapper.TcmAppointmentMapper;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.mapper.TcmServiceTypeMapper;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.system.mapper.SysUserMapper;

@ExtendWith(MockitoExtension.class)
class TcmAppointmentServiceImplTest
{
    @Mock
    private TcmAppointmentMapper appointmentMapper;

    @Mock
    private TcmClinicSettingMapper settingMapper;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private TcmServiceTypeMapper serviceTypeMapper;

    private TcmAppointmentServiceImpl service;

    @BeforeEach
    void setUp()
    {
        service = new TcmAppointmentServiceImpl();
        ReflectionTestUtils.setField(service, "appointmentMapper", appointmentMapper);
        ReflectionTestUtils.setField(service, "settingMapper", settingMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "serviceTypeMapper", serviceTypeMapper);
    }

    @Test
    void checkSlot_shouldRejectWhenPractitionerSpecificIntervalIsTooShort()
    {
        when(appointmentMapper.selectOverlappingAppointments(anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(Collections.emptyList());
        when(appointmentMapper.selectTcmAppointmentList(any())).thenReturn(Arrays.asList(
                appointment("a-1", "p-1", "2026-03-28 10:00:00", "2026-03-28 10:30:00")));
        when(settingMapper.selectSettingByKey("practitionerInterval")).thenReturn(setting("practitionerInterval", "20"));
        when(settingMapper.selectSettingByKey("practitionerIntervals"))
                .thenReturn(setting("practitionerIntervals", "{\"p-1\":30}"));

        Map<String, Object> result = service.checkSlot(
                "p-1",
                null,
                "2026-03-28 10:45:00",
                "2026-03-28 11:15:00",
                null);

        assertFalse((Boolean) result.get("available"));
        assertTrue((Boolean) result.get("practitionerConflict"));
        @SuppressWarnings("unchecked")
        List<String> conflicts = (List<String>) result.get("conflicts");
        assertTrue(conflicts.stream().anyMatch(message -> message.contains("30 minutes")));
    }

    @Test
    void checkSlot_shouldRejectCrossMidnightAndReverseTimeRange()
    {
        Map<String, Object> crossMidnight = service.checkSlot(
                "101",
                null,
                "2026-04-06 23:30:00",
                "2026-04-07 00:00:00",
                null);

        assertFalse((Boolean) crossMidnight.get("available"));
        assertTrue((Boolean) crossMidnight.get("practitionerConflict"));
        @SuppressWarnings("unchecked")
        List<String> crossMidnightConflicts = (List<String>) crossMidnight.get("conflicts");
        assertTrue(crossMidnightConflicts.stream()
                .anyMatch(message -> message.contains("same day")));

        Map<String, Object> reverse = service.checkSlot(
                "101",
                null,
                "2026-04-06 10:30:00",
                "2026-04-06 10:00:00",
                null);

        assertFalse((Boolean) reverse.get("available"));
        assertTrue((Boolean) reverse.get("practitionerConflict"));
        @SuppressWarnings("unchecked")
        List<String> reverseConflicts = (List<String>) reverse.get("conflicts");
        assertTrue(reverseConflicts.stream()
                .anyMatch(message -> message.contains("after start time")));
    }

    @Test
    void insertTcmAppointment_shouldRejectCrossMidnightTimeRange()
    {
        when(serviceTypeMapper.selectTcmServiceTypeByKey("acupuncture_new")).thenReturn(serviceType("acupuncture_new", 60));

        TcmAppointment appointment = appointment("a-1", null, "2026-04-06 23:30:00", "2026-04-07 00:00:00");
        appointment.setServiceType("acupuncture_new");

        ServiceException error = assertThrows(ServiceException.class, () -> service.insertTcmAppointment(appointment));

        assertEquals("appointment must start and end on the same day", error.getMessage());
    }

    @Test
    void updateTcmAppointment_shouldRejectReverseTimeRange()
    {
        when(serviceTypeMapper.selectTcmServiceTypeByKey("acupuncture_new")).thenReturn(serviceType("acupuncture_new", 60));
        TcmAppointment existing = appointment("a-2", "101", "2026-04-06 09:00:00", "2026-04-06 10:00:00");
        existing.setServiceType("acupuncture_new");
        when(appointmentMapper.selectTcmAppointmentById("a-2")).thenReturn(existing);

        TcmAppointment updated = appointment("a-2", "101", "2026-04-06 10:30:00", "2026-04-06 10:00:00");
        updated.setServiceType("acupuncture_new");

        ServiceException error = assertThrows(ServiceException.class, () -> service.updateTcmAppointment(updated));

        assertEquals("appointment end time must be after start time", error.getMessage());
        verify(appointmentMapper, never()).updateTcmAppointment(any());
    }

    @Test
    void checkSlot_shouldRejectLongAppointmentPastClosingBoundary()
    {
        when(userMapper.selectUserById(101L)).thenReturn(practitioner(
                101L,
                "Dr Boundary",
                "{\"serviceKeys\":[\"acupuncture_new\"],\"workingHours\":{\"monday\":[{\"start\":\"09:00\",\"end\":\"17:30\"}]}}"));

        Map<String, Object> exactClose = service.checkSlot(
                "101",
                null,
                "2026-04-06 16:30:00",
                "2026-04-06 17:30:00",
                null);
        assertTrue((Boolean) exactClose.get("available"));

        Map<String, Object> pastClose = service.checkSlot(
                "101",
                null,
                "2026-04-06 17:00:00",
                "2026-04-06 18:00:00",
                null);

        assertFalse((Boolean) pastClose.get("available"));
        @SuppressWarnings("unchecked")
        List<String> conflicts = (List<String>) pastClose.get("conflicts");
        assertTrue(conflicts.stream().anyMatch(message -> message.contains("working hours")));
    }

    @Test
    void updateTcmAppointment_shouldSkipSlotCheckWhenOnlyMetadataChanges()
    {
        TcmAppointment existing = appointment("a-2", "p-2", "2026-03-28 09:00:00", "2026-03-28 09:30:00");
        existing.setRoomId("room-1");
        when(appointmentMapper.selectTcmAppointmentById("a-2")).thenReturn(existing);

        TcmAppointment updated = appointment("a-2", "p-2", "2026-03-28 09:00:00", "2026-03-28 09:30:00");
        updated.setRoomId("room-1");
        updated.setIntakeToken("new-token");

        when(appointmentMapper.updateTcmAppointment(updated)).thenReturn(1);

        int affected = service.updateTcmAppointment(updated);

        assertEquals(1, affected);
        verify(appointmentMapper).updateTcmAppointment(updated);
        verify(appointmentMapper, never()).selectOverlappingAppointments(anyString(), any(), anyString(), anyString(), any());
        verify(appointmentMapper, never()).selectTcmAppointmentList(any());
    }

    @Test
    void getAvailability_shouldAggregatePractitionersByConfiguredSortOrder()
    {
        when(serviceTypeMapper.selectTcmServiceTypeByKey("acupuncture_new")).thenReturn(serviceType("acupuncture_new", 60));
        when(userMapper.selectActiveUserIds()).thenReturn(Arrays.asList(11L, 12L));
        when(userMapper.selectUserById(11L)).thenReturn(practitioner(
                11L,
                "Dr A",
                "{\"practitionerSortOrder\":2,\"serviceKeys\":[\"acupuncture_new\"],\"workingHours\":{\"wednesday\":[{\"start\":\"09:00\",\"end\":\"10:30\"}]}}"));
        when(userMapper.selectUserById(12L)).thenReturn(practitioner(
                12L,
                "Dr B",
                "{\"practitionerSortOrder\":1,\"serviceKeys\":[\"acupuncture_new\"],\"workingHours\":{\"wednesday\":[{\"start\":\"09:00\",\"end\":\"10:30\"}]}}"));
        when(appointmentMapper.selectOverlappingAppointments(anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(Collections.emptyList());
        Map<String, Object> result = service.getAvailability("2026-04-08", "acupuncture_new", null, null, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> slots = (List<Map<String, Object>>) result.get("slots");
        assertFalse(slots.isEmpty());
        @SuppressWarnings("unchecked")
        List<String> practitionerIds = (List<String>) slots.get(0).get("availablePractitionerIds");
        assertEquals(Arrays.asList("12", "11"), practitionerIds);
        assertEquals("12", slots.get(0).get("assignedPractitionerId"));
    }

    @Test
    void getAvailability_shouldAggregatePractitionersWhenNoPractitionerIsSelected()
    {
        when(serviceTypeMapper.selectTcmServiceTypeByKey("acupuncture_new")).thenReturn(serviceType("acupuncture_new", 30));
        when(userMapper.selectActiveUserIds()).thenReturn(Arrays.asList(21L, 22L));
        when(userMapper.selectUserById(21L)).thenReturn(practitioner(
                21L,
                "Dr A",
                "{\"practitionerSortOrder\":1,\"serviceKeys\":[\"acupuncture_new\"],\"workingHours\":{\"wednesday\":[{\"start\":\"09:00\",\"end\":\"11:00\"}]}}"));
        when(userMapper.selectUserById(22L)).thenReturn(practitioner(
                22L,
                "Dr B",
                "{\"practitionerSortOrder\":2,\"serviceKeys\":[\"acupuncture_new\"],\"workingHours\":{\"wednesday\":[{\"start\":\"09:00\",\"end\":\"11:00\"}]}}"));
        when(appointmentMapper.selectOverlappingAppointments(anyString(), any(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    String practitionerId = invocation.getArgument(0);
                    String startTime = invocation.getArgument(2);
                    if ("21".equals(practitionerId) && "2026-04-08 10:00:00".equals(startTime))
                    {
                        return Arrays.asList(appointment("a-occupied", "21", "2026-04-08 10:00:00", "2026-04-08 10:30:00"));
                    }
                    return Collections.emptyList();
                });

        Map<String, Object> result = service.getAvailability("2026-04-08", "acupuncture_new", null, null, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> slots = (List<Map<String, Object>>) result.get("slots");
        assertFalse(slots.isEmpty());

        Map<String, Object> nineSlot = slots.stream()
                .filter(slot -> "09:00".equals(slot.get("label")))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        List<String> nineAvailableIds = (List<String>) nineSlot.get("availablePractitionerIds");
        assertEquals(Arrays.asList("21", "22"), nineAvailableIds);
        assertEquals("21", nineSlot.get("assignedPractitionerId"));
    }

    @Test
    void insertTcmAppointment_shouldAutoAssignPractitionerWhenMissing()
    {
        when(serviceTypeMapper.selectTcmServiceTypeByKey("acupuncture_new")).thenReturn(serviceType("acupuncture_new", 60));
        when(userMapper.selectActiveUserIds()).thenReturn(Arrays.asList(21L));
        when(userMapper.selectUserById(21L)).thenReturn(practitioner(
                21L,
                "Dr C",
                "{\"practitionerSortOrder\":1,\"serviceKeys\":[\"acupuncture_new\"],\"workingHours\":{\"wednesday\":[{\"start\":\"09:00\",\"end\":\"12:00\"}]}}"));
        when(appointmentMapper.selectOverlappingAppointments(anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(Collections.emptyList());
        when(appointmentMapper.insertTcmAppointment(any())).thenReturn(1);

        TcmAppointment appointment = appointment("a-3", null, "2026-04-08 09:00:00", "2026-04-08 10:00:00");
        appointment.setServiceType("acupuncture_new");

        int affected = service.insertTcmAppointment(appointment);

        assertEquals(1, affected);
        assertEquals("21", appointment.getPractitionerId());
        verify(appointmentMapper).insertTcmAppointment(argThat(saved ->
                "21".equals(saved.getPractitionerId()) && "acupuncture_new".equals(saved.getServiceType())));
    }

    @Test
    void getAvailability_shouldRequireRoomWhenServiceNeedsRoom()
    {
        when(serviceTypeMapper.selectTcmServiceTypeByKey("room_service")).thenReturn(serviceType("room_service", 60, true));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.getAvailability("2026-04-08", "room_service", null, null, null));

        assertEquals("room is required for the selected service", error.getMessage());
    }

    @Test
    void getWeeklySchedule_shouldReturnAnonymousSlotStatesForTheWeek()
    {
        when(serviceTypeMapper.selectTcmServiceTypeByKey("acupuncture_new")).thenReturn(serviceType("acupuncture_new", 30));
        when(userMapper.selectUserById(21L)).thenReturn(practitioner(
                21L,
                "Dr C",
                "{\"practitionerSortOrder\":1,\"serviceKeys\":[\"acupuncture_new\"],\"workingHours\":{\"monday\":[{\"start\":\"09:00\",\"end\":\"11:00\"}]}}"));
        when(appointmentMapper.selectOverlappingAppointments(anyString(), any(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    String startTime = invocation.getArgument(2);
                    if ("2026-04-06 10:00:00".equals(startTime))
                    {
                        return Arrays.asList(appointment("a-occupied", "21", "2026-04-06 10:00:00", "2026-04-06 10:30:00"));
                    }
                    return Collections.emptyList();
                });

        Map<String, Object> result = service.getWeeklySchedule("2026-04-06", "acupuncture_new", "21", null);

        assertEquals("2026-04-06", result.get("weekStart"));
        assertEquals("2026-04-12", result.get("weekEnd"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> days = (List<Map<String, Object>>) result.get("days");
        assertEquals(7, days.size());

        Map<String, Object> monday = days.get(0);
        assertEquals("2026-04-06", monday.get("date"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> slots = (List<Map<String, Object>>) monday.get("slots");
        assertEquals(48, slots.size());

        Map<String, Object> bookableSlot = slots.stream()
                .filter(slot -> "09:00".equals(slot.get("time")))
                .findFirst()
                .orElseThrow();
        assertEquals("bookable", bookableSlot.get("state"));
        assertEquals("available", bookableSlot.get("status"));
        assertTrue((Boolean) bookableSlot.get("working"));
        assertTrue((Boolean) bookableSlot.get("available"));
        assertFalse((Boolean) bookableSlot.get("occupied"));

        Map<String, Object> occupiedSlot = slots.stream()
                .filter(slot -> "10:00".equals(slot.get("time")))
                .findFirst()
                .orElseThrow();
        assertEquals("occupied", occupiedSlot.get("state"));
        assertEquals("booked", occupiedSlot.get("status"));
        assertTrue((Boolean) occupiedSlot.get("working"));
        assertFalse((Boolean) occupiedSlot.get("available"));
        assertTrue((Boolean) occupiedSlot.get("occupied"));

        Map<String, Object> workingOnlySlot = slots.stream()
                .filter(slot -> "10:30".equals(slot.get("time")))
                .findFirst()
                .orElseThrow();
        assertEquals("bookable", workingOnlySlot.get("state"));
        assertEquals("available", workingOnlySlot.get("status"));
        assertTrue((Boolean) workingOnlySlot.get("working"));
        assertTrue((Boolean) workingOnlySlot.get("available"));
        assertFalse((Boolean) workingOnlySlot.get("occupied"));
    }

    @Test
    void getWeeklySchedule_shouldNotExposeLateNightSlotsForSinglePractitioner()
    {
        when(serviceTypeMapper.selectTcmServiceTypeByKey("acupuncture_new")).thenReturn(serviceType("acupuncture_new", 30));
        when(userMapper.selectUserById(101L)).thenReturn(practitioner(
                101L,
                "Dr Night",
                "{\"practitionerSortOrder\":1,\"serviceKeys\":[\"acupuncture_new\"],\"workingHours\":{\"monday\":[{\"start\":\"09:00\",\"end\":\"12:00\"},{\"start\":\"14:00\",\"end\":\"17:30\"}],\"tuesday\":[{\"start\":\"09:30\",\"end\":\"12:30\"}]}}"));
        when(appointmentMapper.selectOverlappingAppointments(anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = service.getWeeklySchedule("2026-04-06", "acupuncture_new", "101", "room-1");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> days = (List<Map<String, Object>>) result.get("days");
        Map<String, Object> monday = days.get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> slots = (List<Map<String, Object>>) monday.get("slots");

        Map<String, Object> lateSlot = slots.stream()
                .filter(slot -> "23:30".equals(slot.get("time")))
                .findFirst()
                .orElseThrow();
        assertFalse((Boolean) lateSlot.get("working"));
        assertFalse((Boolean) lateSlot.get("available"));
        assertEquals("off", lateSlot.get("status"));
        assertEquals("off", lateSlot.get("state"));

        Map<String, Object> closingSlot = slots.stream()
                .filter(slot -> "17:30".equals(slot.get("time")))
                .findFirst()
                .orElseThrow();
        assertFalse((Boolean) closingSlot.get("working"));
        assertFalse((Boolean) closingSlot.get("available"));
        assertEquals("off", closingSlot.get("status"));
        assertEquals("off", closingSlot.get("state"));
    }

    @Test
    void getWeeklySchedule_shouldNotExposeLateNightSlotsInAggregatedView()
    {
        when(serviceTypeMapper.selectTcmServiceTypeByKey("acupuncture_new")).thenReturn(serviceType("acupuncture_new", 30));
        when(userMapper.selectActiveUserIds()).thenReturn(Arrays.asList(101L, 102L));
        when(userMapper.selectUserById(101L)).thenReturn(practitioner(
                101L,
                "Dr A",
                "{\"practitionerSortOrder\":1,\"serviceKeys\":[\"acupuncture_new\"],\"workingHours\":{\"monday\":[{\"start\":\"09:00\",\"end\":\"12:00\"},{\"start\":\"14:00\",\"end\":\"17:30\"}]}}"));
        when(userMapper.selectUserById(102L)).thenReturn(practitioner(
                102L,
                "Dr B",
                "{\"practitionerSortOrder\":2,\"serviceKeys\":[\"acupuncture_new\"],\"workingHours\":{\"monday\":[{\"start\":\"09:00\",\"end\":\"12:00\"},{\"start\":\"14:00\",\"end\":\"17:30\"}]}}"));
        when(appointmentMapper.selectOverlappingAppointments(anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = service.getWeeklySchedule("2026-04-06", "acupuncture_new", null, "room-1");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> days = (List<Map<String, Object>>) result.get("days");
        Map<String, Object> monday = days.get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> slots = (List<Map<String, Object>>) monday.get("slots");

        assertTrue(slots.stream().anyMatch(slot -> "09:00".equals(slot.get("time"))));
        assertFalse(slots.stream().anyMatch(slot -> "23:30".equals(slot.get("time"))));
    }

    @Test
    void getWeeklySchedule_shouldAggregatePractitionersWhenNoPractitionerIsSelected()
    {
        when(serviceTypeMapper.selectTcmServiceTypeByKey("acupuncture_new")).thenReturn(serviceType("acupuncture_new", 30));
        when(userMapper.selectActiveUserIds()).thenReturn(Arrays.asList(21L, 22L));
        when(userMapper.selectUserById(21L)).thenReturn(practitioner(
                21L,
                "Dr A",
                "{\"practitionerSortOrder\":1,\"serviceKeys\":[\"acupuncture_new\"],\"workingHours\":{\"monday\":[{\"start\":\"09:00\",\"end\":\"11:00\"}]}}"));
        when(userMapper.selectUserById(22L)).thenReturn(practitioner(
                22L,
                "Dr B",
                "{\"practitionerSortOrder\":2,\"serviceKeys\":[\"acupuncture_new\"],\"workingHours\":{\"monday\":[{\"start\":\"09:00\",\"end\":\"11:00\"}]}}"));
        when(appointmentMapper.selectOverlappingAppointments(anyString(), any(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    String practitionerId = invocation.getArgument(0);
                    String startTime = invocation.getArgument(2);
                    if ("21".equals(practitionerId) && "2026-04-06 10:00:00".equals(startTime))
                    {
                        return Arrays.asList(appointment("a-occupied", "21", "2026-04-06 10:00:00", "2026-04-06 10:30:00"));
                    }
                    return Collections.emptyList();
                });

        Map<String, Object> result = service.getWeeklySchedule("2026-04-06", "acupuncture_new", null, null);

        assertEquals(null, result.get("practitionerId"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> days = (List<Map<String, Object>>) result.get("days");
        Map<String, Object> monday = days.get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> slots = (List<Map<String, Object>>) monday.get("slots");
        assertTrue(slots.stream().allMatch(slot -> "available".equals(slot.get("status"))));

        Map<String, Object> nineSlot = slots.stream()
                .filter(slot -> "09:00".equals(slot.get("time")))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        List<String> nineAvailableIds = (List<String>) nineSlot.get("availablePractitionerIds");
        assertTrue(!nineAvailableIds.isEmpty());
        assertEquals(nineAvailableIds.size(), nineSlot.get("availableCount"));
        assertTrue(nineAvailableIds.contains(String.valueOf(nineSlot.get("assignedPractitionerId"))));
        assertEquals("available", nineSlot.get("status"));
        assertEquals("bookable", nineSlot.get("state"));

        Map<String, Object> tenSlot = slots.stream()
                .filter(slot -> "10:00".equals(slot.get("time")))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        List<String> tenAvailableIds = (List<String>) tenSlot.get("availablePractitionerIds");
        assertTrue(!tenAvailableIds.isEmpty());
        assertEquals(tenAvailableIds.size(), tenSlot.get("availableCount"));
        assertTrue(tenAvailableIds.contains(String.valueOf(tenSlot.get("assignedPractitionerId"))));
        assertEquals("available", tenSlot.get("status"));
        assertEquals("bookable", tenSlot.get("state"));

        Map<String, Object> halfPastSlot = slots.stream()
                .filter(slot -> "10:30".equals(slot.get("time")))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        List<String> halfPastIds = (List<String>) halfPastSlot.get("availablePractitionerIds");
        assertTrue(!halfPastIds.isEmpty());
        assertEquals(halfPastIds.size(), halfPastSlot.get("availableCount"));
        assertTrue(halfPastIds.contains(String.valueOf(halfPastSlot.get("assignedPractitionerId"))));
        assertEquals("available", halfPastSlot.get("status"));
        assertEquals("bookable", halfPastSlot.get("state"));
    }

    private TcmAppointment appointment(String id, String practitionerId, String start, String end)
    {
        TcmAppointment appointment = new TcmAppointment();
        appointment.setId(id);
        appointment.setPractitionerId(practitionerId);
        appointment.setStartTime(start);
        appointment.setEndTime(end);
        appointment.setStatus("booked");
        return appointment;
    }

    private SysUser userWithId(Long userId)
    {
        SysUser user = new SysUser();
        user.setUserId(userId);
        return user;
    }

    private SysUser practitioner(Long userId, String name, String remark)
    {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setNickName(name);
        user.setRemark(remark);
        user.setStatus("0");
        SysRole role = new SysRole();
        role.setRoleId(userId);
        role.setRoleKey("practitioner");
        role.setFlag(true);
        user.setRoles(Collections.singletonList(role));
        return user;
    }

    private TcmClinicSetting setting(String key, String value)
    {
        TcmClinicSetting setting = new TcmClinicSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }

    private TcmServiceType serviceType(String key, int duration)
    {
        return serviceType(key, duration, false);
    }

    private TcmServiceType serviceType(String key, int duration, boolean roomRequired)
    {
        TcmServiceType serviceType = new TcmServiceType();
        serviceType.setServiceKey(key);
        serviceType.setDuration(duration);
        serviceType.setRoomRequired(roomRequired ? 1 : 0);
        serviceType.setDefaultPrice(BigDecimal.TEN);
        return serviceType;
    }
}
