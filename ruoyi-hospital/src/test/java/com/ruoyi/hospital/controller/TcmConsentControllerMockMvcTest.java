package com.ruoyi.hospital.controller;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.service.ITcmPatientService;

@ExtendWith(MockitoExtension.class)
class TcmConsentControllerMockMvcTest
{
    @Mock
    private ITcmPatientService patientService;

    @Mock
    private ITcmAuditLogService auditLogService;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp()
    {
        TcmConsentController controller = new TcmConsentController();
        ReflectionTestUtils.setField(controller, "patientService", patientService);
        ReflectionTestUtils.setField(controller, "auditLogService", auditLogService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new TcmExceptionHandler())
                .build();
    }

    @Test
    void getConsentInfo_shouldReturnStructuredSectionsJson() throws Exception
    {
        TcmPatient patient = new TcmPatient();
        patient.setId("patient-1");
        patient.setName("张三");
        patient.setConsentSigned(0);
        when(patientService.selectByConsentToken("consent-token")).thenReturn(patient);

        mockMvc.perform(get("/api/consent/{token}", "consent-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientName").value("张三"))
                .andExpect(jsonPath("$.consentSigned").value(0))
                .andExpect(jsonPath("$.consentVersion").value("otcm-consent-2026-04"))
                .andExpect(jsonPath("$.sections[0].key").value("patient_consent"))
                .andExpect(jsonPath("$.sections[0].paragraphs[0]").isNotEmpty());
    }

    @Test
    void signConsent_shouldAcceptAcknowledgementsJsonAndReturnSignedAt() throws Exception
    {
        TcmPatient patient = new TcmPatient();
        patient.setId("patient-2");
        patient.setName("李四");
        patient.setConsentSignedAt("2026-04-19 10:30:00");

        when(patientService.signConsentByToken(eq("consent-token"), eq("李四"), anyMap())).thenReturn(patient);

        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> acknowledgements = new LinkedHashMap<>();
        acknowledgements.put("patient_consent", true);
        acknowledgements.put("risks_and_side_effects", true);
        acknowledgements.put("herbal_medicine_and_pregnancy", true);
        acknowledgements.put("medical_history_disclosure", true);
        acknowledgements.put("confidentiality", true);
        acknowledgements.put("consent_statement", true);
        acknowledgements.put("financial_obligations", true);
        acknowledgements.put("cancellation_policy", true);
        acknowledgements.put("liability_clause", true);
        body.put("signatureName", "李四");
        body.put("sectionAcknowledgements", acknowledgements);

        mockMvc.perform(post("/api/consent/{token}/sign", "consent-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.patientName").value("李四"))
                .andExpect(jsonPath("$.consentSignedAt").value("2026-04-19 10:30:00"));
    }

    @Test
    void getConsentInfo_shouldReturnBadRequestWhenTokenInvalid() throws Exception
    {
        when(patientService.selectByConsentToken("bad-token")).thenThrow(new ServiceException("令牌无效或已过期"));

        mockMvc.perform(get("/api/consent/{token}", "bad-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("令牌无效或已过期"));
    }
}
