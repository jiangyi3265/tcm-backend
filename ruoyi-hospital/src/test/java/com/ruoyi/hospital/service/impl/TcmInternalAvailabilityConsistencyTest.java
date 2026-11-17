package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmRoom;
import com.ruoyi.hospital.domain.TcmServiceType;
import com.ruoyi.hospital.mapper.TcmAppointmentMapper;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.mapper.TcmRoomMapper;
import com.ruoyi.hospital.mapper.TcmServiceTypeMapper;
import com.ruoyi.system.mapper.SysUserMapper;

/** Verify staff availability against the real create/edit validators using synthetic data. */
class TcmInternalAvailabilityConsistencyTest
{
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @ParameterizedTest
    @CsvSource({"30,1,false", "40,1,false", "60,1,false", "90,1,false",
                "30,2,false", "40,2,false", "60,2,false", "90,2,false",
                "30,1,true", "40,1,true", "60,1,true", "90,1,true",
                "30,2,true", "40,2,true", "60,2,true", "90,2,true"})
    @SuppressWarnings("unchecked")
    void mixedServicesRoomsAndWorkingPeriodsReturnOnlySaveableSlots(int duration, int roomCount, boolean editing)
    {
        TcmAppointmentServiceImpl service = new TcmAppointmentServiceImpl();
        TcmAppointmentMapper appointments = mock(TcmAppointmentMapper.class);
        TcmRoomMapper rooms = mock(TcmRoomMapper.class);
        TcmServiceTypeMapper services = mock(TcmServiceTypeMapper.class);
        SysUserMapper users = mock(SysUserMapper.class);
        ReflectionTestUtils.setField(service, "appointmentMapper", appointments);
        ReflectionTestUtils.setField(service, "settingMapper", mock(TcmClinicSettingMapper.class));
        ReflectionTestUtils.setField(service, "roomMapper", rooms);
        ReflectionTestUtils.setField(service, "serviceTypeMapper", services);
        ReflectionTestUtils.setField(service, "userMapper", users);
        for (int minutes : new int[] {30, 40, 60, 90})
        {
            TcmServiceType type = new TcmServiceType();
            type.setServiceKey("visit" + minutes); type.setDuration(minutes); type.setPractitionerTime("overlap1");
            type.setRoomRequired(1); type.setRequiredTag("acupuncture"); type.setDefaultPrice(BigDecimal.TEN);
            when(services.selectTcmServiceTypeByKey(type.getServiceKey())).thenReturn(type);
        }
        List<TcmRoom> availableRooms = new ArrayList<>();
        for (int index = 1; index <= roomCount; index++)
        {
            TcmRoom room = new TcmRoom();
            room.setId("room" + index); room.setName("Room " + index);
            room.setSupportTags("[\"acupuncture\"]"); room.setIsActive(1);
            availableRooms.add(room);
            when(rooms.selectTcmRoomById(room.getId())).thenReturn(room);
        }
        when(rooms.selectTcmRoomList(any())).thenReturn(availableRooms);
        when(users.selectActiveUserIds()).thenReturn(Arrays.asList(101L, 102L));
        for (long id : new long[] {101, 102})
        {
            SysUser user = new SysUser(); user.setUserId(id); user.setStatus("0"); user.setNickName("Synthetic " + id);
            SysRole role = new SysRole(); role.setRoleKey("practitioner"); user.setRoles(Collections.singletonList(role));
            user.setRemark("{\"serviceKeys\":[\"visit30\",\"visit40\",\"visit60\",\"visit90\"],\"overlap1\":"
                    + (id == 101 ? 20 : 15) + ",\"workingHours\":{\"tuesday\":[{\"start\":\""
                    + (id == 101 ? "09:30" : "09:10")
                    + "\",\"end\":\"12:00\"},{\"start\":\"13:00\",\"end\":\"17:00\"}]}}");
            when(users.selectUserById(id)).thenReturn(user);
        }
        List<TcmAppointment> booked = Arrays.asList(
                appointment("short", "101", "room1", "09:30", 30),
                appointment("forty", "101", "room1", "10:40", 40),
                appointment("hour", "102", "room1", "13:00", 60),
                appointment("long", "101", "room1", "14:00", 90),
                appointment("editing", "101", "room1", "10:00", 40),
                appointment("block", "102", null, "15:30", 30));
        booked.get(5).setServiceType("time_block");
        when(appointments.selectAppointmentsInRange(any(), any(), anyString(), anyString(), any())).thenReturn(booked);
        when(appointments.selectOverlappingAppointments(any(), any(), anyString(), anyString(), any())).thenReturn(booked);
        when(appointments.insertTcmAppointment(any())).thenReturn(1);
        when(appointments.updateTcmAppointment(any())).thenReturn(1);
        when(appointments.selectTcmAppointmentById("editing")).thenReturn(booked.get(4));
        when(appointments.selectTcmAppointmentForUpdate("editing")).thenReturn(booked.get(4));
        String excludeId = editing ? "editing" : null;
        String type = "visit" + duration;
        Set<String> perDoctorPairs = new LinkedHashSet<>();
        for (String doctor : Arrays.asList("101", "102"))
        {
            List<Map<String, Object>> slots = (List<Map<String, Object>>) service
                    .getAvailability("2026-11-17", type, doctor, null, excludeId).get("slots");
            for (Map<String, Object> slot : slots) perDoctorPairs.add(slot.get("startTime") + "/" + doctor);
        }
        List<Map<String, Object>> aggregate = (List<Map<String, Object>>) service
                .getAvailability("2026-11-17", type, null, null, excludeId).get("slots");
        assertFalse(aggregate.isEmpty());
        Set<String> aggregatedPairs = aggregate.stream().flatMap(slot -> ((List<String>) slot.get("availablePractitionerIds"))
                .stream().map(doctor -> slot.get("startTime") + "/" + doctor)).collect(Collectors.toSet());
        assertEquals(perDoctorPairs, aggregatedPairs, "Any-practitioner choices must match the individual calendars");
        for (Map<String, Object> slot : aggregate)
        {
            TcmAppointment request = new TcmAppointment();
            request.setId(editing ? "editing" : "new"); request.setStatus("booked"); request.setServiceType(type);
            request.setPractitionerId((String) slot.get("assignedPractitionerId")); request.setRoomId((String) slot.get("roomId"));
            request.setStartTime((String) slot.get("startTime")); request.setEndTime((String) slot.get("endTime"));
            assertEquals(duration, java.time.Duration.between(LocalDateTime.parse(request.getStartTime(), FORMAT),
                    LocalDateTime.parse(request.getEndTime(), FORMAT)).toMinutes());
            assertEquals(1, editing ? service.updateTcmAppointment(request) : service.insertTcmAppointment(request),
                    "An offered slot must satisfy the actual save validator: " + request.getStartTime());
        }
    }

    private TcmAppointment appointment(String id, String doctor, String room, String start, int duration)
    {
        TcmAppointment value = new TcmAppointment(); value.setId(id); value.setPractitionerId(doctor); value.setRoomId(room);
        LocalDateTime date = LocalDateTime.parse("2026-11-17 " + start + ":00", FORMAT);
        value.setStartTime(date.format(FORMAT)); value.setEndTime(date.plusMinutes(duration).format(FORMAT));
        value.setServiceType("visit" + duration); value.setStatus("booked");
        return value;
    }
}
