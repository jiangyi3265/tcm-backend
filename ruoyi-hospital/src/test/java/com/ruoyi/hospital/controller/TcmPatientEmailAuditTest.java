package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.service.*;

class TcmPatientEmailAuditTest
{
    private final ITcmPatientService patients = mock(ITcmPatientService.class);
    private final ITcmEmailService emails = mock(ITcmEmailService.class);
    private final ITcmAuditLogService audit = mock(ITcmAuditLogService.class);
    private final TcmPatientController controller = new TcmPatientController();

    @BeforeEach void setup()
    {
        SysUser user = new SysUser(); user.setUserId(1L);
        SysRole role = new SysRole(); role.setRoleId(1L); role.setRoleKey("admin");
        user.setRoles(Collections.singletonList(role));
        LoginUser login = new LoginUser(user, Collections.emptySet()); login.setUserId(1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(login, null, Collections.emptyList()));
        TcmPatient patient = new TcmPatient(); patient.setId("patient"); patient.setName("Synthetic Patient");
        patient.setEmail("fixture@example.invalid"); patient.setIsActive(1);
        when(patients.selectTcmPatientById("patient")).thenReturn(patient);
        when(patients.generateConsentToken("patient")).thenReturn("consent-token");
        when(patients.generateIntakeToken("patient")).thenReturn("intake-token");
        ReflectionTestUtils.setField(controller, "patientService", patients);
        ReflectionTestUtils.setField(controller, "emailService", emails);
        ReflectionTestUtils.setField(controller, "auditLogService", audit);
        ReflectionTestUtils.setField(controller, "consultationService", mock(ITcmConsultationService.class));
        ReflectionTestUtils.setField(controller, "appointmentService", mock(ITcmAppointmentService.class));
        ReflectionTestUtils.setField(controller, "publicAppBaseUrl", "https://fixture.invalid/");
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest @ValueSource(strings = {"consent", "intake"})
    void failedAuditCannotRotateTheExistingLinkOrSendEmail(String type)
    {
        doThrow(new IllegalStateException("Synthetic audit failure")).when(audit)
                .log(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());

        assertThrows(IllegalStateException.class, () -> send(type));

        verify(patients, never()).generateConsentToken(anyString());
        verify(patients, never()).generateIntakeToken(anyString());
        verifyNoInteractions(emails);
    }

    @ParameterizedTest
    @CsvSource({"consent,true", "consent,false", "intake,true", "intake,false"})
    void auditAttemptPrecedesTokenAndDeliveryWhilePreservingTheActualDeliveryResult(String type, boolean sent)
    {
        when(emails.sendTemplateAndLog(anyString(), anyString(), anyMap(), isNull(), isNull(), anyString()))
                .thenReturn(sent);

        Map<String, Object> result = send(type);

        InOrder order = inOrder(audit, patients, emails);
        order.verify(audit).log(eq("patient"), eq("patient"), eq("Synthetic Patient"),
                eq("SEND_" + type.toUpperCase()), eq("1"), contains("attempt"));
        if ("consent".equals(type)) order.verify(patients).generateConsentToken("patient");
        else order.verify(patients).generateIntakeToken("patient");
        order.verify(emails).sendTemplateAndLog(eq("fixture@example.invalid"), eq(type), anyMap(),
                isNull(), isNull(), eq(type));
        order.verifyNoMoreInteractions();
        assertEquals(sent, result.get("sent"));
        assertEquals("https://fixture.invalid/" + type + "/" + type + "-token", result.get("publicLink"));
        assertEquals(type + "-token", result.get("token"));
        assertTrue(String.valueOf(result.get("message")).contains(sent ? "successfully" : "SMTP delivery failed"));
    }

    private Map<String, Object> send(String type)
    {
        return "consent".equals(type)
                ? controller.sendConsentEmail("patient", Collections.emptyMap())
                : controller.sendIntakeEmail("patient", Collections.emptyMap());
    }
}
