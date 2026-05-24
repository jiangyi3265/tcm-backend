package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ruoyi.hospital.domain.TcmEmailLog;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.service.ITcmEmailLogService;
import com.ruoyi.hospital.service.ITcmEmailService;
import com.ruoyi.hospital.service.ITcmPatientService;

@ExtendWith(MockitoExtension.class)
class TcmEmailLogControllerTest
{
    @Mock
    private ITcmEmailLogService emailLogService;

    @Mock
    private ITcmEmailService emailService;

    @Mock
    private ITcmPatientService patientService;

    @Test
    void resend_shouldUseLatestPatientEmailForPatientFacingTemplate()
    {
        TcmEmailLog source = new TcmEmailLog();
        source.setId(9L);
        source.setToEmail("old@example.com");
        source.setSubject("Consent");
        source.setBody("Body");
        source.setEmailType("consent");
        source.setPayload("{\"templateKey\":\"consent\",\"variables\":{\"patientId\":\"patient-1\"}}");

        TcmPatient patient = new TcmPatient();
        patient.setId("patient-1");
        patient.setEmail("new@example.com");

        when(emailLogService.selectTcmEmailLogById(9L)).thenReturn(source);
        when(patientService.selectTcmPatientById("patient-1")).thenReturn(patient);
        when(emailService.sendTemplateAndLog(anyString(), anyString(), any(), any(), any(), anyString(), any()))
                .thenReturn(true);

        Map<String, Object> result = buildController().resend(9L, null);

        assertEquals("new@example.com", result.get("to"));
        verify(emailService).sendTemplateAndLog(
                eq("new@example.com"),
                eq("consent"),
                any(),
                eq("Consent"),
                eq("Body"),
                eq("consent"),
                any());
    }

    @Test
    void resend_shouldFindPatientByOldLoggedEmailWhenPatientIdIsMissing()
    {
        TcmEmailLog source = new TcmEmailLog();
        source.setId(10L);
        source.setToEmail("old@example.com");
        source.setSubject("Intake");
        source.setBody("Body");
        source.setEmailType("intake");
        source.setPayload("{\"templateKey\":\"intake\",\"variables\":{}}");

        TcmPatient patient = new TcmPatient();
        patient.setId("patient-1");
        patient.setEmail("new@example.com");
        patient.setPayload("{\"emails\":[\"old@example.com\"]}");

        when(emailLogService.selectTcmEmailLogById(10L)).thenReturn(source);
        when(patientService.selectTcmPatientList(any(TcmPatient.class))).thenReturn(Collections.singletonList(patient));
        when(emailService.sendTemplateAndLog(anyString(), anyString(), any(), any(), any(), anyString(), any()))
                .thenReturn(true);

        Map<String, Object> result = buildController().resend(10L, null);

        assertEquals("new@example.com", result.get("to"));
        verify(emailService).sendTemplateAndLog(
                eq("new@example.com"),
                eq("intake"),
                any(),
                eq("Intake"),
                eq("Body"),
                eq("intake"),
                any());
    }

    private TcmEmailLogController buildController()
    {
        TcmEmailLogController controller = new TcmEmailLogController();
        ReflectionTestUtils.setField(controller, "emailLogService", emailLogService);
        ReflectionTestUtils.setField(controller, "emailService", emailService);
        ReflectionTestUtils.setField(controller, "patientService", patientService);
        return controller;
    }
}
