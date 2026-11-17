package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmClinicSetting;
import com.ruoyi.hospital.domain.TcmRoom;
import com.ruoyi.hospital.domain.TcmServiceType;
import com.ruoyi.hospital.mapper.TcmAppointmentMapper;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.mapper.TcmRoomMapper;
import com.ruoyi.hospital.mapper.TcmServiceTypeMapper;
import com.ruoyi.system.mapper.SysUserMapper;

/** Synthetic mixed-duration appointments; no database, messages or production records. */
class TcmInternalBookingBoundaryTest
{
    private TcmAppointmentServiceImpl service;
    private TcmAppointmentMapper appointments;
    private TcmClinicSettingMapper settings;
    private SysUser practitioner;
    private final List<TcmAppointment> booked = new ArrayList<>();

    @BeforeEach void setup()
    {
        service = new TcmAppointmentServiceImpl();
        appointments = mock(TcmAppointmentMapper.class);
        settings = mock(TcmClinicSettingMapper.class);
        TcmClinicSetting dripMinutes = new TcmClinicSetting();
        dripMinutes.setSettingKey("publicBookingDripMinutes"); dripMinutes.setSettingValue("240");
        when(settings.selectSettingByKey("publicBookingDripMinutes")).thenReturn(dripMinutes);
        TcmRoomMapper rooms = mock(TcmRoomMapper.class);
        TcmServiceTypeMapper services = mock(TcmServiceTypeMapper.class);
        SysUserMapper users = mock(SysUserMapper.class);
        ReflectionTestUtils.setField(service, "appointmentMapper", appointments);
        ReflectionTestUtils.setField(service, "settingMapper", settings);
        ReflectionTestUtils.setField(service, "roomMapper", rooms);
        ReflectionTestUtils.setField(service, "serviceTypeMapper", services);
        ReflectionTestUtils.setField(service, "userMapper", users);
        for (int minutes : new int[] {30, 60})
        {
            TcmServiceType type = new TcmServiceType();
            type.setServiceKey("visit" + minutes); type.setDuration(minutes); type.setPractitionerTime("20");
            type.setRoomRequired(1); type.setRequiredTag("acupuncture"); type.setDefaultPrice(BigDecimal.TEN);
            when(services.selectTcmServiceTypeByKey(type.getServiceKey())).thenReturn(type);
        }
        TcmRoom room = new TcmRoom();
        room.setId("room1"); room.setName("Room 1"); room.setSupportTags("[\"acupuncture\"]"); room.setIsActive(1);
        when(rooms.selectTcmRoomList(any())).thenReturn(Collections.singletonList(room));
        when(rooms.selectTcmRoomById("room1")).thenReturn(room);
        practitioner = new SysUser();
        practitioner.setUserId(101L); practitioner.setNickName("Synthetic doctor"); practitioner.setStatus("0");
        SysRole role = new SysRole(); role.setRoleKey("practitioner"); practitioner.setRoles(Collections.singletonList(role));
        profile(true);
        when(users.selectUserById(101L)).thenReturn(practitioner);
        when(users.selectActiveUserIds()).thenReturn(Collections.singletonList(101L));
        booked.add(appointment("morning", "09:30", "10:00", "visit30"));
        booked.add(appointment("noon", "12:00", "13:00", "visit60"));
        booked.add(appointment("afternoon1", "13:00", "13:30", "visit30"));
        booked.add(appointment("afternoon2", "13:30", "14:00", "visit30"));
        when(appointments.selectAppointmentsInRange(any(), any(), anyString(), anyString(), any()))
                .thenAnswer(call -> booked);
        when(appointments.selectOverlappingAppointments(any(), any(), anyString(), anyString(), any()))
                .thenAnswer(call -> booked);
        when(appointments.insertTcmAppointment(any())).thenReturn(1);
        when(appointments.updateTcmAppointment(any())).thenReturn(1);
    }

    private void profile(boolean drip)
    {
        practitioner.setRemark("{\"serviceKeys\":[\"visit30\",\"visit60\"],\"dripEnabled\":" + drip
                + ",\"workingHours\":{\"tuesday\":[{\"start\":\"09:30\",\"end\":\"16:00\"}]}}");
    }

