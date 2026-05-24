package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmBranch;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.domain.TcmRoom;
import com.ruoyi.hospital.domain.TcmServiceType;
import com.ruoyi.hospital.mapper.TcmAppointmentMapper;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.service.ITcmBranchService;
import com.ruoyi.hospital.service.ITcmEmailService;
import com.ruoyi.hospital.service.ITcmPatientService;
import com.ruoyi.hospital.service.ITcmRoomService;
import com.ruoyi.hospital.service.ITcmServiceTypeService;
import com.ruoyi.system.mapper.SysUserMapper;

@ExtendWith(MockitoExtension.class)
class TcmAppointmentNotificationServiceImplTest
{
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter MYSQL_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Mock
    private TcmAppointmentMapper appointmentMapper;

    @Mock
    private ITcmPatientService patientService;

    @Mock
    private ITcmEmailService emailService;

    @Mock
    private ITcmBranchService branchService;

    @Mock
    private ITcmRoomService roomService;

    @Mock
    private ITcmServiceTypeService serviceTypeService;

    @Mock
    private TcmClinicSettingMapper clinicSettingMapper;

    @Mock
    private SysUserMapper userMapper;

    private TcmAppointmentNotificationServiceImpl service;

    @BeforeEach
    void setUp()
    {
        service = new TcmAppointmentNotificationServiceImpl();
        ReflectionTestUtils.setField(service, "appointmentMapper", appointmentMapper);
        ReflectionTestUtils.setField(service, "patientService", patientService);
        ReflectionTestUtils.setField(service, "emailService", emailService);
        ReflectionTestUtils.setField(service, "branchService", branchService);
        ReflectionTestUtils.setField(service, "roomService", roomService);
        ReflectionTestUtils.setField(service, "serviceTypeService", serviceTypeService);
        ReflectionTestUtils.setField(service, "clinicSettingMapper", clinicSettingMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "publicAppBaseUrl", "http://test.local");
    }

