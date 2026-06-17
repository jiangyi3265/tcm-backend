package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.domain.TcmRoom;
import com.ruoyi.hospital.domain.TcmServiceType;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.service.ITcmAppointmentService;
import com.ruoyi.hospital.service.ITcmAppointmentNotificationService;
import com.ruoyi.hospital.service.ITcmPatientService;
import com.ruoyi.hospital.service.ITcmRoomService;
import com.ruoyi.hospital.service.ITcmServiceTypeService;
import com.ruoyi.system.mapper.SysUserMapper;

@ExtendWith(MockitoExtension.class)
class TcmPublicBookingControllerTest
{
    @Mock
    private ITcmAppointmentService appointmentService;

    @Mock
    private ITcmPatientService patientService;

    @Mock
    private ITcmRoomService roomService;

    @Mock
    private ITcmServiceTypeService serviceTypeService;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private TcmClinicSettingMapper clinicSettingMapper;

    @Mock
    private ITcmAppointmentNotificationService appointmentNotificationService;

    private TcmPublicBookingController controller;

    @BeforeEach
    void setUp()
    {
        controller = new TcmPublicBookingController();
        ReflectionTestUtils.setField(controller, "appointmentService", appointmentService);
        ReflectionTestUtils.setField(controller, "patientService", patientService);
        ReflectionTestUtils.setField(controller, "roomService", roomService);
        ReflectionTestUtils.setField(controller, "serviceTypeService", serviceTypeService);
        ReflectionTestUtils.setField(controller, "userMapper", userMapper);
        ReflectionTestUtils.setField(controller, "clinicSettingMapper", clinicSettingMapper);
        ReflectionTestUtils.setField(controller, "appointmentNotificationService", appointmentNotificationService);
    }

    @Test
    void options_shouldWorkWithoutLoginContext()
    {
        TcmServiceType serviceType = new TcmServiceType();
        serviceType.setServiceKey("acupuncture_new");
        serviceType.setLabel("针灸");
        serviceType.setRequiredTag("acupuncture");
        when(serviceTypeService.selectAll()).thenReturn(Collections.singletonList(serviceType));

        TcmRoom room = new TcmRoom();
        room.setId("room-1");
        room.setName("治疗室A");
        room.setSupportTags("[\"acupuncture\"]");
        room.setIsActive(1);
        when(roomService.selectTcmRoomList(any(TcmRoom.class))).thenReturn(Collections.singletonList(room));

        when(userMapper.selectActiveUserIds()).thenReturn(Collections.singletonList(42L));

        SysRole practitionerRole = new SysRole();
        practitionerRole.setRoleKey("practitioner");

        SysUser practitioner = new SysUser();
        practitioner.setUserId(42L);
        practitioner.setNickName("张医生");
        practitioner.setStatus("0");
        practitioner.setRoles(Collections.singletonList(practitionerRole));
        practitioner.setRemark(new JSONObject()
                .fluentPut("serviceKeys", Collections.singletonList("acupuncture"))
                .fluentPut("practitionerSortOrder", 2)
                .fluentPut("workingHours", Collections.singletonMap("monday", Collections.emptyList()))
                .toJSONString());
        when(userMapper.selectUserById(42L)).thenReturn(practitioner);

        Map<String, Object> result = controller.options();

        assertEquals(1, ((List<?>) result.get("serviceTypes")).size());
        assertEquals(1, ((List<?>) result.get("rooms")).size());
        assertEquals(1, ((List<?>) result.get("practitioners")).size());
        @SuppressWarnings("unchecked")
        Map<String, Object> serviceTypePayload = (Map<String, Object>) ((List<?>) result.get("serviceTypes")).get(0);
        assertEquals("acupuncture", serviceTypePayload.get("requiredTag"));
        @SuppressWarnings("unchecked")
        Map<String, Object> roomPayload = (Map<String, Object>) ((List<?>) result.get("rooms")).get(0);
        assertEquals(Collections.singletonList("acupuncture"), roomPayload.get("supportTags"));
        verify(userMapper).selectActiveUserIds();
        verify(userMapper).selectUserById(42L);
    }

