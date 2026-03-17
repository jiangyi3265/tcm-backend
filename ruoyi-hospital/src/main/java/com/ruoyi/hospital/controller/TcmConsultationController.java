package com.ruoyi.hospital.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.service.ITcmConsultationService;
import com.ruoyi.hospital.service.ITcmPatientService;
import com.ruoyi.hospital.service.ITcmPdfService;
import com.ruoyi.hospital.utils.PayloadUtils;
import com.ruoyi.hospital.utils.PrivacyUtils;

@RestController
@RequestMapping("/api/consultations")
public class TcmConsultationController
{
    @Autowired
    private ITcmConsultationService consultationService;

    @Autowired
    private ITcmPatientService patientService;

    @Autowired
    private ITcmPdfService pdfService;

    @PreAuthorize("@ss.hasPermi('tcm:consultation:list')")
    @GetMapping("")
    public List<Map<String, Object>> list()
    {
        List<TcmPatient> patients = patientService.selectTcmPatientList(new TcmPatient());
        List<TcmConsultation> allConsultations = consultationService.selectTcmConsultationList(new TcmConsultation());
        Set<String> accessiblePatientIds = PrivacyUtils.collectAccessiblePatientIds(patients, allConsultations);
        return PayloadUtils.flattenConsultations(
                PrivacyUtils.filterConsultations(allConsultations, accessiblePatientIds));
    }

    @PreAuthorize("@ss.hasPermi('tcm:consultation:query')")
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id)
    {
        TcmConsultation c = consultationService.selectTcmConsultationById(id);
        if (c == null) { throw new ServiceException("诊疗记录不存在"); }
        ensureConsultationAccessible(c);
        return PayloadUtils.flatten(c);
    }

    @PreAuthorize("@ss.hasPermi('tcm:consultation:add')")
    @PostMapping("")
    public Map<String, Object> create(@RequestBody Map<String, Object> body)
    {
        TcmConsultation consultation = PayloadUtils.toConsultation(body);
        consultationService.insertTcmConsultation(consultation);
        return PayloadUtils.flatten(
                consultationService.selectTcmConsultationById(consultation.getId()));
    }

    @PreAuthorize("@ss.hasPermi('tcm:consultation:edit')")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id,
            @RequestBody Map<String, Object> body)
    {
        TcmConsultation consultation = PayloadUtils.toConsultation(body);
        consultation.setId(id);
        String actorId = String.valueOf(SecurityUtils.getUserId());
        consultationService.updateTcmConsultation(consultation, actorId);
        return PayloadUtils.flatten(
                consultationService.selectTcmConsultationById(id));
    }

    @PreAuthorize("@ss.hasPermi('tcm:consultation:complete')")
    @PatchMapping("/{id}/complete")
    public Map<String, Object> complete(@PathVariable String id)
    {
        String actorId = String.valueOf(SecurityUtils.getUserId());
        return PayloadUtils.flatten(
                consultationService.completeConsultation(id, actorId));
    }

    @PreAuthorize("@ss.hasPermi('tcm:consultation:paid')")
    @PatchMapping("/{id}/paid")
    public Map<String, Object> paid(@PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body)
    {
        String actorId = String.valueOf(SecurityUtils.getUserId());
        return PayloadUtils.flatten(
                consultationService.markPaid(id, actorId, body));
    }

    @PreAuthorize("@ss.hasPermi('tcm:consultation:dispense')")
    @PatchMapping("/{id}/dispense")
    public Map<String, Object> dispense(@PathVariable String id)
    {
        String actorId = String.valueOf(SecurityUtils.getUserId());
        return PayloadUtils.flatten(
                consultationService.markDispensingComplete(id, actorId));
    }

    @PreAuthorize("@ss.hasPermi('tcm:consultation:remove')")
    @PatchMapping("/{id}/delete")
    public Map<String, Object> softDelete(@PathVariable String id)
    {
        return PayloadUtils.flatten(
                consultationService.softDeleteTcmConsultation(id));
    }

    @PreAuthorize("@ss.hasPermi('tcm:consultation:remove')")
    @PatchMapping("/{id}/restore")
    public Map<String, Object> restore(@PathVariable String id)
    {
        return PayloadUtils.flatten(
                consultationService.restoreTcmConsultation(id));
    }

    @PreAuthorize("@ss.hasPermi('tcm:consultation:remove')")
    @DeleteMapping("/{id}")
    public Map<String, Object> hardDelete(@PathVariable String id)
    {
        consultationService.hardDeleteTcmConsultation(id);
        Map<String, Object> r = new HashMap<>();
        r.put("success", true);
        return r;
    }

    @PostMapping("/{id}/pdf/report")
    @PreAuthorize("@ss.hasPermi('tcm:consultation:query')")
    public Map<String, String> generateReport(@PathVariable String id)
    {
        return pdfService.generateConsultationReport(id);
    }

    @PreAuthorize("@ss.hasAnyPermi('tcm:consultation:query,tcm:consultation:paid')")
    @PostMapping("/{id}/pdf/invoice")
    public Map<String, String> generateInvoice(@PathVariable String id)
    {
        return pdfService.generateInvoice(id);
    }

    private void ensureConsultationAccessible(TcmConsultation consultation)
    {
        List<TcmPatient> patients = patientService.selectTcmPatientList(new TcmPatient());
        List<TcmConsultation> allConsultations = consultationService.selectTcmConsultationList(new TcmConsultation());
        Set<String> accessiblePatientIds = PrivacyUtils.collectAccessiblePatientIds(patients, allConsultations);
        if (!accessiblePatientIds.contains(consultation.getPatientId()))
        {
            throw new ServiceException("无权访问该诊疗记录");
        }
    }
}
