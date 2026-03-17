package com.ruoyi.hospital.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.service.ITcmAppointmentService;
import com.ruoyi.hospital.service.ITcmConsultationService;
import com.ruoyi.hospital.service.ITcmPatientService;
import com.ruoyi.hospital.utils.PayloadUtils;
import com.ruoyi.hospital.utils.PrivacyUtils;

@RestController
@RequestMapping("/api/appointments")
public class TcmAppointmentController
{
    @Autowired
    private ITcmAppointmentService appointmentService;

    @Autowired
    private ITcmPatientService patientService;

    @Autowired
    private ITcmConsultationService consultationService;

    @Autowired
    private ITcmAuditLogService auditLogService;

    @PreAuthorize("@ss.hasPermi('tcm:appointment:list')")
    @GetMapping("")
    public List<Map<String, Object>> list()
    {
        List<TcmPatient> patients = patientService.selectTcmPatientList(new TcmPatient());
        List<TcmConsultation> consultations = consultationService.selectTcmConsultationList(new TcmConsultation());
        Set<String> accessiblePatientIds = PrivacyUtils.collectAccessiblePatientIds(patients, consultations);
        return PayloadUtils.flattenAppointments(
                PrivacyUtils.filterAppointments(
                        appointmentService.selectTcmAppointmentList(new TcmAppointment()),
                        accessiblePatientIds));
    }

    @PreAuthorize("@ss.hasPermi('tcm:appointment:add')")
    @PostMapping("")
    public Map<String, Object> create(@RequestBody Map<String, Object> body)
    {
        TcmAppointment appointment = PayloadUtils.toAppointment(body);
        appointmentService.insertTcmAppointment(appointment);
        TcmAppointment created = appointmentService.selectTcmAppointmentById(appointment.getId());
        logAppointmentAction(created, "CREATE",
                "新建预约: " + safeValue(created.getServiceType()) + " " + safeValue(created.getStartTime()));
        return PayloadUtils.flatten(created);
    }

    @PreAuthorize("@ss.hasPermi('tcm:appointment:edit')")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id,
            @RequestBody Map<String, Object> body)
    {
        TcmAppointment appointment = PayloadUtils.toAppointment(body);
        appointment.setId(id);
        appointmentService.updateTcmAppointment(appointment);
        TcmAppointment updated = appointmentService.selectTcmAppointmentById(id);
        logAppointmentAction(updated, "UPDATE",
                "更新预约: " + safeValue(updated.getServiceType()) + " " + safeValue(updated.getStartTime()));
        return PayloadUtils.flatten(updated);
    }

    @PreAuthorize("@ss.hasPermi('tcm:appointment:status')")
    @PatchMapping("/{id}/status")
    public Map<String, Object> updateStatus(@PathVariable String id,
            @RequestBody Map<String, String> body)
    {
        TcmAppointment updated = appointmentService.updateStatus(id, body.get("status"));
        logAppointmentAction(updated, "STATUS_CHANGE",
                "变更预约状态为: " + safeValue(updated.getStatus()));
        return PayloadUtils.flatten(updated);
    }

    @PreAuthorize("@ss.hasPermi('tcm:appointment:checkslot')")
    @PostMapping("/check-slot")
    @SuppressWarnings("unchecked")
    public Map<String, Object> checkSlot(@RequestBody Map<String, String> body)
    {
        Map<String, Object> result = appointmentService.checkSlot(
                body.get("practitionerId"),
                body.get("roomId"),
                body.get("startTime"),
                body.get("endTime"),
                body.get("excludeId"));

        if (Boolean.FALSE.equals(result.get("available")))
        {
            List<String> conflicts = (List<String>) result.get("conflicts");
            if (conflicts != null && !conflicts.isEmpty())
            {
                result.put("reason", String.join(", ", conflicts));
            }
            else
            {
                result.put("reason", "时间段已被占用");
            }
        }
        return result;
    }

    /**
     * 生成问诊表单链接令牌
     */
    @PreAuthorize("@ss.hasPermi('tcm:appointment:add')")
    @PostMapping("/{id}/intake-link")
    public Map<String, Object> generateIntakeLink(@PathVariable String id)
    {
        TcmAppointment appt = appointmentService.selectTcmAppointmentById(id);
        if (appt == null)
        {
            throw new ServiceException("预约不存在");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        appt.setIntakeToken(token);
        appt.setIntakeSubmitted(0);
        appointmentService.updateTcmAppointment(appt);
        logAppointmentAction(appt, "GENERATE_INTAKE_LINK", "生成问诊表单链接");
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        return result;
    }

    private void logAppointmentAction(TcmAppointment appointment, String action, String details)
    {
        if (appointment == null)
        {
            return;
        }
        auditLogService.log("appointment", appointment.getId(), resolveAppointmentTargetName(appointment),
                action, String.valueOf(SecurityUtils.getUserId()), details);
    }

    private String resolveAppointmentTargetName(TcmAppointment appointment)
    {
        String patientName = appointment.getPatientId();
        if (appointment.getPatientId() != null && !appointment.getPatientId().isEmpty())
        {
            TcmPatient patient = patientService.selectTcmPatientById(appointment.getPatientId());
            if (patient != null && patient.getName() != null && !patient.getName().isEmpty())
            {
                patientName = patient.getName();
            }
        }
        if (appointment.getStartTime() != null && !appointment.getStartTime().isEmpty())
        {
            return safeValue(patientName) + " @ " + appointment.getStartTime();
        }
        return safeValue(patientName);
    }

    private String safeValue(String value)
    {
        return value != null && !value.isEmpty() ? value : "-";
    }
}
