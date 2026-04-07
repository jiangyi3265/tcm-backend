package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    }

    @Test
    void options_shouldWorkWithoutLoginContext()
    {
        TcmServiceType serviceType = new TcmServiceType();
        serviceType.setServiceKey("acupuncture_new");
        serviceType.setLabel("针灸");
        when(serviceTypeService.selectAll()).thenReturn(Collections.singletonList(serviceType));

        TcmRoom room = new TcmRoom();
        room.setId("room-1");
        room.setName("治疗室A");
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
        verify(userMapper).selectActiveUserIds();
        verify(userMapper).selectUserById(42L);
    }

    @Test
    void availability_shouldDelegateToAppointmentService()
    {
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("date", "2026-04-06");
        when(appointmentService.getAvailability("2026-04-06", "acupuncture_new", "42", "room-1", null))
                .thenReturn(expected);

        Map<String, Object> result = controller.availability("2026-04-06", "acupuncture_new", "42", "room-1");

        assertEquals(expected, result);
        verify(appointmentService).getAvailability("2026-04-06", "acupuncture_new", "42", "room-1", null);
    }

    @Test
    void schedule_shouldAcceptWeekStartWithoutDate()
    {
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("weekStart", "2026-04-06");
        when(appointmentService.getWeeklySchedule("2026-04-06", "acupuncture_new", null, null))
                .thenReturn(expected);

        Map<String, Object> result = controller.schedule(null, "2026-04-06", "acupuncture_new", null, null);

        assertEquals(expected, result);
        verify(appointmentService).getWeeklySchedule("2026-04-06", "acupuncture_new", null, null);
    }
}