    private TcmAppointment appointment(String id, String start, String end, String type)
    {
        TcmAppointment appointment = new TcmAppointment();
        appointment.setId(id); appointment.setPractitionerId("101"); appointment.setRoomId("room1");
        appointment.setStartTime("2026-11-17 " + start + ":00"); appointment.setEndTime("2026-11-17 " + end + ":00");
        appointment.setServiceType(type); appointment.setStatus("booked");
        return appointment;
    }

    @SuppressWarnings("unchecked")
    private List<String> labels(String doctor, String room, String exclude)
    {
        Map<String, Object> result = service.getAvailability("2026-11-17", "visit60", doctor, room, exclude);
        return ((List<Map<String, Object>>) result.get("slots")).stream()
                .map(slot -> String.valueOf(slot.get("label"))).collect(Collectors.toList());
    }

    @ParameterizedTest
    @CsvSource({"true,true,true", "true,true,false", "true,false,true", "true,false,false",
                "false,true,true", "false,true,false", "false,false,true", "false,false,false"})
    void internalAvailabilityIncludesTenAndTwoWithoutPublicDrip(boolean drip, boolean selectedDoctor, boolean selectedRoom)
    {
        profile(drip);
        List<String> times = labels(selectedDoctor ? "101" : null, selectedRoom ? "room1" : null, null);
        assertTrue(times.contains("10:00"), "The preceding 30-minute visit ends at 10:00");
        assertTrue(times.contains("14:00"), "The preceding 30-minute visit ends at 14:00");
        assertFalse(times.contains("09:30")); assertFalse(times.contains("11:30"));
        assertFalse(times.contains("13:30")); assertFalse(times.contains("15:30"));
        verify(settings, never()).selectSettingByKey("publicBookingDripMinutes");
        verify(settings, never()).selectSettingByKey("publicBookingDripWindowDays");
    }

    @ParameterizedTest @CsvSource({"10:00,11:00", "14:00,15:00"})
    void backToBackHourCanBeSavedWithoutChangingConflictRules(String start, String end)
    {
        assertEquals(1, service.insertTcmAppointment(appointment("new", start, end, "visit60")));
        verify(appointments).insertTcmAppointment(argThat(value -> value.getStartTime().endsWith(start + ":00")));
    }

    @ParameterizedTest @CsvSource({"09:30,10:30", "11:30,12:30", "13:30,14:30", "15:30,16:30"})
    void trueOverlapAndClosingBoundaryStillRejectWrites(String start, String end)
    {
        assertThrows(ServiceException.class, () -> service.insertTcmAppointment(appointment("new", start, end, "visit60")));
        verify(appointments, never()).insertTcmAppointment(any());
    }

    @Test void editExcludesOnlyItselfAndCanMoveToTheFreeBoundary()
    {
        TcmAppointment old = appointment("editing", "10:30", "11:30", "visit60");
        booked.add(old);
        assertFalse(labels("101", "room1", null).contains("10:00"));
        assertTrue(labels("101", "room1", "editing").contains("10:00"));
        assertFalse(labels("101", "room1", "editing").contains("09:30"));
        when(appointments.selectTcmAppointmentById("editing")).thenReturn(old);
        when(appointments.selectTcmAppointmentForUpdate("editing")).thenReturn(old);
        assertEquals(1, service.updateTcmAppointment(appointment("editing", "10:00", "11:00", "visit60")));
    }

    @Test void allPractitionersOffersSameOffGridWorkingStartsAsSelectedPractitioner()
    {
        booked.clear();
        practitioner.setRemark(practitioner.getRemark().replace("09:30", "09:10"));
        List<String> selected = labels("101", "room1", null);
        assertTrue(selected.contains("09:10")); assertTrue(selected.contains("09:40"));
        assertEquals(selected, labels(null, "room1", null));
    }

    @Test void internalBookingStillHonorsExplicitTimeBlocks()
    {
        booked.add(appointment("blocked", "14:00", "15:00", "time_block"));
        assertFalse(labels("101", "room1", null).contains("14:00"));
        assertThrows(ServiceException.class, () -> service.insertTcmAppointment(appointment("new", "14:00", "15:00", "visit60")));
        verify(appointments, never()).insertTcmAppointment(any());
    }
}