    @Test
    void availability_shouldReturnSlots()
    {
        stubPractitioner();

        Map<String, Object> rawDay = new LinkedHashMap<>();
        rawDay.put("date", "2026-04-06");
        rawDay.put("weekday", "monday");
        rawDay.put("slots", new ArrayList<>());
        Map<String, Object> rawSchedule = new LinkedHashMap<>();
        rawSchedule.put("slotStepMinutes", 10);
        rawSchedule.put("duration", 40);
        rawSchedule.put("practitionerBusyMinutes", 20);
        rawSchedule.put("slotMinutes", 40);
        rawSchedule.put("days", Collections.singletonList(rawDay));
        when(appointmentService.getWeeklySchedule(eq("2026-04-06"), eq("acupuncture_new"), eq("42"), eq("room-1")))
                .thenReturn(rawSchedule);

        Map<String, Object> result = controller.availability("2026-04-06", "acupuncture_new", "42", "room-1");

        assertEquals("2026-04-06", result.get("date"));
        assertEquals("acupuncture_new", result.get("serviceType"));
        assertNotNull(result.get("slots"));
    }

    @Test
    void schedule_shouldReturnWeeklyData()
    {
        stubPractitioner();

        Map<String, Object> rawSchedule = new LinkedHashMap<>();
        rawSchedule.put("slotStepMinutes", 10);
        rawSchedule.put("duration", 40);
        rawSchedule.put("practitionerBusyMinutes", 20);
        rawSchedule.put("slotMinutes", 40);
        rawSchedule.put("days", new ArrayList<>());
        when(appointmentService.getWeeklySchedule(eq("2026-04-06"), eq("acupuncture_new"), eq("42"), any()))
                .thenReturn(rawSchedule);

        Map<String, Object> result = controller.schedule(null, "2026-04-06", "acupuncture_new", "42", null);

        assertEquals("2026-04-06", result.get("weekStart"));
        assertEquals("2026-04-12", result.get("weekEnd"));
        assertEquals("acupuncture_new", result.get("serviceType"));
        assertNotNull(result.get("days"));
        assertTrue(result.get("days") instanceof List);
    }

