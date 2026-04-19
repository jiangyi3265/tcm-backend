package com.ruoyi.hospital.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.service.ITcmAppointmentService;
import com.ruoyi.hospital.service.ITcmAppointmentNotificationService;
import com.ruoyi.hospital.service.ITcmPatientService;

/**
 * 问诊表单公开接口（无需登录）
 */
@Anonymous
@RestController
@RequestMapping("/api/intake")
public class TcmIntakeController
{
    @Autowired
    private ITcmAppointmentService appointmentService;

    @Autowired
    private ITcmPatientService patientService;

    @Autowired
    private ITcmAppointmentNotificationService appointmentNotificationService;

    /**
     * 根据令牌获取问诊表单信息（公开接口）
     */
    @GetMapping("/{token}")
    public Map<String, Object> getIntakeInfo(@PathVariable String token)
    {
        TcmAppointment appt = appointmentService.selectTcmAppointmentByIntakeToken(token);
        if (appt != null)
        {
            TcmPatient patient = patientService.selectTcmPatientById(appt.getPatientId());

            Map<String, Object> result = new HashMap<>();
            result.put("scope", "appointment");
            result.put("appointmentId", appt.getId());
            result.put("patientName", patient != null ? patient.getName() : "");
            result.put("serviceType", appt.getServiceType());
            result.put("startTime", appt.getStartTime());
            result.put("intakeSubmitted", appt.getIntakeSubmitted() != null && appt.getIntakeSubmitted() == 1);
            return result;
        }

        TcmPatient patient = patientService.selectByIntakeToken(token);

        Map<String, Object> result = new HashMap<>();
        result.put("scope", "patient");
        result.put("patientId", patient.getId());
        result.put("patientName", patient.getName() != null ? patient.getName() : "");
        result.put("serviceType", null);
        result.put("startTime", null);
        result.put("intakeSubmitted", false);
        return result;
    }

    /**
     * 提交问诊表单（公开接口）
     */
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/{token}/submit")
    public Map<String, Object> submitIntakeForm(@PathVariable String token,
            @RequestBody Map<String, Object> formData)
    {
        TcmAppointment appt = appointmentService.selectTcmAppointmentByIntakeToken(token);
        if (appt != null)
        {
            if (appt.getIntakeSubmitted() != null && appt.getIntakeSubmitted() == 1)
            {
                throw new ServiceException("该表单已提交");
            }

            // 将表单数据存入 payload 的 intakeFormData 字段
            String payloadStr = appt.getPayload();
            JSONObject payload;
            if (payloadStr != null && !payloadStr.isEmpty())
            {
                try { payload = JSON.parseObject(payloadStr); }
                catch (Exception e) { payload = new JSONObject(); }
            }
            else
            {
                payload = new JSONObject();
            }
            payload.put("intakeFormData", formData);
            appt.setPayload(payload.toJSONString());
            appt.setIntakeSubmitted(1);
            appt.setIntakeToken(null);
            appointmentService.updateTcmAppointment(appt);
            patientService.saveLatestIntakeForm(appt.getPatientId(), formData);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("scope", "appointment");
            return result;
        }

        TcmPatient patient = patientService.saveIntakeFormByToken(token, formData);

        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("scope", "patient");
        result.put("patientId", patient.getId());
        return result;
    }

    @PostMapping("/{token}/cancel")
    public Map<String, Object> cancelAppointment(@PathVariable String token)
    {
        TcmAppointment appointment = appointmentNotificationService.cancelByIntakeToken(token, "patient_intake_form");
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("appointmentId", appointment.getId());
        result.put("status", appointment.getStatus());
        return result;
    }
}
