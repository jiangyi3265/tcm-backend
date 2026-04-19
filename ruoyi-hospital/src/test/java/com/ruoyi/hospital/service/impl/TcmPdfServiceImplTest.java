package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.alibaba.fastjson2.JSONObject;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.ruoyi.hospital.domain.TcmClinicSetting;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.domain.TcmPatientFile;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.mapper.TcmConsultationMapper;
import com.ruoyi.hospital.mapper.TcmPatientMapper;
import com.ruoyi.hospital.service.ITcmPatientFileService;
import com.ruoyi.hospital.util.ConsentDocumentTemplate;
import com.ruoyi.hospital.util.HospitalFileStorage;
import com.ruoyi.hospital.util.SignedFileUrlService;
import com.ruoyi.system.service.ISysUserService;

@ExtendWith(MockitoExtension.class)
class TcmPdfServiceImplTest
{
    @Mock
    private TcmConsultationMapper consultationMapper;

    @Mock
    private TcmPatientMapper patientMapper;

    @Mock
    private TcmClinicSettingMapper settingMapper;

    @Mock
    private SignedFileUrlService signedFileUrlService;

    @Mock
    private HospitalFileStorage hospitalFileStorage;

    @Mock
    private ISysUserService userService;

    @Mock
    private ITcmPatientFileService patientFileService;

    private TcmPdfServiceImpl service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp()
    {
        service = new TcmPdfServiceImpl();
        ReflectionTestUtils.setField(service, "consultationMapper", consultationMapper);
        ReflectionTestUtils.setField(service, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(service, "settingMapper", settingMapper);
        ReflectionTestUtils.setField(service, "signedFileUrlService", signedFileUrlService);
        ReflectionTestUtils.setField(service, "hospitalFileStorage", hospitalFileStorage);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "patientFileService", patientFileService);
    }

    @Test
    void generateConsentForm_shouldCreatePdfArchiveAndPersistMeta() throws Exception
    {
        TcmPatient patient = new TcmPatient();
        patient.setId("patient-1");
        patient.setName("张三");
        patient.setConsentSignedAt("2026-04-19 12:00:00");

        JSONObject payload = new JSONObject(new LinkedHashMap<>());
        payload.put("consentDocumentTitle", "OTCM Informed Consent / OTCM 知情同意书");
        payload.put("consentVersion", ConsentDocumentTemplate.getVersion());
        payload.put("consentDocumentSections", ConsentDocumentTemplate.toResponseSections());
        JSONObject acknowledgements = new JSONObject(new LinkedHashMap<>());
        for (String key : ConsentDocumentTemplate.getSectionKeys())
        {
            acknowledgements.put(key, true);
        }
        payload.put("consentSectionAcknowledgements", acknowledgements);
        patient.setPayload(payload.toJSONString());

        Path output = tempDir.resolve("consent-test.pdf");
        when(patientMapper.selectTcmPatientById("patient-1")).thenReturn(patient);
        when(hospitalFileStorage.createResourceKey("consent", ".pdf"))
                .thenReturn("hospital-private/test/consent-test.pdf");
        when(hospitalFileStorage.resolve("hospital-private/test/consent-test.pdf")).thenReturn(output);
        when(signedFileUrlService.buildAccessUrl("hospital-private/test/consent-test.pdf"))
                .thenReturn("/api/public/files/access?resource=hospital-private/test/consent-test.pdf");
        when(settingMapper.selectSettingByKey("clinicName")).thenReturn(setting("clinicName", "仁和中医"));
        when(settingMapper.selectSettingByKey("clinicAddress")).thenReturn(setting("clinicAddress", "深圳市南山区科技园"));
        when(settingMapper.selectSettingByKey("clinicPhone")).thenReturn(setting("clinicPhone", "0755-12345678"));

        List<TcmPatient> updatedPatients = new ArrayList<>();
        doAnswer(invocation -> {
            updatedPatients.add(copyPatient(invocation.getArgument(0)));
            return 1;
        }).when(patientMapper).updateTcmPatient(any(TcmPatient.class));

        List<TcmPatientFile> insertedFiles = new ArrayList<>();
        doAnswer(invocation -> {
            insertedFiles.add((TcmPatientFile) invocation.getArgument(0));
            return 1;
        }).when(patientFileService).insertTcmPatientFile(any(TcmPatientFile.class));

        Map<String, String> result = service.generateConsentForm("patient-1", "Alice Zhang");

        assertEquals("hospital-private/test/consent-test.pdf", result.get("filePath"));
        assertEquals("/api/public/files/access?resource=hospital-private/test/consent-test.pdf", result.get("url"));
        assertTrue(Files.exists(output));
        assertTrue(Files.size(output) > 0);

        String pdfText = readPdfText(output);
        assertTrue(pdfText.contains("OTCM Informed Consent"));
        assertTrue(pdfText.contains(ConsentDocumentTemplate.getVersion()));
        assertTrue(pdfText.contains("Patient Consent"));
        assertTrue(pdfText.contains("Consent Statement"));
        assertTrue(pdfText.contains("Alice Zhang"));
        assertTrue(pdfText.contains("已读并同意"));

        assertEquals(1, updatedPatients.size());
        JSONObject updatedPayload = JSONObject.parseObject(updatedPatients.get(0).getPayload());
        assertEquals("hospital-private/test/consent-test.pdf", updatedPayload.getString("consentPdfPath"));
        assertEquals("/api/public/files/access?resource=hospital-private/test/consent-test.pdf",
                updatedPayload.getString("consentPdfUrl"));
        assertTrue(updatedPayload.containsKey("consentPdfGeneratedAt"));

        assertEquals(1, insertedFiles.size());
        assertEquals("patient-1", insertedFiles.get(0).getPatientId());
        assertEquals("consent_pdf", insertedFiles.get(0).getFileType());
        assertEquals("hospital-private/test/consent-test.pdf", insertedFiles.get(0).getFilePath());

        verify(patientMapper).selectTcmPatientById("patient-1");
        verify(patientMapper).updateTcmPatient(any(TcmPatient.class));
        verify(patientFileService).insertTcmPatientFile(any(TcmPatientFile.class));
        verify(hospitalFileStorage).createResourceKey("consent", ".pdf");
        verify(hospitalFileStorage).resolve("hospital-private/test/consent-test.pdf");
        verify(signedFileUrlService).buildAccessUrl("hospital-private/test/consent-test.pdf");
    }

    private String readPdfText(Path path) throws Exception
    {
        StringBuilder text = new StringBuilder();
        try (PdfReader reader = new PdfReader(path.toString()); PdfDocument document = new PdfDocument(reader))
        {
            for (int page = 1; page <= document.getNumberOfPages(); page++)
            {
                text.append(PdfTextExtractor.getTextFromPage(document.getPage(page))).append('\n');
            }
        }
        return text.toString();
    }

    private TcmClinicSetting setting(String key, String value)
    {
        TcmClinicSetting setting = new TcmClinicSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }

    private TcmPatient copyPatient(TcmPatient source)
    {
        TcmPatient copy = new TcmPatient();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setPayload(source.getPayload());
        return copy;
    }
}
