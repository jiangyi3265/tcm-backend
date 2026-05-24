package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.domain.TcmPatientFile;
import com.ruoyi.hospital.mapper.TcmAppointmentMapper;
import com.ruoyi.hospital.mapper.TcmConsultationMapper;
import com.ruoyi.hospital.mapper.TcmPatientMapper;
import com.ruoyi.hospital.service.ITcmPatientFileService;
import com.ruoyi.hospital.service.ITcmPdfService;
import com.ruoyi.hospital.util.ConsentDocumentTemplate;

@ExtendWith(MockitoExtension.class)
class TcmPatientServiceImplTest
{
    @Mock
    private TcmPatientMapper patientMapper;

    @Mock
    private TcmConsultationMapper consultationMapper;

    @Mock
    private TcmAppointmentMapper appointmentMapper;

    @Mock
    private ITcmPdfService pdfService;

    @Mock
    private ITcmPatientFileService patientFileService;

    private TcmPatientServiceImpl service;

    @BeforeEach
    void setUp()
    {
        service = new TcmPatientServiceImpl();
        ReflectionTestUtils.setField(service, "tcmPatientMapper", patientMapper);
        ReflectionTestUtils.setField(service, "tcmConsultationMapper", consultationMapper);
        ReflectionTestUtils.setField(service, "tcmAppointmentMapper", appointmentMapper);
        ReflectionTestUtils.setField(service, "pdfService", pdfService);
        ReflectionTestUtils.setField(service, "patientFileService", patientFileService);
    }

    @Test
    void mergeTcmPatients_shouldRejectSelfMerge()
    {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.mergeTcmPatients("patient-1", "patient-1"));

