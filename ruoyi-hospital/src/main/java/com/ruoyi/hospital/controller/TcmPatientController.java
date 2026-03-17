package com.ruoyi.hospital.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.service.ITcmConsultationService;
import com.ruoyi.hospital.service.ITcmPatientService;
import com.ruoyi.hospital.utils.PayloadUtils;
import com.ruoyi.hospital.utils.PrivacyUtils;

@RestController
@RequestMapping("/api/patients")
public class TcmPatientController
{
    @Autowired
    private ITcmPatientService patientService;

    @Autowired
    private ITcmConsultationService consultationService;

    @Autowired
    private ITcmAuditLogService auditLogService;

    @PreAuthorize("@ss.hasPermi('tcm:patient:list')")
    @GetMapping("")
    public List<Map<String, Object>> list()
    {
        List<TcmPatient> patients = patientService.selectTcmPatientList(new TcmPatient());
        List<TcmConsultation> consultations = consultationService.selectTcmConsultationList(new TcmConsultation());
        return PayloadUtils.flattenPatients(
                PrivacyUtils.filterPatients(patients, consultations));
    }

    @PreAuthorize("@ss.hasPermi('tcm:patient:query')")
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id)
    {
        TcmPatient p = patientService.selectTcmPatientById(id);
        if (p == null) { throw new ServiceException("病人不存在"); }
        List<TcmConsultation> consultations = consultationService.selectTcmConsultationList(new TcmConsultation());
        if (!PrivacyUtils.canAccessPatient(p, consultations))
        {
            throw new ServiceException("无权访问该病人档案");
        }
        return PayloadUtils.flatten(p);
    }

    @PreAuthorize("@ss.hasPermi('tcm:patient:add')")
    @PostMapping("")
    public Map<String, Object> create(@RequestBody Map<String, Object> body)
    {
        TcmPatient patient = PayloadUtils.toPatient(body);
        patientService.insertTcmPatient(patient);
        TcmPatient created = patientService.selectTcmPatientById(patient.getId());
        auditLogService.log("patient", created.getId(), created.getName(),
                "CREATE", String.valueOf(SecurityUtils.getUserId()), "新建患者档案");
        return PayloadUtils.flatten(created);
    }

    @PreAuthorize("@ss.hasPermi('tcm:patient:edit')")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id,
            @RequestBody Map<String, Object> body)
    {
        TcmPatient patient = PayloadUtils.toPatient(body);
        patient.setId(id);
        patientService.updateTcmPatient(patient);
        TcmPatient updated = patientService.selectTcmPatientById(id);
        auditLogService.log("patient", updated.getId(), updated.getName(),
                "UPDATE", String.valueOf(SecurityUtils.getUserId()), "更新患者档案");
        return PayloadUtils.flatten(updated);
    }

    @PreAuthorize("@ss.hasPermi('tcm:patient:remove')")
    @PatchMapping("/{id}/delete")
    public Map<String, Object> softDelete(@PathVariable String id)
    {
        TcmPatient patient = patientService.softDeleteTcmPatient(id);
        auditLogService.log("patient", patient.getId(), patient.getName(),
                "SOFT_DELETE", String.valueOf(SecurityUtils.getUserId()), "逻辑删除患者档案");
        return PayloadUtils.flatten(patient);
    }

    @PreAuthorize("@ss.hasPermi('tcm:patient:remove')")
    @PatchMapping("/{id}/restore")
    public Map<String, Object> restore(@PathVariable String id)
    {
        TcmPatient patient = patientService.restoreTcmPatient(id);
        auditLogService.log("patient", patient.getId(), patient.getName(),
                "RESTORE", String.valueOf(SecurityUtils.getUserId()), "恢复患者档案");
        return PayloadUtils.flatten(patient);
    }

    @PreAuthorize("@ss.hasPermi('tcm:patient:remove')")
    @DeleteMapping("/{id}")
    public Map<String, Object> hardDelete(@PathVariable String id)
    {
        TcmPatient patient = patientService.selectTcmPatientById(id);
        patientService.hardDeleteTcmPatient(id);
        auditLogService.log("patient", id, patient != null ? patient.getName() : id,
                "DELETE", String.valueOf(SecurityUtils.getUserId()), "物理删除患者档案");
        Map<String, Object> r = new HashMap<>();
        r.put("success", true);
        return r;
    }

    @PreAuthorize("@ss.hasPermi('tcm:patient:merge')")
    @PostMapping("/{id}/merge")
    public Map<String, Object> merge(@PathVariable String id,
            @RequestBody Map<String, String> body)
    {
        String mergeId = body.get("mergeId");
        if (mergeId == null) { throw new ServiceException("缺少 mergeId"); }
        TcmPatient keepPatient = patientService.selectTcmPatientById(id);
        TcmPatient mergePatient = patientService.selectTcmPatientById(mergeId);
        patientService.mergeTcmPatients(id, mergeId);
        auditLogService.log("patient", id, keepPatient != null ? keepPatient.getName() : id,
                "MERGE", String.valueOf(SecurityUtils.getUserId()),
                "合并患者档案: " + (mergePatient != null ? mergePatient.getName() : mergeId));
        Map<String, Object> r = new HashMap<>();
        r.put("ok", true);
        return r;
    }

    @PreAuthorize("@ss.hasPermi('tcm:patient:consent')")
    @PatchMapping("/{id}/consent")
    public Map<String, Object> consent(@PathVariable String id)
    {
        TcmPatient patient = patientService.signConsent(id);
        auditLogService.log("patient", patient.getId(), patient.getName(),
                "CONSENT", String.valueOf(SecurityUtils.getUserId()), "签署知情同意书");
        return PayloadUtils.flatten(patient);
    }

    /**
     * 发送同意书签署邮件（生成令牌）
     */
    @PreAuthorize("@ss.hasPermi('tcm:patient:consent')")
    @PostMapping("/{id}/consent/send")
    public Map<String, Object> sendConsentEmail(@PathVariable String id)
    {
        String token = patientService.generateConsentToken(id);
        TcmPatient patient = patientService.selectTcmPatientById(id);
        auditLogService.log("patient", patient.getId(), patient.getName(),
                "SEND_CONSENT", String.valueOf(SecurityUtils.getUserId()), "发送知情同意书邮件");
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("patientName", patient.getName());
        result.put("email", patient.getEmail());
        return result;
    }
}
