package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmClinicSetting;
import com.ruoyi.hospital.mapper.TcmAppointmentMapper;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
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

    private TcmAppointmentServiceImpl service;

    @BeforeEach
    void setUp()
    {
        service = new TcmAppointmentServiceImpl();
        ReflectionTestUtils.setField(service, "appointmentMapper", appointmentMapper);
        ReflectionTestUtils.setField(service, "settingMapper", settingMapper);
        ReflectionTestUtils.setField(service, "userService", userService);
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

    private TcmClinicSetting setting(String key, String value)
    {
        TcmClinicSetting setting = new TcmClinicSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }
}