        assertEquals("不能合并到自身", ex.getMessage());
    }

    @Test
    void mergeTcmPatients_shouldMoveConsultationsAppointmentsAndFilesToKeptPatient()
    {
        TcmPatient keep = new TcmPatient();
        keep.setId("keep-patient");
        keep.setIsActive(1);
        TcmPatient merge = new TcmPatient();
        merge.setId("merge-patient");
        merge.setIsActive(1);

        TcmConsultation activeConsultation = new TcmConsultation();
        activeConsultation.setId("consult-active");
        activeConsultation.setPatientId("merge-patient");
        TcmConsultation deletedConsultation = new TcmConsultation();
        deletedConsultation.setId("consult-deleted");
        deletedConsultation.setPatientId("merge-patient");
        deletedConsultation.setDeletedAt("2026-04-01 10:00:00");

        TcmAppointment appointment = new TcmAppointment();
        appointment.setId("appt-1");
        appointment.setPatientId("merge-patient");

        TcmPatientFile file = new TcmPatientFile();
        file.setId(1L);
        file.setPatientId("merge-patient");

        when(patientMapper.selectTcmPatientById("keep-patient")).thenReturn(keep);
        when(patientMapper.selectTcmPatientById("merge-patient")).thenReturn(merge);
        when(consultationMapper.selectTcmConsultationList(any(TcmConsultation.class)))
                .thenReturn(Arrays.asList(activeConsultation, deletedConsultation));
        when(appointmentMapper.selectTcmAppointmentList(any(TcmAppointment.class)))
                .thenReturn(Collections.singletonList(appointment));
        when(patientFileService.selectFilesByPatientId("merge-patient"))
                .thenReturn(Collections.singletonList(file));

        service.mergeTcmPatients("keep-patient", "merge-patient");

        assertEquals("keep-patient", activeConsultation.getPatientId());
        assertEquals("keep-patient", deletedConsultation.getPatientId());
        assertEquals("keep-patient", appointment.getPatientId());
        assertEquals("keep-patient", file.getPatientId());
        verify(consultationMapper).updateTcmConsultation(activeConsultation);
        verify(consultationMapper).updateTcmConsultation(deletedConsultation);
        verify(appointmentMapper).updateTcmAppointment(appointment);
        verify(patientFileService).updateTcmPatientFile(file);
    }

    @Test
    void signConsentByToken_shouldPersistConsentSectionsAndPdfMeta()
    {
        TcmPatient patient = consentPatient("patient-1", "consent-token");
        when(patientMapper.selectTcmPatientByConsentToken("consent-token")).thenReturn(patient);

        Map<String, String> pdf = new LinkedHashMap<>();
        pdf.put("filePath", "consent/consent-1.pdf");
        pdf.put("url", "https://files.example.com/consent-1.pdf");
        when(pdfService.generateConsentForm(eq("patient-1"), anyString())).thenReturn(pdf);

        TcmPatient signed = service.signConsentByToken("consent-token", " 张三 ", acknowledgeAllSections());

        assertEquals(Integer.valueOf(1), signed.getConsentSigned());
        assertNotNull(signed.getConsentSignedAt());
        assertEquals(null, signed.getConsentToken());
        assertEquals(null, signed.getConsentTokenExpires());

        JSONObject payload = JSONObject.parseObject(signed.getPayload());
        assertEquals("张三", payload.getString("consentSignatureName"));
        assertEquals(ConsentDocumentTemplate.getVersion(), payload.getString("consentVersion"));
        assertEquals("OTCM Informed Consent / OTCM 知情同意书", payload.getString("consentDocumentTitle"));
        assertEquals(pdf.get("filePath"), payload.getString("consentPdfPath"));
        assertEquals(pdf.get("url"), payload.getString("consentPdfUrl"));

        JSONObject acknowledgements = payload.getJSONObject("consentSectionAcknowledgements");
        assertNotNull(acknowledgements);
        assertEquals(ConsentDocumentTemplate.getSectionKeys().size(), acknowledgements.size());
        for (String key : ConsentDocumentTemplate.getSectionKeys())
        {
            assertTrue(acknowledgements.getBooleanValue(key), "缺少同意段落: " + key);
        }

        JSONArray sectionKeys = payload.getJSONArray("consentSectionKeys");
        assertNotNull(sectionKeys);
        assertEquals(ConsentDocumentTemplate.getSectionKeys().size(), sectionKeys.size());

        JSONArray sections = payload.getJSONArray("consentDocumentSections");
        assertNotNull(sections);
        assertEquals(ConsentDocumentTemplate.getSections().size(), sections.size());
        assertEquals(ConsentDocumentTemplate.getSections().get(0).getKey(),
                sections.getJSONObject(0).getString("key"));

        verify(patientMapper, times(2)).updateTcmPatient(patient);
        verify(pdfService).generateConsentForm(eq("patient-1"), anyString());
    }

    @Test
    void signConsentByToken_shouldRejectWhenAnyConsentSectionIsMissing()
    {
        TcmPatient patient = consentPatient("patient-2", "consent-token-2");
        when(patientMapper.selectTcmPatientByConsentToken("consent-token-2")).thenReturn(patient);

        Map<String, Object> acknowledgements = acknowledgeAllSections();
        List<String> sectionKeys = ConsentDocumentTemplate.getSectionKeys();
        acknowledgements.remove(sectionKeys.get(sectionKeys.size() - 1));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.signConsentByToken("consent-token-2", "李四", acknowledgements));

        assertEquals("请逐段阅读并同意知情同意书后再签署", ex.getMessage());
        verify(patientMapper, never()).updateTcmPatient(any(TcmPatient.class));
        verify(pdfService, never()).generateConsentForm(anyString(), anyString());
    }

    @Test
    void saveIntakeFormByToken_shouldMarkCompletedAndSyncProfileSummary()
    {
        TcmPatient patient = new TcmPatient();
        patient.setId("patient-3");
        patient.setName("王五");
        patient.setPayload("{\"intakeToken\":\"intake-token\",\"intakeTokenExpires\":\"" + futureTimestamp() + "\"}");
        when(patientMapper.selectTcmPatientList(any(TcmPatient.class))).thenReturn(Collections.singletonList(patient));

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("chiefComplaint", "肩颈痛");
        formData.put("drugAllergies", "青霉素");
        formData.put("medicalHistorySelections", Collections.singletonList("高血压 Hypertension"));
        formData.put("currentMedicationSelections", Collections.singletonList("降压药 Antihypertensives"));
        formData.put("smokingStatus", "无");
        formData.put("signatureName", "王五");
        formData.put("signedDate", "2026/04/19");

        TcmPatient saved = service.saveIntakeFormByToken("intake-token", formData);

        JSONObject payload = JSONObject.parseObject(saved.getPayload());
        assertTrue(payload.getBooleanValue("latestIntakeCompleted"));
        assertEquals("public_intake_form", payload.getString("latestIntakeSource"));
        assertFalse(payload.containsKey("intakeToken"));
        assertFalse(payload.containsKey("intakeTokenExpires"));
        assertEquals("青霉素", payload.getString("drugAllergies"));
        assertTrue(payload.getString("historyAndMedication").contains("高血压 Hypertension"));
        assertTrue(payload.getString("historyAndMedication").contains("降压药 Antihypertensives"));
        assertTrue(payload.getString("historyAndMedication").contains("青霉素"));

        JSONObject latestIntake = payload.getJSONObject("latestIntakeFormData");
        assertNotNull(latestIntake);
        assertEquals("肩颈痛", latestIntake.getString("chiefComplaint"));
        assertEquals("王五", latestIntake.getString("signatureName"));
        assertEquals("2026/04/19", latestIntake.getString("signedDate"));
        assertEquals(1, latestIntake.getJSONArray("medicalHistorySelections").size());
        assertEquals(1, latestIntake.getJSONArray("currentMedicationSelections").size());

        verify(patientMapper).updateTcmPatient(patient);
    }

    @Test
    void savePublicBookingIntakeSummary_shouldRemainIncompleteAndMergeExistingLatestIntake()
    {
        JSONObject payload = new JSONObject(new LinkedHashMap<>());
        JSONObject latestIntake = new JSONObject(new LinkedHashMap<>());
        latestIntake.put("chiefComplaint", "头痛");
        payload.put("latestIntakeFormData", latestIntake);

        TcmPatient patient = new TcmPatient();
        patient.setId("patient-4");
        patient.setPayload(payload.toJSONString());
        when(patientMapper.selectTcmPatientById("patient-4")).thenReturn(patient);

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("medicalHistorySelections", Arrays.asList("糖尿病 Diabetes", "冠心病 Coronary Artery Disease"));
        formData.put("currentMedicationSelections", Collections.singletonList("中草药 / Chinese Herbal Medicine"));
        formData.put("otherAllergies", "乳胶");

        service.savePublicBookingIntakeSummary("patient-4", "appointment-9", formData);

        JSONObject updatedPayload = JSONObject.parseObject(patient.getPayload());
        assertFalse(updatedPayload.getBooleanValue("latestIntakeCompleted"));
        assertEquals("public_booking", updatedPayload.getString("latestIntakeSource"));
        assertEquals("appointment-9", updatedPayload.getString("latestIntakeAppointmentId"));

        JSONObject mergedLatest = updatedPayload.getJSONObject("latestIntakeFormData");
        assertEquals("头痛", mergedLatest.getString("chiefComplaint"));
        assertEquals(2, mergedLatest.getJSONArray("medicalHistorySelections").size());
        assertEquals(1, mergedLatest.getJSONArray("currentMedicationSelections").size());
        assertFalse(mergedLatest.containsKey("dietPreference"));

        String summary = updatedPayload.getString("historyAndMedication");
        assertTrue(summary.contains("糖尿病 Diabetes"));
        assertTrue(summary.contains("冠心病 Coronary Artery Disease"));
        assertTrue(summary.contains("中草药 / Chinese Herbal Medicine"));

        verify(patientMapper).updateTcmPatient(patient);
    }

    private TcmPatient consentPatient(String id, String token)
    {
        TcmPatient patient = new TcmPatient();
        patient.setId(id);
        patient.setName("张三");
        patient.setConsentSigned(0);
        patient.setConsentToken(token);
        patient.setConsentTokenExpires(futureTimestamp());
        patient.setPayload("{}");
        return patient;
    }

    private Map<String, Object> acknowledgeAllSections()
    {
        Map<String, Object> acknowledgements = new LinkedHashMap<>();
        for (String key : ConsentDocumentTemplate.getSectionKeys())
        {
            acknowledgements.put(key, true);
        }
        return acknowledgements;
    }

    private String futureTimestamp()
    {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new Date(System.currentTimeMillis() + 24L * 60L * 60L * 1000L));
    }
}
