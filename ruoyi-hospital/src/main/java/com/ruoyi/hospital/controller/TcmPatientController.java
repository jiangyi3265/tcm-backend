package com.ruoyi.hospital.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.framework.config.ServerConfig;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.service.ITcmConsultationService;
import com.ruoyi.hospital.service.ITcmEmailService;
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

    @Autowired
    private ITcmEmailService emailService;

    @Autowired
    private ServerConfig serverConfig;

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner,apprentice')")
    @GetMapping("")
    public List<Map<String, Object>> list()
    {
        List<TcmPatient> patients = patientService.selectTcmPatientList(new TcmPatient());
        List<TcmConsultation> consultations = consultationService.selectTcmConsultationList(new TcmConsultation());
        return PayloadUtils.flattenPatients(PrivacyUtils.filterPatients(patients, consultations));
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner,apprentice')")
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id)
    {
        TcmPatient patient = requirePatient(id);
        ensurePatientAccessible(patient);
        return PayloadUtils.flatten(patient);
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner')")
    @PostMapping("")
    public Map<String, Object> create(@RequestBody Map<String, Object> body)
    {
        TcmPatient patient = PayloadUtils.toPatient(body);
        patientService.insertTcmPatient(patient);
        TcmPatient created = requirePatient(patient.getId());
        auditLogService.log(
                "patient",
                created.getId(),
                created.getName(),
                "CREATE",
                String.valueOf(SecurityUtils.getUserId()),
                "create patient");
        return PayloadUtils.flatten(created);
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner')")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body)
    {
        ensurePatientAccessible(requirePatient(id));
        TcmPatient patient = PayloadUtils.toPatient(body);
        patient.setId(id);
        patientService.updateTcmPatient(patient);
        TcmPatient updated = requirePatient(id);
        auditLogService.log(
                "patient",
                updated.getId(),
                updated.getName(),
                "UPDATE",
                String.valueOf(SecurityUtils.getUserId()),
                "update patient");
        return PayloadUtils.flatten(updated);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PatchMapping("/{id}/delete")
    public Map<String, Object> softDelete(@PathVariable String id)
    {
        TcmPatient patient = patientService.softDeleteTcmPatient(id);
        auditLogService.log(
                "patient",
                patient.getId(),
                patient.getName(),
                "SOFT_DELETE",
                String.valueOf(SecurityUtils.getUserId()),
                "soft delete patient");
        return PayloadUtils.flatten(patient);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PatchMapping("/{id}/restore")
    public Map<String, Object> restore(@PathVariable String id)
    {
        TcmPatient patient = patientService.restoreTcmPatient(id);
        auditLogService.log(
                "patient",
                patient.getId(),
                patient.getName(),
                "RESTORE",
                String.valueOf(SecurityUtils.getUserId()),
                "restore patient");
        return PayloadUtils.flatten(patient);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @DeleteMapping("/{id}")
    public Map<String, Object> hardDelete(@PathVariable String id)
    {
        TcmPatient patient = patientService.selectTcmPatientById(id);
        patientService.hardDeleteTcmPatient(id);
        auditLogService.log(
                "patient",
                id,
                patient != null ? patient.getName() : id,
                "DELETE",
                String.valueOf(SecurityUtils.getUserId()),
                "hard delete patient");
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner')")
    @PostMapping("/{id}/merge")
    public Map<String, Object> merge(@PathVariable String id, @RequestBody Map<String, String> body)
    {
        String mergeId = body.get("mergeId");
        if (mergeId == null)
        {
            throw new ServiceException("mergeId is required");
        }
        TcmPatient keepPatient = requirePatient(id);
        TcmPatient mergePatient = requirePatient(mergeId);
        ensurePatientAccessible(keepPatient);
        ensurePatientAccessible(mergePatient);
        patientService.mergeTcmPatients(id, mergeId);
        auditLogService.log(
                "patient",
                id,
                keepPatient.getName(),
                "MERGE",
                String.valueOf(SecurityUtils.getUserId()),
                "merge patient " + mergePatient.getName());
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner')")
    @PatchMapping("/{id}/consent")
    public Map<String, Object> consent(@PathVariable String id)
    {
        TcmPatient patient = requirePatient(id);
        ensurePatientAccessible(patient);
        TcmPatient updated = patientService.signConsent(id);
        auditLogService.log(
                "patient",
                updated.getId(),
                updated.getName(),
                "CONSENT",
                String.valueOf(SecurityUtils.getUserId()),
                "sign consent");
        return PayloadUtils.flatten(updated);
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner')")
    @PostMapping("/{id}/consent/send")
    public Map<String, Object> sendConsentEmail(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body)
    {
        TcmPatient patient = requirePatient(id);
        ensurePatientAccessible(patient);
        if (patient.getEmail() == null || patient.getEmail().trim().isEmpty())
        {
            throw new ServiceException("patient email is required");
        }

        String token = patientService.generateConsentToken(id);
        String clinicName = body != null && body.get("clinicName") != null
                ? String.valueOf(body.get("clinicName")).trim()
                : "";
        if (clinicName.isEmpty())
        {
            clinicName = "TCM Clinic";
        }

        boolean sent = emailService.sendAndLog(
                patient.getEmail(),
                clinicName + " - Consent Form Signature",
                buildConsentEmailBody(patient, clinicName, buildConsentLink(token)),
                "consent");

        auditLogService.log(
                "patient",
                patient.getId(),
                patient.getName(),
                "SEND_CONSENT",
                String.valueOf(SecurityUtils.getUserId()),
                "send consent email");

        Map<String, Object> result = new HashMap<>();
        result.put("sent", sent);
        result.put("message", sent ? "Consent email sent successfully" : "Consent email logged, but SMTP delivery failed");
        result.put("token", token);
        result.put("patientName", patient.getName());
        result.put("email", patient.getEmail());
        return result;
    }

    private TcmPatient requirePatient(String id)
    {
        TcmPatient patient = patientService.selectTcmPatientById(id);
        if (patient == null)
        {
            throw new ServiceException("patient not found");
        }
        return patient;
    }

    private void ensurePatientAccessible(TcmPatient patient)
    {
        List<TcmConsultation> consultations = consultationService.selectTcmConsultationList(new TcmConsultation());
        if (!PrivacyUtils.canAccessPatient(patient, consultations))
        {
            throw new ServiceException("access denied");
        }
    }

    private String buildConsentLink(String token)
    {
        String baseUrl = serverConfig.getUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty())
        {
            baseUrl = "/";
        }
        if (!baseUrl.endsWith("/"))
        {
            baseUrl += "/";
        }
        return baseUrl + "consent/" + token;
    }

    private String buildConsentEmailBody(TcmPatient patient, String clinicName, String consentLink)
    {
        String patientName = patient.getName() != null && !patient.getName().trim().isEmpty()
                ? patient.getName().trim()
                : "Patient";
        return "Dear " + patientName + ",\n\n"
                + "Thank you for choosing " + clinicName
                + ". Before your visit, please sign the informed consent form using the link below:\n\n"
                + consentLink + "\n\n"
                + "This link is valid for 7 days.\n\n"
                + "If you have any questions, please contact the clinic.\n\n"
                + clinicName;
    }
}
