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
import com.ruoyi.system.service.ISysUserService;

@ExtendWith(MockitoExtension.class)
class TcmAppointmentServiceImplTest
{
    @Mock
    private TcmAppointmentMapper appointmentMapper;

    @Mock
    private TcmClinicSettingMapper settingMapper;

    @Mock
    private ISysUserService userService;

    @Mock
    private TcmServiceTypeMapper serviceTypeMapper;

    private TcmAppointmentServiceImpl service;

    @BeforeEach
    void setUp()
    {
        service = new TcmAppointmentServiceImpl();
        ReflectionTestUtils.setField(service, "appointmentMapper", appointmentMapper);
        ReflectionTestUtils.setField(service, "settingMapper", settingMapper);
        ReflectionTestUtils.setField(service, "userService", userService);
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
        when(userService.selectUserList(any())).thenReturn(Arrays.asList(userWithId(11L), userWithId(12L)));
        when(userService.selectUserById(11L)).thenReturn(practitioner(
                11L,
                "Dr A",
                "{\"practitionerSortOrder\":2,\"serviceKeys\":[\"acupuncture_new\"],\"workingHours\":{\"wednesday\":[{\"start\":\"09:00\",\"end\":\"10:30\"}]}}"));
        when(userService.selectUserById(12L)).thenReturn(practitioner(
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
    void insertTcmAppointment_shouldAutoAssignPractitionerWhenMissing()
    {
        when(serviceTypeMapper.selectTcmServiceTypeByKey("acupuncture_new")).thenReturn(serviceType("acupuncture_new", 60));
        when(userService.selectUserList(any())).thenReturn(Arrays.asList(userWithId(21L)));
        when(userService.selectUserById(21L)).thenReturn(practitioner(
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
        user.setRoles(List.of(role));
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
