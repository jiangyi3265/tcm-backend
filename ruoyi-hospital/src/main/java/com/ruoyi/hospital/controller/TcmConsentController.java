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

/**
 * 同意书公开签署接口（无需登录）
 */
@Anonymous
@RestController
@RequestMapping("/api/consent")
public class TcmConsentController
{
    @Autowired
    private ITcmPatientService patientService;

    @Autowired
    private ITcmAuditLogService auditLogService;

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
        return ResponseEntity.ok(result);
    }

    /**
     * 通过令牌签署同意书（公开接口）
     */
    @PostMapping("/{token}/sign")
    public ResponseEntity<Map<String, Object>> signConsent(@PathVariable String token,
            @RequestBody Map<String, String> body)
    {
        String signatureName = body.get("signatureName");
        TcmPatient patient = patientService.signConsentByToken(token, signatureName);
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
        result.put("consentSignedAt", patient.getConsentSignedAt());
        return ResponseEntity.ok(result);
    }
}
