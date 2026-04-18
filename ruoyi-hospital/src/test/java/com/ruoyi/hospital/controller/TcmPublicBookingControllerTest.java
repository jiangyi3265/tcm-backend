package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.ruoyi.hospital.domain.TcmRoom;
import com.ruoyi.hospital.domain.TcmServiceType;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.service.ITcmAppointmentService;
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
}