    @Test
    void handleAppointmentCreated_shouldSendConfirmationAndInternalBookingEmails()
    {
        TcmAppointment appointment = appointment(
                "apt-1",
                "pat-1",
                "branch-1",
                "101",
                "room-1",
                "acupuncture_new",
                "booked",
                LocalDateTime.now(CLINIC_ZONE).plusDays(2),
                "{}");
        AtomicReference<TcmAppointment> stored = new AtomicReference<>(appointment);
        when(appointmentMapper.selectTcmAppointmentById("apt-1")).thenAnswer(invocation -> stored.get());
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        }).when(appointmentMapper).updateTcmAppointment(any(TcmAppointment.class));

        TcmPatient patient = patient("pat-1", "张三", "patient@example.com", 0, "{}");
        when(patientService.selectTcmPatientById("pat-1")).thenReturn(patient);
        when(patientService.generateConsentToken("pat-1")).thenReturn("consent-token-1");
        when(branchService.selectTcmBranchById("branch-1"))
                .thenReturn(branch("branch-1", "仁和中医", "深圳市南山区科技园", "branch@example.com"));
        when(roomService.selectTcmRoomById("room-1")).thenReturn(room("room-1", "A诊室"));
        when(serviceTypeService.selectByKey("acupuncture_new")).thenReturn(serviceType("acupuncture_new", "针灸", 60));
        when(userMapper.selectUserById(101L)).thenReturn(practitioner(101L, "李医生", "doctor@example.com"));

        service.handleAppointmentCreated(appointment);

        verify(patientService).generateConsentToken("pat-1");
        verify(emailService).sendTemplateAndLog(
                eq("patient@example.com"),
                eq("appointmentConfirmation"),
                argThat(variables -> String.valueOf(variables.get("manageLink")).contains("/manage/")
                        && String.valueOf(variables.get("intakeLink")).contains("/intake/")
                        && String.valueOf(variables.get("consentLink")).contains("/consent/")),
                eq("仁和中医｜预约确认"),
                anyString(),
                eq("appointment_confirmation"));
        verify(emailService).sendTemplateAndLog(
                eq("branch@example.com"),
                eq("internalBooking"),
                any(),
                eq("仁和中医｜新预约通知"),
                anyString(),
                eq("appointment_internal_new"));
        verify(emailService).sendTemplateAndLog(
                eq("doctor@example.com"),
                eq("internalBooking"),
                any(),
                eq("仁和中医｜新预约通知"),
                anyString(),
                eq("appointment_internal_new"));

        JSONObject payload = JSONObject.parseObject(stored.get().getPayload());
        assertTrue(StringUtils.isNotBlank(payload.getString("manageToken")));
        assertTrue(StringUtils.isNotBlank(payload.getString("confirmationEmailSentAt")));
        assertTrue(StringUtils.isNotBlank(payload.getString("internalBookingEmailSentAt")));
        assertTrue(StringUtils.isNotBlank(stored.get().getIntakeToken()));
        assertEquals(Integer.valueOf(0), stored.get().getIntakeSubmitted());
    }

    @Test
    void handleAppointmentUpdated_shouldSendChangeNotificationWhenKeyFieldsChange()
    {
        TcmAppointment before = appointment(
                "apt-2",
                "pat-2",
                "branch-1",
                "101",
                "room-1",
                "acupuncture_new",
                "booked",
                LocalDateTime.now(CLINIC_ZONE).plusDays(3),
                "{\"manageToken\":\"old-token\"}");
        TcmAppointment after = appointment(
                "apt-2",
                "pat-2",
                "branch-1",
                "101",
                "room-1",
                "acupuncture_new",
                "booked",
                LocalDateTime.now(CLINIC_ZONE).plusDays(3).plusHours(1),
                "{}");
        AtomicReference<TcmAppointment> stored = new AtomicReference<>(after);
        when(appointmentMapper.selectTcmAppointmentById("apt-2")).thenAnswer(invocation -> stored.get());
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        }).when(appointmentMapper).updateTcmAppointment(any(TcmAppointment.class));

        TcmPatient patient = patient("pat-2", "李四", "change@example.com", 1,
                "{\"latestIntakeCompleted\":true,\"latestIntakeSource\":\"public_intake_form\"}");
        when(patientService.selectTcmPatientById("pat-2")).thenReturn(patient);
        when(branchService.selectTcmBranchById("branch-1"))
                .thenReturn(branch("branch-1", "仁和中医", "深圳市南山区科技园", "branch@example.com"));
        when(roomService.selectTcmRoomById("room-1")).thenReturn(room("room-1", "A诊室"));
        when(serviceTypeService.selectByKey("acupuncture_new")).thenReturn(serviceType("acupuncture_new", "针灸", 60));
        when(userMapper.selectUserById(101L)).thenReturn(practitioner(101L, "李医生", "doctor@example.com"));

        service.handleAppointmentUpdated(before, after);

        verify(emailService).sendTemplateAndLog(
                eq("change@example.com"),
                eq("appointmentChange"),
                any(),
                eq("仁和中医｜预约变动通知"),
                anyString(),
                eq("appointment_change"));
        verify(emailService).sendTemplateAndLog(
                eq("branch@example.com"),
                eq("internalAppointmentChange"),
                any(),
                eq("仁和中医｜预约变动通知"),
                anyString(),
                eq("appointment_change_internal"));

        JSONObject payload = JSONObject.parseObject(stored.get().getPayload());
        assertTrue(StringUtils.isNotBlank(payload.getString("manageToken")));
        assertTrue(StringUtils.isNotBlank(payload.getString("changeEmailSentAt")));
    }

    @Test
    void handleAppointmentStatusChanged_shouldSendCancelNotificationWhenCancelled()
    {
        TcmAppointment before = appointment(
                "apt-3",
                "pat-3",
                "branch-1",
                "101",
                "room-1",
                "acupuncture_new",
                "booked",
                LocalDateTime.now(CLINIC_ZONE).plusDays(1),
                "{}");
        TcmAppointment after = appointment(
                "apt-3",
                "pat-3",
                "branch-1",
                "101",
                "room-1",
                "acupuncture_new",
                "cancelled",
                LocalDateTime.now(CLINIC_ZONE).plusDays(1),
                "{}");
        AtomicReference<TcmAppointment> stored = new AtomicReference<>(after);
        when(appointmentMapper.selectTcmAppointmentById("apt-3")).thenAnswer(invocation -> stored.get());
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        }).when(appointmentMapper).updateTcmAppointment(any(TcmAppointment.class));

        TcmPatient patient = patient("pat-3", "王五", "cancel@example.com", 1, "{}");
        when(patientService.selectTcmPatientById("pat-3")).thenReturn(patient);
        when(branchService.selectTcmBranchById("branch-1"))
                .thenReturn(branch("branch-1", "仁和中医", "深圳市南山区科技园", "branch@example.com"));
        when(roomService.selectTcmRoomById("room-1")).thenReturn(room("room-1", "A诊室"));
        when(serviceTypeService.selectByKey("acupuncture_new")).thenReturn(serviceType("acupuncture_new", "针灸", 60));
        when(userMapper.selectUserById(101L)).thenReturn(practitioner(101L, "李医生", "doctor@example.com"));

        service.handleAppointmentStatusChanged(before, after);

        verify(emailService).sendTemplateAndLog(
                eq("cancel@example.com"),
                eq("appointmentCancellation"),
                any(),
                eq("仁和中医｜预约取消通知"),
                anyString(),
                eq("appointment_cancel"));
        verify(emailService).sendTemplateAndLog(
                eq("branch@example.com"),
                eq("internalAppointmentCancellation"),
                any(),
                eq("仁和中医｜预约取消通知"),
                anyString(),
                eq("appointment_cancel_internal"));

        JSONObject payload = JSONObject.parseObject(stored.get().getPayload());
        assertTrue(StringUtils.isNotBlank(payload.getString("cancelledAt")));
        assertEquals("system", payload.getString("cancellationSource"));
    }

    @Test
    void handleAppointmentStatusChanged_shouldSendAftercareNotificationWhenCompleted()
    {
        TcmAppointment before = appointment(
                "apt-4",
                "pat-4",
                "branch-1",
                "101",
                "room-1",
                "acupuncture_new",
                "booked",
                LocalDateTime.now(CLINIC_ZONE).plusDays(1),
                "{}");
        TcmAppointment after = appointment(
                "apt-4",
                "pat-4",
                "branch-1",
                "101",
                "room-1",
                "acupuncture_new",
                "completed",
                LocalDateTime.now(CLINIC_ZONE).plusDays(1),
                "{}");
        AtomicReference<TcmAppointment> stored = new AtomicReference<>(after);
        when(appointmentMapper.selectTcmAppointmentById("apt-4")).thenAnswer(invocation -> stored.get());
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        }).when(appointmentMapper).updateTcmAppointment(any(TcmAppointment.class));

        TcmPatient patient = patient("pat-4", "赵六", "aftercare@example.com", 1, "{}");
        when(patientService.selectTcmPatientById("pat-4")).thenReturn(patient);
        when(branchService.selectTcmBranchById("branch-1"))
                .thenReturn(branch("branch-1", "仁和中医", "深圳市南山区科技园", "branch@example.com"));

        service.handleAppointmentStatusChanged(before, after);

        verify(emailService).sendTemplateAndLog(
                eq("aftercare@example.com"),
                eq("aftercare"),
                any(),
                eq("仁和中医｜治疗后护理提醒"),
                anyString(),
                eq("appointment_aftercare"));

        JSONObject payload = JSONObject.parseObject(stored.get().getPayload());
        assertTrue(StringUtils.isNotBlank(payload.getString("treatmentCompletedAt")));
    }

    @Test
    void processDueNotifications_shouldSendReminderAndFollowUpForDueAppointments()
    {
        LocalDateTime now = LocalDateTime.now(CLINIC_ZONE);
        String reminderStart = now.plusHours(2).format(MYSQL_DATETIME);
        String completedAt = now.minusDays(3).minusMinutes(5).format(MYSQL_DATETIME);

        TcmAppointment reminder = appointment(
                "apt-5",
                "pat-5",
                "branch-1",
                "101",
                "room-1",
                "acupuncture_new",
                "booked",
                LocalDateTime.parse(reminderStart, MYSQL_DATETIME),
                "{}");
        TcmAppointment followUp = appointment(
                "apt-6",
                "pat-6",
                "branch-1",
                "101",
                "room-1",
                "acupuncture_new",
                "completed",
                now.minusDays(4),
                "{\"treatmentCompletedAt\":\"" + completedAt + "\"}");

        when(appointmentMapper.selectTcmAppointmentList(any())).thenReturn(Arrays.asList(reminder, followUp));
        when(patientService.selectTcmPatientById("pat-5")).thenReturn(patient("pat-5", "提醒患者", "reminder@example.com", 1, "{}"));
        when(patientService.selectTcmPatientById("pat-6")).thenReturn(patient("pat-6", "回访患者", "follow@example.com", 1, "{}"));
        when(branchService.selectTcmBranchById("branch-1"))
                .thenReturn(branch("branch-1", "仁和中医", "深圳市南山区科技园", "branch@example.com"));

        service.processDueNotifications();

        verify(emailService).sendTemplateAndLog(
                eq("reminder@example.com"),
                eq("reminder"),
                any(),
                eq("仁和中医｜预约提醒"),
                anyString(),
                eq("appointment_reminder"));
        verify(emailService).sendTemplateAndLog(
                eq("follow@example.com"),
                eq("followUp"),
                any(),
                eq("仁和中医｜治疗后回访"),
                anyString(),
                eq("appointment_follow_up"));
        verify(emailService, times(2)).sendTemplateAndLog(anyString(), anyString(), any(), anyString(), anyString(), anyString());
    }

    private TcmAppointment appointment(String id, String patientId, String branchId, String practitionerId,
            String roomId, String serviceType, String status, LocalDateTime startTime, String payload)
    {
        TcmAppointment appointment = new TcmAppointment();
        appointment.setId(id);
        appointment.setPatientId(patientId);
        appointment.setBranchId(branchId);
        appointment.setPractitionerId(practitionerId);
        appointment.setRoomId(roomId);
        appointment.setServiceType(serviceType);
        appointment.setStatus(status);
        appointment.setStartTime(startTime.format(MYSQL_DATETIME));
        appointment.setEndTime(startTime.plusMinutes(60).format(MYSQL_DATETIME));
        appointment.setPayload(payload);
        return appointment;
    }

    private TcmPatient patient(String id, String name, String email, Integer consentSigned, String payload)
    {
        TcmPatient patient = new TcmPatient();
        patient.setId(id);
        patient.setName(name);
        patient.setEmail(email);
        patient.setConsentSigned(consentSigned);
        patient.setPayload(payload);
        return patient;
    }

    private TcmBranch branch(String id, String name, String address, String email)
    {
        TcmBranch branch = new TcmBranch();
        branch.setId(id);
        branch.setName(name);
        branch.setAddress(address);
        branch.setEmail(email);
        return branch;
    }

    private TcmRoom room(String id, String name)
    {
        TcmRoom room = new TcmRoom();
        room.setId(id);
        room.setName(name);
        return room;
    }

    private TcmServiceType serviceType(String key, String label, Integer duration)
    {
        TcmServiceType serviceType = new TcmServiceType();
        serviceType.setServiceKey(key);
        serviceType.setLabel(label);
        serviceType.setDuration(duration);
        serviceType.setRoomRequired(1);
        return serviceType;
    }

    private SysUser practitioner(long id, String nickName, String email)
    {
        SysUser user = new SysUser();
        user.setUserId(id);
        user.setNickName(nickName);
        user.setEmail(email);
        return user;
    }
}