    @Test
    void schedule_shouldReleaseOnlyLatestAvailableDripWindow()
    {
        stubPractitioner();
        LocalDate bookingDate = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(1);
        String date = bookingDate.toString();

        List<Map<String, Object>> slots = new ArrayList<>();
        slots.add(slot(date, "21:00:00", "22:00:00", "available", "room-1"));
        slots.add(slot(date, "21:30:00", "22:30:00", "available", "room-2"));
        slots.add(slot(date, "22:00:00", "23:00:00", "booked", "room-1"));
        slots.add(slot(date, "22:30:00", "23:30:00", "available", "room-2"));

        Map<String, Object> rawDay = new LinkedHashMap<>();
        rawDay.put("date", date);
        rawDay.put("weekday", bookingDate.getDayOfWeek().name().toLowerCase());
        rawDay.put("slots", slots);
        Map<String, Object> rawSchedule = new LinkedHashMap<>();
        rawSchedule.put("slotStepMinutes", 30);
        rawSchedule.put("duration", 60);
        rawSchedule.put("practitionerBusyMinutes", 30);
        rawSchedule.put("slotMinutes", 60);
        rawSchedule.put("days", Collections.singletonList(rawDay));
        when(appointmentService.getWeeklySchedule(eq(date), eq("acupuncture_new"), eq("42"), any()))
                .thenReturn(rawSchedule);

        Map<String, Object> result = controller.schedule(null, date, "acupuncture_new", "42", null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> days = (List<Map<String, Object>>) result.get("days");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> releasedSlots = (List<Map<String, Object>>) days.stream()
                .filter(day -> date.equals(day.get("date")))
                .findFirst()
                .orElseThrow()
                .get("slots");
        assertEquals(1, releasedSlots.size());
        assertEquals(date + " 22:30:00", releasedSlots.get(0).get("startTime"));
    }

    @Test
    void manageInfo_shouldDelegateToNotificationService()
    {
        Map<String, Object> manageInfo = new LinkedHashMap<>();
        manageInfo.put("token", "manage-token");
        manageInfo.put("status", "booked");
        when(appointmentNotificationService.getManageInfo("manage-token")).thenReturn(manageInfo);

        Map<String, Object> result = controller.manageInfo("manage-token");

        assertEquals("manage-token", result.get("token"));
        assertEquals("booked", result.get("status"));
        verify(appointmentNotificationService).getManageInfo("manage-token");
    }

    @Test
    void cancel_shouldTrimSourceAndDelegateToNotificationService()
    {
        TcmAppointment appointment = new TcmAppointment();
        appointment.setId("appt-1");
        appointment.setStatus("cancelled");
        appointment.setPatientId("patient-1");
        when(appointmentNotificationService.cancelByManageToken("manage-token", "patient_portal"))
                .thenReturn(appointment);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", "  patient_portal  ");

        Map<String, Object> result = controller.cancel("manage-token", body);

        assertEquals(Boolean.TRUE, result.get("ok"));
        @SuppressWarnings("unchecked")
        Map<String, Object> flattenedAppointment = (Map<String, Object>) result.get("appointment");
        assertEquals("appt-1", flattenedAppointment.get("id"));
        assertEquals("cancelled", flattenedAppointment.get("status"));
        verify(appointmentNotificationService).cancelByManageToken("manage-token", "patient_portal");
    }

    @Test
    void create_shouldTriggerAppointmentCreatedNotification()
    {
        TcmPatient patient = new TcmPatient();
        patient.setId("patient-1");
        patient.setName("张三");
        patient.setPhone("13800000000");
        patient.setPractitionerId("doctor-1");
        when(patientService.selectTcmPatientList(any(TcmPatient.class))).thenReturn(Collections.singletonList(patient));

        TcmAppointment created = new TcmAppointment();
        created.setId("appt-1");
        created.setPatientId("patient-1");
        created.setPractitionerId("doctor-1");
        created.setStatus("booked");
        created.setServiceType("acupuncture_new");
        created.setStartTime("2026-04-06 09:00:00");
        created.setEndTime("2026-04-06 09:30:00");
        when(appointmentService.selectTcmAppointmentById("appt-1")).thenReturn(created);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", "appt-1");
        body.put("patientName", "张三");
        body.put("phone", "13800000000");
        body.put("serviceType", "acupuncture_new");
        body.put("practitionerId", "doctor-1");
        body.put("startTime", "2026-04-06 09:00:00");
        body.put("endTime", "2026-04-06 09:30:00");

        Map<String, Object> result = controller.create(body);

        assertEquals("patient-1", result.get("patientId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> flattenedAppointment = (Map<String, Object>) result.get("appointment");
        assertEquals("appt-1", flattenedAppointment.get("id"));
        assertEquals("booked", flattenedAppointment.get("status"));
        verify(appointmentService).insertTcmAppointment(any(TcmAppointment.class));
        verify(appointmentNotificationService).handleAppointmentCreated(created);
    }

    private void stubPractitioner()
    {
        when(userMapper.selectActiveUserIds()).thenReturn(Collections.singletonList(42L));

        SysRole practitionerRole = new SysRole();
        practitionerRole.setRoleKey("practitioner");

        SysUser practitioner = new SysUser();
        practitioner.setUserId(42L);
        practitioner.setNickName("张医生");
        practitioner.setStatus("0");
        practitioner.setRoles(Collections.singletonList(practitionerRole));
        practitioner.setRemark(new JSONObject()
                .fluentPut("serviceKeys", Collections.singletonList("acupuncture_new"))
                .fluentPut("practitionerSortOrder", 1)
                .fluentPut("workingHours", Collections.singletonMap("monday", Collections.emptyList()))
                .toJSONString());
        when(userMapper.selectUserById(42L)).thenReturn(practitioner);
    }

    private Map<String, Object> slot(String date, String start, String end, String status, String roomId)
    {
        Map<String, Object> slot = new LinkedHashMap<>();
        slot.put("startTime", date + " " + start);
        slot.put("endTime", date + " " + end);
        slot.put("status", status);
        slot.put("roomId", roomId);
        return slot;
    }
}
