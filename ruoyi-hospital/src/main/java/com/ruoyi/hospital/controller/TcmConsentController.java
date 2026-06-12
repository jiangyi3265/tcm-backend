package com.ruoyi.hospital.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.service.ITcmPatientService;
import com.ruoyi.hospital.service.ITcmSettingsService;
import com.ruoyi.hospital.util.ConsentDocumentTemplate;

/**
 * 同意书公开签署接口（无需登录）
 */
@Anonymous
@RestController
@RequestMapping("/api/consent")
public class TcmConsentController
{
    private static final String DEFAULT_CLINIC_NAME = "OTCM Acupuncture Clinic";

    @Autowired
    private ITcmPatientService patientService;

    @Autowired
    private ITcmAuditLogService auditLogService;

    @Autowired
    private ITcmSettingsService settingsService;

    /**
     * 根据令牌获取同意书信息（公开接口）
     */
    @GetMapping("/{token}")
    public ResponseEntity<Map<String, Object>> getConsentInfo(@PathVariable String token)
    {
        TcmPatient patient = patientService.selectByConsentToken(token);
        if (patient == null)
        {
            Map<String, Object> error = new HashMap<>();
            error.put("message", "同意书链接不存在或已失效");
            return ResponseEntity.status(404).body(error);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("patientName", patient.getName());
        result.put("consentSigned", patient.getConsentSigned());
        Map<String, Object> settings = settingsService.getBundle();
        Object consentTemplate = settings.get("consentTemplate");
        result.put("consentTitle", ConsentDocumentTemplate.getTitle(consentTemplate));
        result.put("consentVersion", ConsentDocumentTemplate.getVersion(consentTemplate));
        result.put("sections", ConsentDocumentTemplate.toResponseSections(consentTemplate));
        result.put("clinicName", normalizeClinicName(settings.get("clinicName")));
        result.put("clinicAddress", settings.getOrDefault("clinicAddress", ""));
        result.put("clinicPhone", settings.getOrDefault("clinicPhone", ""));
        return ResponseEntity.ok(result);
    }

    /**
     * 通过令牌签署同意书（公开接口）
     */
    @PostMapping("/{token}/sign")
    public ResponseEntity<Map<String, Object>> signConsent(@PathVariable String token,
            @RequestBody Map<String, Object> body)
    {
        String signatureName = body.get("signatureName") != null ? String.valueOf(body.get("signatureName")) : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> sectionAcknowledgements = body.get("sectionAcknowledgements") instanceof Map<?, ?>
                ? (Map<String, Object>) body.get("sectionAcknowledgements")
                : null;
        TcmPatient patient = patientService.signConsentByToken(token, signatureName, sectionAcknowledgements);
        if (patient == null)
        {
            Map<String, Object> error = new HashMap<>();
            error.put("message", "同意书链接不存在或已失效");
            return ResponseEntity.status(404).body(error);
        }
        auditLogService.log("patient", patient.getId(), patient.getName(),
                "CONSENT", "public:consent",
                signatureName != null && !signatureName.isEmpty()
                        ? "患者通过公开链接签署知情同意书: " + signatureName
                        : "患者通过公开链接签署知情同意书");
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("patientName", patient.getName());
        result.put("consentSigned", patient.getConsentSigned());
        result.put("consentSignedAt", patient.getConsentSignedAt());
        return ResponseEntity.ok(result);
    }

    private String normalizeClinicName(Object value)
    {
        String text = value != null ? String.valueOf(value).trim() : "";
        if (text.isEmpty()
                || "TCM Clinic".equalsIgnoreCase(text)
                || "TCM Clinic Management System".equalsIgnoreCase(text)
                || "\u8bca\u6240".equals(text))
        {
            return DEFAULT_CLINIC_NAME;
        }
        return text;
    }
}
