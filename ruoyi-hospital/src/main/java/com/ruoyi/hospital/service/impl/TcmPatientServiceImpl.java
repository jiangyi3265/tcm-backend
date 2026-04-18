package com.ruoyi.hospital.service.impl;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.mapper.TcmAppointmentMapper;
import com.ruoyi.hospital.mapper.TcmConsultationMapper;
import com.ruoyi.hospital.mapper.TcmPatientMapper;
import com.ruoyi.hospital.service.ITcmPatientService;

/**
 * 中医患者 Service业务层处理
 *
 * @author ruoyi
 */
@Service
public class TcmPatientServiceImpl implements ITcmPatientService
{
    @Autowired
    private TcmPatientMapper tcmPatientMapper;

    @Autowired
    private TcmConsultationMapper tcmConsultationMapper;

    @Autowired
    private TcmAppointmentMapper tcmAppointmentMapper;

    /**
     * 查询中医患者列表
     *
     * @param tcmPatient 中医患者查询条件
     * @return 中医患者集合
     */
    @Override
    public List<TcmPatient> selectTcmPatientList(TcmPatient tcmPatient)
    {
        return tcmPatientMapper.selectTcmPatientList(tcmPatient);
    }

    /**
     * 查询中医患者详情
     *
     * @param id 患者ID
     * @return 中医患者
     */
    @Override
    public TcmPatient selectTcmPatientById(String id)
    {
        return tcmPatientMapper.selectTcmPatientById(id);
    }

    /**
     * 新增中医患者
     *
     * @param tcmPatient 中医患者信息
     * @return 影响行数
     */
    @Override
    public int insertTcmPatient(TcmPatient tcmPatient)
    {
        if (tcmPatient.getId() == null || tcmPatient.getId().isEmpty())
        {
            tcmPatient.setId(java.util.UUID.randomUUID().toString());
        }
        tcmPatient.setCreateTime(DateUtils.getNowDate());
        return tcmPatientMapper.insertTcmPatient(tcmPatient);
    }

    /**
     * 修改中医患者
     *
     * @param tcmPatient 中医患者信息
     * @return 影响行数
     */
    @Override
    public int updateTcmPatient(TcmPatient tcmPatient)
    {
        return tcmPatientMapper.updateTcmPatient(tcmPatient);
    }

    /**
     * 软删除中医患者
     *
     * @param id 患者ID
     * @return 软删除后的患者对象
     */
    @Override
    public TcmPatient softDeleteTcmPatient(String id)
    {
        TcmPatient patient = tcmPatientMapper.selectTcmPatientById(id);
        if (patient == null)
        {
            throw new ServiceException("患者记录不存在");
        }
        // 检查是否有未完成的问诊记录（draft/completed状态且未删除）
        TcmConsultation consultQuery = new TcmConsultation();
        consultQuery.setPatientId(id);
        List<TcmConsultation> consultations = tcmConsultationMapper.selectTcmConsultationList(consultQuery);
        for (TcmConsultation c : consultations)
        {
            if ((c.getDeletedAt() == null || c.getDeletedAt().isEmpty())
                    && ("draft".equals(c.getStatus()) || "completed".equals(c.getStatus())))
            {
                throw new ServiceException("该患者有进行中的问诊记录（状态: " + c.getStatus() + "），请先处理后再删除");
            }
        }
        // 检查是否有活跃的预约（booked/confirmed状态）
        TcmAppointment apptQuery = new TcmAppointment();
        apptQuery.setPatientId(id);
        List<TcmAppointment> appointments = tcmAppointmentMapper.selectTcmAppointmentList(apptQuery);
        for (TcmAppointment a : appointments)
        {
            if ("booked".equals(a.getStatus()) || "confirmed".equals(a.getStatus()))
            {
                throw new ServiceException("该患者有未完成的预约（状态: " + a.getStatus() + "），请先取消预约后再删除");
            }
        }
        patient.setDeletedAt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        tcmPatientMapper.updateTcmPatient(patient);
        return patient;
    }

    /**
     * 恢复已软删除的中医患者
     *
     * @param id 患者ID
     * @return 恢复后的患者对象
     */
    @Override
    public TcmPatient restoreTcmPatient(String id)
    {
        TcmPatient patient = tcmPatientMapper.selectTcmPatientById(id);
        if (patient == null)
        {
            throw new ServiceException("患者记录不存在");
        }
        patient.setDeletedAt(null);
        tcmPatientMapper.updateTcmPatient(patient);
        return patient;
    }

