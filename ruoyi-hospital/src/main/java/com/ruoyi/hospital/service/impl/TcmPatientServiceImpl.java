package com.ruoyi.hospital.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
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
import com.ruoyi.hospital.domain.TcmPatientFile;
import com.ruoyi.hospital.mapper.TcmAppointmentMapper;
import com.ruoyi.hospital.mapper.TcmConsultationMapper;
import com.ruoyi.hospital.mapper.TcmPatientMapper;
import com.ruoyi.hospital.service.ITcmPdfService;
import com.ruoyi.hospital.service.ITcmPatientFileService;
import com.ruoyi.hospital.service.ITcmPatientService;
import com.ruoyi.hospital.service.ITcmSettingsService;
import com.ruoyi.hospital.util.ConsentDocumentTemplate;

/**
 * 中医患者 Service业务层处理
 *
 * @author ruoyi
 */
@Service
public class TcmPatientServiceImpl implements ITcmPatientService
{
    private static final String INTAKE_SOURCE_PUBLIC_FORM = "public_intake_form";
    private static final String INTAKE_SOURCE_PUBLIC_BOOKING = "public_booking";

    @Autowired
    private TcmPatientMapper tcmPatientMapper;

    @Autowired
    private TcmConsultationMapper tcmConsultationMapper;

    @Autowired
    private TcmAppointmentMapper tcmAppointmentMapper;

    @Autowired
    private ITcmPdfService pdfService;

    @Autowired
    private ITcmPatientFileService patientFileService;

