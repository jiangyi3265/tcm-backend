package com.ruoyi.hospital.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.service.ITcmAppointmentService;
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
    private ITcmAppointmentService appointmentService;

    @Autowired
    private ITcmAuditLogService auditLogService;

    @Autowired
    private ITcmEmailService emailService;

    @Autowired
    private ServerConfig serverConfig;

    @Value("${public.app-base-url:${PUBLIC_APP_BASE_URL:http://127.0.0.1:5173}}")
    private String publicAppBaseUrl;

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner,apprentice')")
    @GetMapping("")
    public List<Map<String, Object>> list()
    {
        List<TcmPatient> patients = patientService.selectTcmPatientList(new TcmPatient());
        List<TcmConsultation> consultations = consultationService.selectTcmConsultationList(new TcmConsultation());
        List<TcmAppointment> appointments = appointmentService.selectTcmAppointmentList(new TcmAppointment());
        return flattenPatientsForCurrentRole(PrivacyUtils.filterPatients(patients, consultations, appointments));
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner,apprentice')")
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id)
    {
        TcmPatient patient = requirePatient(id);
        ensurePatientAccessible(patient);
        return flattenPatientForCurrentRole(patient);
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
        TcmPatient existing = requirePatient(id);
        ensurePatientAccessible(existing);
        TcmPatient patient = PayloadUtils.toPatient(body);
        patient.setId(id);
        patient.setConsentToken(existing.getConsentToken());
        patient.setConsentTokenExpires(existing.getConsentTokenExpires());
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
        String appBaseUrl = resolvePublicBaseUrl(body);
        if (clinicName.isEmpty())
        {
            clinicName = "TCM Clinic";
        }

        boolean sent = emailService.sendAndLog(
                patient.getEmail(),
                clinicName + " - Consent Form Signature",
                buildConsentEmailBody(patient, clinicName, buildConsentLink(token, appBaseUrl)),
                "consent");

        auditLogService.log(
                "patient",
                patient.getId(),
                patient.getName(),
                "SEND_CONSENT",
                String.valueOf(SecurityUtils.getUserId()),
                "send consent email");

        Map<String, Object> result = new HashMap<>();
        String publicLink = buildConsentLink(token, appBaseUrl);
        result.put("sent", sent);
        result.put("message", sent ? "Consent email sent successfully" : "Consent email logged, but SMTP delivery failed");
        result.put("token", token);
        result.put("patientName", patient.getName());
        result.put("email", patient.getEmail());
        result.put("publicLink", publicLink);
        return result;
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner')")
    @PostMapping("/{id}/intake/send")
    public Map<String, Object> sendIntakeEmail(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body)
    {
        TcmPatient patient = requirePatient(id);
        ensurePatientAccessible(patient);
        if (patient.getEmail() == null || patient.getEmail().trim().isEmpty())
        {
            throw new ServiceException("patient email is required");
        }

        String token = patientService.generateIntakeToken(id);
        String clinicName = body != null && body.get("clinicName") != null
                ? String.valueOf(body.get("clinicName")).trim()
                : "";
        String appBaseUrl = resolvePublicBaseUrl(body);
        if (clinicName.isEmpty())
        {
            clinicName = "TCM Clinic";
        }

        boolean sent = emailService.sendAndLog(
                patient.getEmail(),
                clinicName + " - Intake Form",
                buildIntakeEmailBody(patient, clinicName, buildIntakeLink(token, appBaseUrl)),
                "intake");

        auditLogService.log(
                "patient",
                patient.getId(),
                patient.getName(),
                "SEND_INTAKE",
                String.valueOf(SecurityUtils.getUserId()),
                "send intake email");

        Map<String, Object> result = new HashMap<>();
        String publicLink = buildIntakeLink(token, appBaseUrl);
        result.put("sent", sent);
        result.put("message", sent ? "Intake email sent successfully" : "Intake email logged, but SMTP delivery failed");
        result.put("token", token);
        result.put("patientName", patient.getName());
        result.put("email", patient.getEmail());
        result.put("publicLink", publicLink);
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
        List<TcmAppointment> appointments = appointmentService.selectTcmAppointmentList(new TcmAppointment());
        if (!PrivacyUtils.canAccessPatient(patient, consultations, appointments))
        {
            throw new ServiceException("access denied");
        }
    }

    private List<Map<String, Object>> flattenPatientsForCurrentRole(List<TcmPatient> patients)
    {
        if (PrivacyUtils.hasRole("apprentice"))
        {
            return PayloadUtils.flattenPatientSummaries(patients);
        }
        return PayloadUtils.flattenPatients(patients);
    }

    private Map<String, Object> flattenPatientForCurrentRole(TcmPatient patient)
    {
        if (PrivacyUtils.hasRole("apprentice"))
        {
            return PayloadUtils.flattenPatientSummary(patient);
        }
        return PayloadUtils.flatten(patient);
    }

    private String buildConsentLink(String token, String appBaseUrl)
    {
        return normalizePublicBaseUrl(appBaseUrl) + "consent/" + token;
    }

    private String buildIntakeLink(String token, String appBaseUrl)
    {
        return normalizePublicBaseUrl(appBaseUrl) + "intake/" + token;
    }

    private String normalizePublicBaseUrl(String appBaseUrl)
    {
        String baseUrl = appBaseUrl;
        if (baseUrl == null || baseUrl.trim().isEmpty())
        {
            baseUrl = publicAppBaseUrl;
        }
        if (baseUrl == null || baseUrl.trim().isEmpty())
        {
            baseUrl = serverConfig.getUrl();
        }
        if (baseUrl == null || baseUrl.trim().isEmpty())
        {
            baseUrl = "/";
        }
        if (!baseUrl.endsWith("/"))
        {
            baseUrl += "/";
        }
        return baseUrl;
    }

    private String resolvePublicBaseUrl(Map<String, Object> body)
    {
        if (body != null && body.get("appBaseUrl") != null)
        {
            String appBaseUrl = decodePublicBaseUrl(String.valueOf(body.get("appBaseUrl")).trim());
            if (!appBaseUrl.isEmpty())
            {
                return appBaseUrl;
            }
        }
        return publicAppBaseUrl != null ? publicAppBaseUrl.trim() : "";
    }

    private String decodePublicBaseUrl(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return "";
        }
        try
        {
            return URLDecoder.decode(value.trim(), StandardCharsets.UTF_8.name());
        }
        catch (UnsupportedEncodingException e)
        {
            throw new ServiceException("invalid appBaseUrl encoding");
        }
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

    private String buildIntakeEmailBody(TcmPatient patient, String clinicName, String intakeLink)
    {
        String patientName = patient.getName() != null && !patient.getName().trim().isEmpty()
                ? patient.getName().trim()
                : "Patient";
        return "Dear " + patientName + ",\n\n"
                + "Thank you for choosing " + clinicName
                + ". Please complete your pre-visit intake form using the link below:\n\n"
                + intakeLink + "\n\n"
                + "This link is valid for 7 days.\n\n"
                + "If you have any questions, please contact the clinic.\n\n"
                + clinicName;
    }
}