    /**
     * 硬删除中医患者（需删除时间超过3个月）
     *
     * @param id 患者ID
     * @return 影响行数
     */
    @Override
    public int hardDeleteTcmPatient(String id)
    {
        TcmPatient patient = tcmPatientMapper.selectTcmPatientById(id);
        if (patient == null)
        {
            throw new ServiceException("患者记录不存在");
        }
        if (patient.getDeletedAt() == null || patient.getDeletedAt().isEmpty())
        {
            throw new ServiceException("该记录未被软删除，无法物理删除");
        }
        try
        {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date deletedDate = sdf.parse(patient.getDeletedAt());
            long threeMonthsMs = 90L * 24 * 60 * 60 * 1000;
            if (System.currentTimeMillis() - deletedDate.getTime() < threeMonthsMs)
            {
                throw new ServiceException("该记录删除不满3个月，无法物理删除");
            }
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ServiceException("删除时间格式解析错误");
        }
        return tcmPatientMapper.deleteTcmPatientById(id);
    }

    /**
     * 合并两个患者记录
     *
     * @param keepId  保留的患者ID
     * @param mergeId 被合并的患者ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mergeTcmPatients(String keepId, String mergeId)
    {
        if (keepId != null && keepId.equals(mergeId))
        {
            throw new ServiceException("不能合并到自身");
        }
        TcmPatient keepPatient = tcmPatientMapper.selectTcmPatientById(keepId);
        if (keepPatient == null)
        {
            throw new ServiceException("保留的患者记录不存在");
        }
        TcmPatient mergePatient = tcmPatientMapper.selectTcmPatientById(mergeId);
        if (mergePatient == null)
        {
            throw new ServiceException("被合并的患者记录不存在");
        }
        mergePatient.setMergedInto(keepId);
        mergePatient.setIsActive(0);
        tcmPatientMapper.updateTcmPatient(mergePatient);

        // 迁移被合并患者的问诊记录到保留患者
        TcmConsultation consultQuery = new TcmConsultation();
        consultQuery.setPatientId(mergeId);
        List<TcmConsultation> mergedConsultations = tcmConsultationMapper.selectTcmConsultationList(consultQuery);
        for (TcmConsultation c : mergedConsultations)
        {
            c.setPatientId(keepId);
            tcmConsultationMapper.updateTcmConsultation(c);
        }

        // 迁移被合并患者的预约记录到保留患者
        TcmAppointment apptQuery = new TcmAppointment();
        apptQuery.setPatientId(mergeId);
        List<TcmAppointment> mergedAppointments = tcmAppointmentMapper.selectTcmAppointmentList(apptQuery);
        for (TcmAppointment a : mergedAppointments)
        {
            a.setPatientId(keepId);
            tcmAppointmentMapper.updateTcmAppointment(a);
        }
    }

    /**
     * 签署知情同意书
     *
     * @param id 患者ID
     * @return 签署后的患者对象
     */
    @Override
    public TcmPatient signConsent(String id)
    {
        TcmPatient patient = tcmPatientMapper.selectTcmPatientById(id);
        if (patient == null)
        {
            throw new ServiceException("患者记录不存在");
        }
        patient.setConsentSigned(1);
        patient.setConsentSignedAt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        patient.setConsentToken(null);
        patient.setConsentTokenExpires(null);
        tcmPatientMapper.updateTcmPatient(patient);
        return patient;
    }

    /**
     * 生成同意书签署令牌（用于邮件链接签署）
     * 令牌7天内有效
     */
    @Override
    public String generateConsentToken(String id)
    {
        TcmPatient patient = tcmPatientMapper.selectTcmPatientById(id);
        if (patient == null)
        {
            throw new ServiceException("患者记录不存在");
        }
        if (patient.getConsentSigned() != null && patient.getConsentSigned() == 1)
        {
            throw new ServiceException("该患者已签署同意书");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 7);
        String expires = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(cal.getTime());
        patient.setConsentToken(token);
        patient.setConsentTokenExpires(expires);
        tcmPatientMapper.updateTcmPatient(patient);
        return token;
    }

    /**
     * 根据令牌查找患者（公开接口，验证令牌有效性）
     */
    @Override
    public TcmPatient selectByConsentToken(String token)
    {
        if (token == null || token.isEmpty())
        {
            throw new ServiceException("无效的令牌");
        }
        TcmPatient patient = tcmPatientMapper.selectTcmPatientByConsentToken(token);
        if (patient == null)
        {
            throw new ServiceException("令牌无效或已过期");
        }
        // 检查是否过期
        if (patient.getConsentTokenExpires() != null)
        {
            try
            {
                Date expires = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(patient.getConsentTokenExpires());
                if (new Date().after(expires))
                {
                    throw new ServiceException("签署链接已过期，请联系诊所重新发送");
                }
            }
            catch (ServiceException e) { throw e; }
            catch (Exception e) { /* ignore parse error */ }
        }
        // 检查是否已签署
        if (patient.getConsentSigned() != null && patient.getConsentSigned() == 1)
        {
            throw new ServiceException("同意书已签署，无需重复操作");
        }
        return patient;
    }