    @Autowired
    private ITcmSettingsService settingsService;

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
        // 只允许删除没有任何未删除问诊记录的患者。问诊应先进入自己的回收站。
        TcmConsultation consultQuery = new TcmConsultation();
        consultQuery.setPatientId(id);
        List<TcmConsultation> consultations = tcmConsultationMapper.selectTcmConsultationList(consultQuery);
        for (TcmConsultation c : consultations)
        {
            if (c.getDeletedAt() == null || c.getDeletedAt().isEmpty())
            {
                throw new ServiceException("该患者仍有问诊记录，请先删除问诊记录后再删除患者");
            }
        }
        // 只阻止未来仍未完成的预约；过去的 booked/confirmed 不再卡删除。
        TcmAppointment apptQuery = new TcmAppointment();
        apptQuery.setPatientId(id);
        List<TcmAppointment> appointments = tcmAppointmentMapper.selectTcmAppointmentList(apptQuery);
        for (TcmAppointment a : appointments)
        {
            if (isBlockingFutureAppointment(a))
            {
                throw new ServiceException("该患者有未来未完成的预约（状态: " + a.getStatus() + "），请先取消预约后再删除");
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
     * 硬删除中医患者
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
        mergePatientFields(keepPatient, mergePatient);
        tcmPatientMapper.updateTcmPatient(keepPatient);

        mergePatient.setMergedInto(keepId);
        mergePatient.setIsActive(0);
        mergePatient.setDeletedAt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        tcmPatientMapper.updateTcmPatient(mergePatient);

        // 迁移被合并患者的问诊记录到保留患者，包括已删除记录，避免孤儿问诊。
        TcmConsultation consultQuery = new TcmConsultation();
        consultQuery.setPatientId(mergeId);
        consultQuery.setDeletedAt("ANY");
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

        for (TcmPatientFile file : patientFileService.selectFilesByPatientId(mergeId))
        {
            file.setPatientId(keepId);
            patientFileService.updateTcmPatientFile(file);
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
        return signConsent(id, null, null);
    }

    @Override
    public TcmPatient signConsent(String id, String signatureName, Map<String, Object> sectionAcknowledgements)
    {
        TcmPatient patient = tcmPatientMapper.selectTcmPatientById(id);
        if (patient == null)
        {
            throw new ServiceException("患者记录不存在");
        }
        return applyConsentSignature(patient, signatureName, sectionAcknowledgements, false);
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
    public TcmPatient signConsentByToken(String token, String signatureName, Map<String, Object> sectionAcknowledgements)
    {
        TcmPatient patient = selectByConsentToken(token);
        return applyConsentSignature(patient, signatureName, sectionAcknowledgements, true);
    }

    private TcmPatient applyConsentSignature(
            TcmPatient patient,
            String signatureName,
            Map<String, Object> sectionAcknowledgements,
            boolean requireExplicitAcknowledgements)
    {
        Object consentTemplate = currentConsentTemplate();
        List<String> sectionKeys = ConsentDocumentTemplate.getSectionKeys(consentTemplate);
        JSONObject normalizedAcknowledgements = normalizeConsentAcknowledgements(
                sectionAcknowledgements,
                sectionKeys,
                requireExplicitAcknowledgements);
        patient.setConsentSigned(1);
        patient.setConsentSignedAt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        patient.setConsentToken(null);
        patient.setConsentTokenExpires(null);
        JSONObject json = parsePayload(patient.getPayload());
        if (signatureName != null && !signatureName.isEmpty())
        {
            json.put("consentSignatureName", signatureName.trim());
        }
        json.put("consentVersion", ConsentDocumentTemplate.getVersion(consentTemplate));
        json.put("consentDocumentTitle", ConsentDocumentTemplate.getTitle(consentTemplate));
        json.put("consentDocumentSections", ConsentDocumentTemplate.toResponseSections(consentTemplate));
        json.put("consentSectionAcknowledgements", normalizedAcknowledgements);
        json.put("consentSectionKeys", sectionKeys);
        json.put("consentSignedAt", patient.getConsentSignedAt());
        patient.setPayload(json.toJSONString());
        tcmPatientMapper.updateTcmPatient(patient);
        try
        {
            Map<String, String> pdf = pdfService.generateConsentForm(patient.getId(), signatureName);
            JSONObject refreshed = parsePayload(patient.getPayload());
            refreshed.put("consentPdfPath", pdf.get("filePath"));
            refreshed.put("consentPdfUrl", pdf.get("url"));
            patient.setPayload(refreshed.toJSONString());
            tcmPatientMapper.updateTcmPatient(patient);
        }
        catch (Exception ignored)
        {
            // PDF 归档失败不影响签署结果
        }
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
        JSONObject normalized = normalizeIntakeForm(formData);
        persistIntakeProfile(payload, normalized, true, INTAKE_SOURCE_PUBLIC_FORM, null);
        applyIntakeContactFields(patient, payload, normalized);
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
        JSONObject normalized = normalizeIntakeForm(formData);
        persistIntakeProfile(payload, normalized, true, INTAKE_SOURCE_PUBLIC_FORM, null);
        applyIntakeContactFields(patient, payload, normalized);
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
        JSONObject latest = extractLatestIntake(payload);
        JSONObject normalized = normalizeIntakeForm(formData);
        mergeJson(latest, normalized);
        if (latest.isEmpty())
        {
            return;
        }
        persistIntakeProfile(payload, latest, false, INTAKE_SOURCE_PUBLIC_BOOKING, appointmentId);
        applyIntakeContactFields(patient, payload, latest);
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
        String normalizedEmail = email != null ? email.trim().toLowerCase() : "";
        TcmPatient matched = null;
        for (TcmPatient patient : patients)
        {
            JSONObject payload = parsePayload(patient.getPayload());
            if (linkedUserId.equals(payload.getString("linkedUserId"))
                    || linkedUserId.equals(payload.getString("staffUserId"))
                    || linkedUserId.equals(payload.getString("userId")))
            {
                matched = patient;
                break;
            }
            JSONObject staffMeta = payload.getJSONObject("staffMeta");
            if (staffMeta != null && linkedUserId.equals(staffMeta.getString("userId")))
            {
                matched = patient;
                break;
            }
            if (!normalizedEmail.isEmpty() && hasPatientEmail(patient, payload, normalizedEmail))
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
        payload.put("staffUserId", linkedUserId);
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

    private boolean hasPatientEmail(TcmPatient patient, JSONObject payload, String normalizedEmail)
    {
        if (normalizedEmail == null || normalizedEmail.isEmpty())
        {
            return false;
        }
        if (patient != null && patient.getEmail() != null
                && normalizedEmail.equals(patient.getEmail().trim().toLowerCase()))
        {
            return true;
        }
        Object emails = payload != null ? payload.get("emails") : null;
        if (emails instanceof Collection<?>)
        {
            for (Object value : (Collection<?>) emails)
            {
                if (value != null && normalizedEmail.equals(String.valueOf(value).trim().toLowerCase()))
                {
                    return true;
                }
            }
        }
        else if (emails != null && normalizedEmail.equals(String.valueOf(emails).trim().toLowerCase()))
        {
            return true;
        }
        return false;
    }

    private boolean isBlockingFutureAppointment(TcmAppointment appointment)
    {
        if (appointment == null)
        {
            return false;
        }
        String status = appointment.getStatus();
        if (!"booked".equals(status) && !"confirmed".equals(status))
        {
            return false;
        }
        Date start = parseDateTime(appointment.getStartTime());
        return start != null && start.after(new Date());
    }

    private Date parseDateTime(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return null;
        }
        List<String> patterns = java.util.Arrays.asList(
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd");
        for (String pattern : patterns)
        {
            try
            {
                return new SimpleDateFormat(pattern).parse(value);
            }
            catch (Exception ignored)
            {
            }
        }
        return null;
    }

    private void mergePatientFields(TcmPatient keepPatient, TcmPatient mergePatient)
    {
        if (isBlank(keepPatient.getName())) keepPatient.setName(mergePatient.getName());
        if (isBlank(keepPatient.getFirstName())) keepPatient.setFirstName(mergePatient.getFirstName());
        if (isBlank(keepPatient.getLastName())) keepPatient.setLastName(mergePatient.getLastName());
        if (isBlank(keepPatient.getEmail())) keepPatient.setEmail(mergePatient.getEmail());
        if (isBlank(keepPatient.getPhone())) keepPatient.setPhone(mergePatient.getPhone());
        if (isBlank(keepPatient.getPractitionerId())) keepPatient.setPractitionerId(mergePatient.getPractitionerId());
        if ((keepPatient.getConsentSigned() == null || keepPatient.getConsentSigned() == 0)
                && mergePatient.getConsentSigned() != null && mergePatient.getConsentSigned() == 1)
        {
            keepPatient.setConsentSigned(1);
            keepPatient.setConsentSignedAt(mergePatient.getConsentSignedAt());
        }
        if (isBlank(keepPatient.getConsentToken())) keepPatient.setConsentToken(mergePatient.getConsentToken());
        if (isBlank(keepPatient.getConsentTokenExpires())) keepPatient.setConsentTokenExpires(mergePatient.getConsentTokenExpires());

        JSONObject keepPayload = parsePayload(keepPatient.getPayload());
        JSONObject mergePayload = parsePayload(mergePatient.getPayload());
        mergeMissingPayload(keepPayload, mergePayload);
        mergeEmailPayload(keepPayload, keepPatient.getEmail(), mergePatient.getEmail());
        keepPatient.setPayload(keepPayload.toJSONString());
    }

    private void mergeMissingPayload(JSONObject target, JSONObject source)
    {
        if (target == null || source == null)
        {
            return;
        }
        for (String key : source.keySet())
        {
            Object sourceValue = source.get(key);
            if (!hasMeaningfulValue(sourceValue))
            {
                continue;
            }
            Object targetValue = target.get(key);
            if (!hasMeaningfulValue(targetValue))
            {
                target.put(key, sourceValue);
            }
            else if (targetValue instanceof Collection<?> || sourceValue instanceof Collection<?>)
            {
                List<String> merged = new ArrayList<>();
                for (String item : toStringList(targetValue))
                {
                    if (!merged.contains(item)) merged.add(item);
                }
                for (String item : toStringList(sourceValue))
                {
                    if (!merged.contains(item)) merged.add(item);
                }
                if (!merged.isEmpty())
                {
                    target.put(key, merged);
                }
            }
        }
    }

    private void mergeEmailPayload(JSONObject payload, String keepEmail, String mergeEmail)
    {
        List<String> emails = new ArrayList<>();
        for (String email : toStringList(payload.get("emails")))
        {
            if (!emails.contains(email)) emails.add(email);
        }
        for (String email : java.util.Arrays.asList(keepEmail, mergeEmail))
        {
            String normalized = email != null ? email.trim() : "";
            if (!normalized.isEmpty() && !emails.contains(normalized))
            {
                emails.add(normalized);
            }
        }
        if (!emails.isEmpty())
        {
            payload.put("emails", emails);
        }
    }

    private boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
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

    private Object currentConsentTemplate()
    {
        try
        {
            Map<String, Object> settings = settingsService.getBundle();
            return settings != null ? settings.get("consentTemplate") : null;
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private JSONObject normalizeConsentAcknowledgements(
            Map<String, Object> sectionAcknowledgements,
            List<String> sectionKeys,
            boolean requireExplicitAcknowledgements)
    {
        JSONObject normalized = new JSONObject(new LinkedHashMap<>());
        List<String> keys = sectionKeys != null && !sectionKeys.isEmpty()
                ? sectionKeys
                : ConsentDocumentTemplate.getSectionKeys();
        for (String key : keys)
        {
            Object value = sectionAcknowledgements != null ? sectionAcknowledgements.get(key) : null;
            boolean agreed = toBoolean(value);
            if (!agreed && requireExplicitAcknowledgements)
            {
                throw new ServiceException("请逐段阅读并同意知情同意书后再签署");
            }
            normalized.put(key, agreed || !requireExplicitAcknowledgements);
        }
        return normalized;
    }

    private JSONObject normalizeIntakeForm(Map<String, Object> formData)
    {
        JSONObject normalized = new JSONObject(new LinkedHashMap<>());
        if (formData == null || formData.isEmpty())
        {
            return normalized;
        }

        mergeTextField(normalized, "firstName", formData.get("firstName"));
        mergeTextField(normalized, "lastName", formData.get("lastName"));
        mergeTextField(normalized, "gender", formData.get("gender"));
        mergeTextField(normalized, "dateOfBirth", firstMeaningful(formData.get("dateOfBirth"), formData.get("birthday")));
        mergeTextField(normalized, "email", formData.get("email"));
        mergeTextField(normalized, "phone", formData.get("phone"));
        mergeTextField(normalized, "addressStreet", firstMeaningful(formData.get("addressStreet"), formData.get("address")));
        mergeTextField(normalized, "addressCity", formData.get("addressCity"));
        mergeTextField(normalized, "addressState", formData.get("addressState"));
        mergeTextField(normalized, "addressCountry", formData.get("addressCountry"));
        mergeTextField(normalized, "addressPostal", formData.get("addressPostal"));
        mergeTextField(normalized, "chiefComplaint", formData.get("chiefComplaint"));
        mergeTextField(normalized, "chiefComplaintDuration", formData.get("chiefComplaintDuration"));
        mergeTextField(normalized, "chiefComplaintDescription", formData.get("chiefComplaintDescription"));
        mergeTextField(normalized, "progressOfDisease", formData.get("progressOfDisease"));
        mergeTextField(normalized, "metalImplantsLocation", formData.get("metalImplantsLocation"));
        mergeTextField(normalized, "implantType", formData.get("implantType"));
        mergeSelectionField(normalized, "medicalHistorySelections", formData.get("medicalHistorySelections"));
        mergeTextField(normalized, "otherMedicalHistory", formData.get("otherMedicalHistory"));
        mergeSelectionField(normalized, "currentMedicationSelections", formData.get("currentMedicationSelections"));
        mergeTextField(normalized, "medicationDetails", formData.get("medicationDetails"));
        mergeTextField(normalized, "drugAllergies", firstMeaningful(formData.get("drugAllergies"), formData.get("allergies")));
        mergeTextField(normalized, "otherAllergies", formData.get("otherAllergies"));
        mergeTextField(normalized, "smokingStatus", formData.get("smokingStatus"));
        mergeTextField(normalized, "alcoholStatus", formData.get("alcoholStatus"));
        mergeTextField(normalized, "exerciseStatus", formData.get("exerciseStatus"));
        mergeTextField(normalized, "familyHistory", formData.get("familyHistory"));
        mergeTextField(normalized, "lifestyleNotes", firstMeaningful(formData.get("lifestyleNotes"), formData.get("lifestyle")));
        mergeTextField(normalized, "currentlyPregnant", formData.get("currentlyPregnant"));
        mergeTextField(normalized, "breastfeeding", formData.get("breastfeeding"));
        mergeTextField(normalized, "signatureName", formData.get("signatureName"));
        mergeTextField(normalized, "signedDate", formData.get("signedDate"));
        mergeTextField(normalized, "additionalNotes", formData.get("additionalNotes"));

        String allergiesSummary = joinBlocks(
                formatLine("药物过敏 / Drug allergies", normalized.getString("drugAllergies")),
                formatLine("其他过敏 / Other allergies", normalized.getString("otherAllergies")));
        if (hasMeaningfulValue(allergiesSummary))
        {
            normalized.put("allergies", allergiesSummary);
        }

        String implantSummary = joinBlocks(
                formatLine("金属植入部位 / Implant location", normalized.getString("metalImplantsLocation")),
                formatLine("植入物类型 / Implant type", normalized.getString("implantType")));
        String medicalHistorySummary = joinBlocks(
                joinSelections(toStringList(normalized.get("medicalHistorySelections"))),
                implantSummary,
                formatLine("其他病史补充 / Additional history", normalized.getString("otherMedicalHistory")));
        if (hasMeaningfulValue(medicalHistorySummary))
        {
            normalized.put("medicalHistory", medicalHistorySummary);
            normalized.put("pastMedicalHistory", medicalHistorySummary);
        }

        String currentMedicationSummary = joinBlocks(
                joinSelections(toStringList(normalized.get("currentMedicationSelections"))),
                formatLine("药名及剂量 / Medication details", normalized.getString("medicationDetails")));
        if (hasMeaningfulValue(currentMedicationSummary))
        {
            normalized.put("currentMedications", currentMedicationSummary);
        }

        String lifestyleSummary = joinBlocks(
                formatLine("吸烟 / Smoking", normalized.getString("smokingStatus")),
                formatLine("饮酒 / Alcohol", normalized.getString("alcoholStatus")),
                formatLine("运动 / Exercise", normalized.getString("exerciseStatus")),
                formatLine("生活方式补充 / Lifestyle notes", normalized.getString("lifestyleNotes")));
        if (hasMeaningfulValue(lifestyleSummary))
        {
            normalized.put("lifestyle", lifestyleSummary);
        }

        String femaleSummary = joinBlocks(
                formatLine("是否怀孕 / Currently pregnant", normalized.getString("currentlyPregnant")),
                formatLine("是否哺乳 / Breastfeeding", normalized.getString("breastfeeding")));
        if (hasMeaningfulValue(femaleSummary))
        {
            normalized.put("femaleHealthSummary", femaleSummary);
        }

        return normalized;
    }

    private void applyIntakeContactFields(TcmPatient patient, JSONObject payload, JSONObject intakeData)
    {
        if (patient == null || payload == null || intakeData == null)
        {
            return;
        }
        String firstName = intakeData.getString("firstName");
        String lastName = intakeData.getString("lastName");
        if (hasMeaningfulValue(firstName))
        {
            patient.setFirstName(firstName.trim());
            payload.put("firstName", firstName.trim());
        }
        if (hasMeaningfulValue(lastName))
        {
            patient.setLastName(lastName.trim());
            payload.put("lastName", lastName.trim());
        }
        if (hasMeaningfulValue(firstName) || hasMeaningfulValue(lastName))
        {
            String resolvedFirst = hasMeaningfulValue(firstName) ? firstName.trim() : defaultString(patient.getFirstName());
            String resolvedLast = hasMeaningfulValue(lastName) ? lastName.trim() : defaultString(patient.getLastName());
            String name = (resolvedLast + " " + resolvedFirst).trim();
            if (!name.isEmpty())
            {
                patient.setName(name);
            }
        }
        String email = intakeData.getString("email");
        if (hasMeaningfulValue(email))
        {
            patient.setEmail(email.trim());
            payload.put("email", email.trim());
            payload.put("emails", Collections.singletonList(email.trim()));
        }
        String phone = intakeData.getString("phone");
        if (hasMeaningfulValue(phone))
        {
            patient.setPhone(phone.trim());
            payload.put("phone", phone.trim());
            payload.put("mobilePhone", phone.trim());
        }
        copyMeaningfulField(payload, intakeData, "gender");
        copyMeaningfulField(payload, intakeData, "dateOfBirth");
        copyMeaningfulField(payload, intakeData, "addressStreet");
        copyMeaningfulField(payload, intakeData, "addressCity");
        copyMeaningfulField(payload, intakeData, "addressState");
        copyMeaningfulField(payload, intakeData, "addressCountry");
        copyMeaningfulField(payload, intakeData, "addressPostal");
    }

    private String defaultString(String value)
    {
        return value != null ? value.trim() : "";
    }

    private void persistIntakeProfile(
            JSONObject payload,
            JSONObject intakeData,
            boolean completed,
            String source,
            String appointmentId)
    {
        if (payload == null || intakeData == null || intakeData.isEmpty())
        {
            return;
        }

        payload.put("latestIntakeFormData", intakeData);
        payload.put("latestIntakeSubmittedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        payload.put("latestIntakeSource", source);
        payload.put("latestIntakeCompleted", completed);
        if (appointmentId != null && !appointmentId.trim().isEmpty())
        {
            payload.put("latestIntakeAppointmentId", appointmentId.trim());
        }

        syncPatientProfileFields(payload, intakeData);
        String historyAndMedication = buildHistoryAndMedicationSummary(intakeData);
        if (hasMeaningfulValue(historyAndMedication))
        {
            payload.put("historyAndMedication", historyAndMedication);
        }
        payload.remove("intakeHistoryAndMedicationSummary");
    }

    private void syncPatientProfileFields(JSONObject payload, JSONObject intakeData)
    {
        if (payload == null || intakeData == null)
        {
            return;
        }

        copyMeaningfulField(payload, intakeData, "allergies");
        copyMeaningfulField(payload, intakeData, "drugAllergies");
        copyMeaningfulField(payload, intakeData, "otherAllergies");
        copyMeaningfulField(payload, intakeData, "medicalHistory");
        copyMeaningfulField(payload, intakeData, "pastMedicalHistory");
        copyMeaningfulField(payload, intakeData, "currentMedications");
        copyMeaningfulField(payload, intakeData, "medicationDetails");
        copyMeaningfulField(payload, intakeData, "familyHistory");
        copyMeaningfulField(payload, intakeData, "lifestyle");
        copyMeaningfulField(payload, intakeData, "lifestyleNotes");
        copyMeaningfulField(payload, intakeData, "metalImplantsLocation");
        copyMeaningfulField(payload, intakeData, "implantType");
        copyMeaningfulField(payload, intakeData, "smokingStatus");
        copyMeaningfulField(payload, intakeData, "alcoholStatus");
        copyMeaningfulField(payload, intakeData, "exerciseStatus");
        copyMeaningfulField(payload, intakeData, "currentlyPregnant");
        copyMeaningfulField(payload, intakeData, "breastfeeding");
        copyMeaningfulField(payload, intakeData, "femaleHealthSummary");
        copyMeaningfulField(payload, intakeData, "chiefComplaint");
        copyMeaningfulField(payload, intakeData, "additionalNotes");
        copyMeaningfulField(payload, intakeData, "signatureName");
        copyMeaningfulField(payload, intakeData, "signedDate");
        copyMeaningfulField(payload, intakeData, "medicalHistorySelections");
        copyMeaningfulField(payload, intakeData, "currentMedicationSelections");
    }

    private void copyMeaningfulField(JSONObject target, JSONObject source, String key)
    {
        if (target == null || source == null || key == null || key.trim().isEmpty())
        {
            return;
        }
        Object value = source.get(key);
        if (hasMeaningfulValue(value))
        {
            target.put(key, value);
        }
    }

    private JSONObject extractLatestIntake(JSONObject payload)
    {
        JSONObject latest = new JSONObject(new LinkedHashMap<>());
        if (payload == null)
        {
            return latest;
        }
        Object existingLatest = payload.get("latestIntakeFormData");
        if (existingLatest instanceof JSONObject)
        {
            latest = JSON.parseObject(((JSONObject) existingLatest).toJSONString());
        }
        else if (existingLatest != null)
        {
            try
            {
                latest = JSON.parseObject(JSON.toJSONString(existingLatest));
            }
            catch (Exception ignored)
            {
                latest = new JSONObject(new LinkedHashMap<>());
            }
        }
        return latest;
    }

    private void mergeJson(JSONObject target, JSONObject source)
    {
        if (target == null || source == null)
        {
            return;
        }
        for (String key : source.keySet())
        {
            Object value = source.get(key);
            if (hasMeaningfulValue(value))
            {
                target.put(key, value);
            }
        }
    }

    private void mergeTextField(JSONObject target, String key, Object value)
    {
        if (target == null || key == null || key.trim().isEmpty())
        {
            return;
        }
        String text = toTrimmedString(value);
        if (text != null)
        {
            target.put(key, text);
        }
    }

    private void mergeSelectionField(JSONObject target, String key, Object value)
    {
        if (target == null || key == null || key.trim().isEmpty())
        {
            return;
        }
        List<String> selected = toStringList(value);
        if (!selected.isEmpty())
        {
            target.put(key, selected);
        }
    }

    private String buildHistoryAndMedicationSummary(JSONObject intakeData)
    {
        if (intakeData == null || intakeData.isEmpty())
        {
            return "";
        }
        return joinBlocks(
                formatSection("Chief complaint / 主诉", intakeData.getString("chiefComplaint")),
                formatSection("Allergies / 过敏史", intakeData.getString("allergies")),
                formatSection("Medical history / 病史", intakeData.getString("medicalHistory")),
                formatSection("Current medications / 当前用药", intakeData.getString("currentMedications")),
                formatSection("Family history / 家族史", intakeData.getString("familyHistory")),
                trimSectionContent(intakeData.getString("lifestyle")),
                trimSectionContent(intakeData.getString("femaleHealthSummary")),
                formatSection("Additional notes / 其他补充", intakeData.getString("additionalNotes")));
    }

    private String formatSection(String title, String content)
    {
        if (content == null || content.trim().isEmpty())
        {
            return "";
        }
        return title + ":\n" + content.trim();
    }

    private String trimSectionContent(String content)
    {
        if (content == null || content.trim().isEmpty())
        {
            return "";
        }
        return content.trim();
    }

    private String formatLine(String label, String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return "";
        }
        return label + "： " + value.trim();
    }

    private String joinSelections(List<String> selections)
    {
        if (selections == null || selections.isEmpty())
        {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (String item : selections)
        {
            if (item != null && !item.trim().isEmpty())
            {
                lines.add("• " + item.trim());
            }
        }
        return joinBlocks(lines.toArray(new String[0]));
    }

    private String joinBlocks(String... parts)
    {
        if (parts == null || parts.length == 0)
        {
            return "";
        }
        List<String> blocks = new ArrayList<>();
        for (String part : parts)
        {
            if (part != null && !part.trim().isEmpty())
            {
                blocks.add(part.trim());
            }
        }
        return String.join("\n", blocks);
    }

    private List<String> toStringList(Object value)
    {
        if (value == null)
        {
            return Collections.emptyList();
        }
        Collection<?> source;
        if (value instanceof Collection<?>)
        {
            source = (Collection<?>) value;
        }
        else if (value instanceof String)
        {
            String text = ((String) value).trim();
            if (text.isEmpty())
            {
                return Collections.emptyList();
            }
            source = Collections.singletonList(text);
        }
        else
        {
            return Collections.emptyList();
        }

        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object item : source)
        {
            String text = toTrimmedString(item);
            if (text != null)
            {
                result.add(text);
            }
        }
        return new ArrayList<>(result);
    }

    private Object firstMeaningful(Object primary, Object fallback)
    {
        return hasMeaningfulValue(primary) ? primary : fallback;
    }

    private String toTrimmedString(Object value)
    {
        if (value == null)
        {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private boolean toBoolean(Object value)
    {
        if (value instanceof Boolean)
        {
            return (Boolean) value;
        }
        if (value instanceof Number)
        {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String)
        {
            String text = ((String) value).trim();
            return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
        }
        return false;
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
