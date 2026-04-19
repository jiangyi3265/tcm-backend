package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.service.ITcmPatientService;
import com.ruoyi.hospital.util.ConsentDocumentTemplate;

@ExtendWith(MockitoExtension.class)
class TcmConsentControllerTest
{
    @Mock
    private ITcmPatientService patientService;

    @Mock
    private ITcmAuditLogService auditLogService;

    private TcmConsentController controller;

    @BeforeEach
    void setUp()
    {
        controller = new TcmConsentController();
        ReflectionTestUtils.setField(controller, "patientService", patientService);
        ReflectionTestUtils.setField(controller, "auditLogService", auditLogService);
    }

    @Test
    void getConsentInfo_shouldReturnConsentSectionsAndVersion()
    {
        TcmPatient patient = new TcmPatient();
        patient.setId("patient-1");
        patient.setName("张三");
        patient.setConsentSigned(0);
        when(patientService.selectByConsentToken("consent-token")).thenReturn(patient);

        ResponseEntity<Map<String, Object>> response = controller.getConsentInfo("consent-token");

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("张三", body.get("patientName"));
        assertEquals(0, body.get("consentSigned"));
        assertEquals(ConsentDocumentTemplate.getVersion(), body.get("consentVersion"));
        assertTrue(body.get("sections") instanceof List<?>);
        assertEquals(ConsentDocumentTemplate.getSections().size(), ((List<?>) body.get("sections")).size());
    }

    @Test
    void signConsent_shouldPassSectionAcknowledgementsToServiceAndWriteAudit()
    {
        TcmPatient patient = new TcmPatient();
        patient.setId("patient-2");
        patient.setName("李四");
        patient.setConsentSignedAt("2026-04-19 10:30:00");

        Map<String, Object> acknowledgements = new LinkedHashMap<>();
        for (String key : ConsentDocumentTemplate.getSectionKeys())
        {
            acknowledgements.put(key, true);
        }

        when(patientService.signConsentByToken("consent-token", "李四", acknowledgements)).thenReturn(patient);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("signatureName", "李四");
        body.put("sectionAcknowledgements", acknowledgements);

        ResponseEntity<Map<String, Object>> response = controller.signConsent("consent-token", body);

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> payload = response.getBody();
        assertNotNull(payload);
        assertEquals(Boolean.TRUE, payload.get("ok"));
        assertEquals("李四", payload.get("patientName"));
        assertEquals("2026-04-19 10:30:00", payload.get("consentSignedAt"));

        verify(patientService).signConsentByToken("consent-token", "李四", acknowledgements);
        verify(auditLogService).log(
                eq("patient"),
                eq("patient-2"),
                eq("李四"),
                eq("CONSENT"),
                eq("public:consent"),
                eq("患者通过公开链接签署知情同意书: 李四"));
    }
}