    /**
     * 通过令牌签署同意书（公开接口）
     */
    @Override
    public TcmPatient signConsentByToken(String token, String signatureName)
    {
        TcmPatient patient = selectByConsentToken(token);
        patient.setConsentSigned(1);
        patient.setConsentSignedAt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        patient.setConsentToken(null);
        patient.setConsentTokenExpires(null);
        // 将签名人存入payload
        if (signatureName != null && !signatureName.isEmpty())
        {
            String payload = patient.getPayload();
            if (payload != null && !payload.isEmpty())
            {
                try
                {
                    com.alibaba.fastjson2.JSONObject json = com.alibaba.fastjson2.JSON.parseObject(payload);
                    json.put("consentSignatureName", signatureName);
                    json.put("consentSignedAt", patient.getConsentSignedAt());
                    patient.setPayload(json.toJSONString());
                }
                catch (Exception e) { /* ignore */ }
            }
        }
        tcmPatientMapper.updateTcmPatient(patient);
        return patient;
    }

    @Override
    public String generateIntakeToken(String id)
    {
        TcmPatient patient = tcmPatientMapper.selectTcmPatientById(id);
        if (patient == null)
        {
            throw new ServiceException("患者记录不存在");
        }
        JSONObject payload = parsePayload(patient.getPayload());
        String token = UUID.randomUUID().toString().replace("-", "");
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 7);
        payload.put("intakeToken", token);
        payload.put("intakeTokenExpires", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(cal.getTime()));
        patient.setPayload(payload.toJSONString());
        tcmPatientMapper.updateTcmPatient(patient);
        return token;
    }

    @Override
    public TcmPatient selectByIntakeToken(String token)
    {
        if (token == null || token.isEmpty())
        {
            throw new ServiceException("无效的令牌");
        }
        List<TcmPatient> patients = tcmPatientMapper.selectTcmPatientList(new TcmPatient());
        for (TcmPatient patient : patients)
        {
            JSONObject payload = parsePayload(patient.getPayload());
            if (!token.equals(payload.getString("intakeToken")))
            {
                continue;
            }
            String expiresAt = payload.getString("intakeTokenExpires");
            if (expiresAt != null && !expiresAt.isEmpty())
            {
                try
                {
                    Date expires = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(expiresAt);
                    if (new Date().after(expires))
                    {
                        throw new ServiceException("问诊链接已过期，请联系诊所重新发送");
                    }
                }
                catch (ServiceException e)
                {
                    throw e;
                }
                catch (Exception ignored)
                {
                }
            }
            return patient;
        }
        throw new ServiceException("令牌无效或已过期");
    }

    @Override
    public TcmPatient saveIntakeFormByToken(String token, Map<String, Object> formData)
    {
        TcmPatient patient = selectByIntakeToken(token);
        JSONObject payload = parsePayload(patient.getPayload());
        payload.put("latestIntakeFormData", JSON.toJSON(formData));
        payload.put("latestIntakeSubmittedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        payload.remove("intakeToken");
        payload.remove("intakeTokenExpires");
        patient.setPayload(payload.toJSONString());
        tcmPatientMapper.updateTcmPatient(patient);
        return patient;
    }

    @Override
    public void saveLatestIntakeForm(String patientId, Map<String, Object> formData)
    {
        if (patientId == null || patientId.isEmpty() || formData == null)
        {
            return;
        }
        TcmPatient patient = tcmPatientMapper.selectTcmPatientById(patientId);
        if (patient == null)
        {
            return;
        }
        JSONObject payload = parsePayload(patient.getPayload());
        payload.put("latestIntakeFormData", JSON.toJSON(formData));
        payload.put("latestIntakeSubmittedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        patient.setPayload(payload.toJSONString());
        tcmPatientMapper.updateTcmPatient(patient);
    }

    @Override
    public void savePublicBookingIntakeSummary(String patientId, String appointmentId, Map<String, Object> formData)
    {
        if (patientId == null || patientId.isEmpty() || formData == null || formData.isEmpty())
        {
            return;
        }
        TcmPatient patient = tcmPatientMapper.selectTcmPatientById(patientId);
        if (patient == null)
        {
            return;
        }

        JSONObject payload = parsePayload(patient.getPayload());
        JSONObject latest = new JSONObject();
        Object existingLatest = payload.get("latestIntakeFormData");
        if (existingLatest instanceof JSONObject)
        {
            latest = (JSONObject) existingLatest;
        }
        else if (existingLatest != null)
        {
            try
            {
                latest = JSON.parseObject(JSON.toJSONString(existingLatest));
            }
            catch (Exception ignored)
            {
                latest = new JSONObject();
            }
        }

        mergeIntakeField(latest, "chiefComplaint", formData.get("chiefComplaint"));
        mergeIntakeField(latest, "allergies", formData.get("allergies"));
        mergeIntakeField(latest, "currentMedications", formData.get("currentMedications"));
        mergeIntakeField(latest, "medicalHistory", formData.get("medicalHistory"));
        if (!hasMeaningfulValue(latest.get("pastMedicalHistory")))
        {
            mergeIntakeField(latest, "pastMedicalHistory", formData.get("medicalHistory"));
        }
        mergeIntakeField(latest, "additionalNotes", formData.get("additionalNotes"));

        if (latest.isEmpty())
        {
            return;
        }

        payload.put("latestIntakeFormData", latest);
        payload.put("latestIntakeSubmittedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        payload.put("latestIntakeSource", "public_booking");
        if (appointmentId != null && !appointmentId.trim().isEmpty())
        {
            payload.put("latestIntakeAppointmentId", appointmentId.trim());
        }
        patient.setPayload(payload.toJSONString());
        tcmPatientMapper.updateTcmPatient(patient);
    }

    @Override
    public void ensureStaffPatientProfile(Long userId, String name, String email, String phone)
    {
        if (userId == null || name == null || name.trim().isEmpty())
        {
            return;
        }

        TcmPatient query = new TcmPatient();
        query.setDeletedAt("ANY");
        List<TcmPatient> patients = tcmPatientMapper.selectTcmPatientList(query);

        String linkedUserId = String.valueOf(userId);
        TcmPatient matched = null;
        for (TcmPatient patient : patients)
        {
            JSONObject payload = parsePayload(patient.getPayload());
            if (linkedUserId.equals(payload.getString("linkedUserId")))
            {
                matched = patient;
                break;
            }
        }

        if (matched == null)
        {
            matched = new TcmPatient();
            matched.setId(UUID.randomUUID().toString());
            matched.setIsActive(1);
            matched.setConsentSigned(0);
        }

        matched.setName(name != null ? name.trim() : null);
        matched.setEmail(email != null ? email.trim() : null);
        matched.setPhone(phone != null ? phone.trim() : null);
        matched.setDeletedAt(null);

        JSONObject payload = parsePayload(matched.getPayload());
        payload.put("linkedUserId", linkedUserId);
        payload.put("isStaffProfile", true);
        if (email != null && !email.trim().isEmpty())
        {
            payload.put("emails", java.util.Collections.singletonList(email.trim()));
        }
        Map<String, Object> staffMeta = payload.containsKey("staffMeta")
                ? payload.getJSONObject("staffMeta")
                : new LinkedHashMap<>();
        staffMeta.put("name", name != null ? name.trim() : "");
        staffMeta.put("email", email != null ? email.trim() : "");
        staffMeta.put("phone", phone != null ? phone.trim() : "");
        payload.put("staffMeta", staffMeta);
        matched.setPayload(payload.toJSONString());

        if (tcmPatientMapper.selectTcmPatientById(matched.getId()) == null)
        {
            insertTcmPatient(matched);
        }
        else
        {
            updateTcmPatient(matched);
        }
    }

    private JSONObject parsePayload(String payload)
    {
        if (payload == null || payload.isEmpty())
        {
            return new JSONObject();
        }
        try
        {
            return JSON.parseObject(payload);
        }
        catch (Exception ignored)
        {
            return new JSONObject();
        }
    }

    private void mergeIntakeField(JSONObject target, String key, Object value)
    {
        if (target == null || key == null || key.trim().isEmpty() || !hasMeaningfulValue(value))
        {
            return;
        }
        target.put(key, value);
    }

    private boolean hasMeaningfulValue(Object value)
    {
        if (value == null)
        {
            return false;
        }
        if (value instanceof String)
        {
            return !((String) value).trim().isEmpty();
        }
        if (value instanceof java.util.Collection<?>)
        {
            return !((java.util.Collection<?>) value).isEmpty();
        }
        if (value instanceof Map<?, ?>)
        {
            return !((Map<?, ?>) value).isEmpty();
        }
        if (value instanceof JSONObject)
        {
            return !((JSONObject) value).isEmpty();
        }
        return true;
    }
}
