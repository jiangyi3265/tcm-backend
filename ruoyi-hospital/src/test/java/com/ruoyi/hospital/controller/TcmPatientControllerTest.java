package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.service.ITcmAppointmentService;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.service.ITcmConsultationService;
import com.ruoyi.hospital.service.ITcmEmailService;
import com.ruoyi.hospital.service.ITcmPatientService;

@ExtendWith(MockitoExtension.class)
class TcmPatientControllerTest
{
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private ITcmPatientService patientService;

    @Mock
    private ITcmConsultationService consultationService;

    @Mock
    private ITcmAppointmentService appointmentService;

    @Mock
    private ITcmAuditLogService auditLogService;

    @Mock
    private ITcmEmailService emailService;

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_shouldSanitizePatientPayloadForApprentice()
    {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        loginAsApprentice(today.toString());

        TcmPatient patient = patient("p-1");
        TcmConsultation consultation = consultation("c-1", "p-1", today.toString());

        when(patientService.selectTcmPatientList(any(TcmPatient.class))).thenReturn(Collections.singletonList(patient));
        when(consultationService.selectTcmConsultationList(any(TcmConsultation.class))).thenReturn(Collections.singletonList(consultation));
        when(appointmentService.selectTcmAppointmentList(any(TcmAppointment.class))).thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = buildController().list();

        assertEquals(1, result.size());
        assertSanitizedPatient(result.get(0));
    }

    @Test
    void get_shouldSanitizePatientPayloadForApprentice()
    {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        loginAsApprentice(today.minusDays(1).toString());

        TcmPatient patient = patient("p-1");
        TcmConsultation consultation = consultation("c-1", "p-1", today.toString());

        when(patientService.selectTcmPatientById("p-1")).thenReturn(patient);
        when(consultationService.selectTcmConsultationList(any(TcmConsultation.class))).thenReturn(Collections.singletonList(consultation));
        when(appointmentService.selectTcmAppointmentList(any(TcmAppointment.class))).thenReturn(Collections.emptyList());

        Map<String, Object> result = buildController().get("p-1");

        assertSanitizedPatient(result);
    }

    @Test
    void sendConsentEmail_shouldPreferConfiguredPublicBaseUrlOverClientOrigin()
    {
        loginAsAdmin();

        TcmPatient patient = patient("p-1");
        when(patientService.selectTcmPatientById("p-1")).thenReturn(patient);
        when(patientService.generateConsentToken("p-1")).thenReturn("consent-token");
        when(emailService.sendAndLog(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        TcmPatientController controller = buildController();
        ReflectionTestUtils.setField(controller, "publicAppBaseUrl", "https://www.otcm.app");

        Map<String, Object> result = controller.sendConsentEmail(
                "p-1",
                Collections.singletonMap("appBaseUrl", "http://127.0.0.1:5173"));

        assertEquals("https://www.otcm.app/consent/consent-token", result.get("publicLink"));
        verify(emailService).sendAndLog(
                anyString(),
                anyString(),
                org.mockito.ArgumentMatchers.contains("https://www.otcm.app/consent/consent-token"),
                anyString());
    }

    private void assertSanitizedPatient(Map<String, Object> patient)
    {
        assertEquals("p-1", patient.get("id"));
        assertEquals("Patient One", patient.get("name"));
        assertFalse(patient.containsKey("email"));
        assertFalse(patient.containsKey("phone"));
        assertFalse(patient.containsKey("emails"));
        assertFalse(patient.containsKey("mobilePhone"));
        assertFalse(patient.containsKey("notes"));
        assertFalse(patient.containsKey("historyAndMedication"));
        assertFalse(patient.containsKey("addressStreet"));
        assertFalse(patient.containsKey("consentSigned"));
    }

    private TcmPatientController buildController()
    {
        TcmPatientController controller = new TcmPatientController();
        ReflectionTestUtils.setField(controller, "patientService", patientService);
        ReflectionTestUtils.setField(controller, "consultationService", consultationService);
        ReflectionTestUtils.setField(controller, "appointmentService", appointmentService);
        ReflectionTestUtils.setField(controller, "auditLogService", auditLogService);
        ReflectionTestUtils.setField(controller, "emailService", emailService);
        return controller;
    }

    private void loginAsAdmin()
    {
        SysUser user = new SysUser();
        user.setUserId(1L);

        SysRole role = new SysRole();
        role.setRoleId(1L);
        role.setRoleKey("admin");
        role.setFlag(true);
        user.setRoles(Collections.singletonList(role));

        LoginUser loginUser = new LoginUser(user, Collections.emptySet());
        loginUser.setUserId(1L);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
    }

    private void loginAsApprentice(String internshipDate)
    {
        SysUser user = new SysUser();
        user.setUserId(88L);
        user.setRemark("{\"internshipDates\":[\"" + internshipDate + "\"]}");

        SysRole role = new SysRole();
        role.setRoleId(7L);
        role.setRoleKey("apprentice");
        role.setFlag(true);
        user.setRoles(Collections.singletonList(role));

        LoginUser loginUser = new LoginUser(user, Collections.emptySet());
        loginUser.setUserId(88L);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
    }

    private TcmPatient patient(String id)
    {
        TcmPatient patient = new TcmPatient();
        patient.setId(id);
        patient.setName("Patient One");
        patient.setFirstName("One");
        patient.setLastName("Patient");
        patient.setEmail("patient@example.com");
        patient.setPhone("123456");
        patient.setPractitionerId("doctor-1");
        patient.setIsActive(1);
        patient.setPayload(
                "{\"emails\":[\"patient@example.com\"],\"mobilePhone\":\"555-0001\",\"notes\":\"sensitive note\",\"historyAndMedication\":\"sensitive history\",\"addressStreet\":\"123 Main St\"}");
        return patient;
    }

    private TcmConsultation consultation(String id, String patientId, String consultDate)
    {
        TcmConsultation consultation = new TcmConsultation();
        consultation.setId(id);
        consultation.setPatientId(patientId);
        consultation.setPractitionerId("doctor-1");
        consultation.setConsultDate(consultDate);
        consultation.setStatus("completed");
        return consultation;
    }
}
