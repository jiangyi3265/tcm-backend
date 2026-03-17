package com.ruoyi.hospital.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmConsultationMod;
import com.ruoyi.hospital.mapper.TcmConsultationMapper;
import com.ruoyi.hospital.mapper.TcmConsultationModMapper;
import com.ruoyi.hospital.service.ITcmConsultationService;
import com.ruoyi.hospital.service.ITcmInventoryService;
import com.ruoyi.hospital.service.ITcmPdfService;

/**
 * 中医问诊 Service业务层处理
 *
 * @author ruoyi
 */
@Service
public class TcmConsultationServiceImpl implements ITcmConsultationService
{
    @Autowired
    private TcmConsultationMapper consultationMapper;

    @Autowired
    private TcmConsultationModMapper modMapper;

    @Autowired
    private ITcmPdfService pdfService;

    @Autowired
    private ITcmInventoryService inventoryService;

    private static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * 查询问诊列表
     *
     * @param consultation 问诊查询条件
     * @return 问诊集合
     */
    @Override
    public List<TcmConsultation> selectTcmConsultationList(TcmConsultation consultation)
    {
        return consultationMapper.selectTcmConsultationList(consultation);
    }

    /**
     * 查询问诊详情
     *
     * @param id 问诊ID
     * @return 问诊信息
     */
    @Override
    public TcmConsultation selectTcmConsultationById(String id)
    {
        return consultationMapper.selectTcmConsultationById(id);
    }

    /**
     * 新增问诊
     *
     * @param consultation 问诊信息
     * @return 影响行数
     */
    @Override
    public int insertTcmConsultation(TcmConsultation consultation)
    {
        if (consultation.getId() == null || consultation.getId().isEmpty())
        {
            consultation.setId(java.util.UUID.randomUUID().toString());
        }
        if (consultation.getConsultationId() == null || consultation.getConsultationId().isEmpty())
        {
            consultation.setConsultationId(generateConsultationId());
        }
        consultation.setVersion(1);
        consultation.setCreateTime(DateUtils.getNowDate());
        return consultationMapper.insertTcmConsultation(consultation);
    }

    /**
     * 修改问诊
     *
     * @param consultation 问诊信息
     * @param actorId      操作者ID
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateTcmConsultation(TcmConsultation consultation, String actorId)
    {
        TcmConsultation existing = consultationMapper.selectTcmConsultationById(consultation.getId());
        if (existing == null)
        {
            throw new ServiceException("问诊记录不存在");
        }
        if (existing.getLockedAt() != null && !existing.getLockedAt().isEmpty())
        {
            TcmConsultationMod mod = new TcmConsultationMod();
            mod.setConsultationId(existing.getConsultationId());
            mod.setModDate(nowString());
            mod.setModType("edit");
            mod.setAction("Modified after lock");
            mod.setUserId(actorId);
            mod.setVersion(existing.getVersion() != null ? existing.getVersion() + 1 : 2);
            modMapper.insertTcmConsultationMod(mod);
            consultation.setVersion(existing.getVersion() != null ? existing.getVersion() + 1 : 2);
        }
        return consultationMapper.updateTcmConsultation(consultation);
    }

    /**
     * 完成问诊
     *
     * @param id      问诊ID
     * @param actorId 操作者ID
     * @return 完成后的问诊对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TcmConsultation completeConsultation(String id, String actorId)
    {
        TcmConsultation existing = consultationMapper.selectTcmConsultationById(id);
        if (existing == null)
        {
            throw new ServiceException("问诊记录不存在");
        }
        // 状态校验：只有草稿状态才能完成
        if (!"draft".equals(existing.getStatus()))
        {
            throw new ServiceException("仅草稿状态的问诊可以标记为完成，当前状态: " + existing.getStatus());
        }
        existing.setStatus("completed");
        consultationMapper.updateTcmConsultation(existing);

        // 记录完成审计日志（与 markPaid、markDispensingComplete 保持一致）
        String operationTime = nowString();
        insertConsultationMod(existing, actorId, "complete", "Consultation completed", "问诊完成", operationTime);

        return consultationMapper.selectTcmConsultationById(id);
    }

    /**
     * 标记已付款
     *
     * @param id          问诊ID
     * @param actorId     操作者ID
     * @param paymentInfo 收款信息
     * @return 标记后的问诊对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TcmConsultation markPaid(String id, String actorId, Map<String, Object> paymentInfo)
    {
        TcmConsultation existing = consultationMapper.selectTcmConsultationById(id);
        if (existing == null)
        {
            throw new ServiceException("问诊记录不存在");
        }
        if ("paid".equals(existing.getStatus()))
        {
            return existing;
        }
        if (!"completed".equals(existing.getStatus()))
        {
            throw new ServiceException("仅已完成的问诊可以收款");
        }

        JSONObject payload = parsePayload(existing.getPayload());
        String operationTime = nowString();
        Map<String, Object> safePaymentInfo = paymentInfo != null ? paymentInfo : new LinkedHashMap<>();

        existing.setStatus("paid");
        existing.setLockedAt(operationTime);
        payload.put("paidAt", operationTime);
        payload.put("paidBy", actorId);
        payload.put("paymentMethod", getString(safePaymentInfo, "paymentMethod", "manual"));
        putIfPresent(payload, "paymentReference", safePaymentInfo.get("paymentReference"));
        putIfPresent(payload, "paymentNote", safePaymentInfo.get("paymentNote"));
        existing.setPayload(payload.toJSONString());
        consultationMapper.updateTcmConsultation(existing);

        Map<String, String> invoice = pdfService.generateInvoice(id);
        putIfPresent(payload, "invoicePdfUrl", invoice.get("url"));
        putIfPresent(payload, "invoicePdfPath", invoice.get("filePath"));
        payload.put("invoiceGeneratedAt", operationTime);

        String changeSummary = buildPaymentSummary(payload);
        appendPayloadModification(payload, "payment", "收款并锁定", actorId, changeSummary, operationTime);
        existing.setPayload(payload.toJSONString());
        consultationMapper.updateTcmConsultation(existing);

        insertConsultationMod(existing, actorId, "payment", "Marked as paid", changeSummary, operationTime);
        return consultationMapper.selectTcmConsultationById(id);
    }

    /**
     * 标记配药完成
     *
     * @param id      问诊ID
     * @param actorId 操作者ID
     * @return 标记后的问诊对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TcmConsultation markDispensingComplete(String id, String actorId)
    {
        TcmConsultation existing = consultationMapper.selectTcmConsultationById(id);
        if (existing == null)
        {
            throw new ServiceException("问诊记录不存在");
        }
        if (!"paid".equals(existing.getStatus()))
        {
            throw new ServiceException("仅已收款的问诊可以发药");
        }

        JSONObject payload = parsePayload(existing.getPayload());
        if (payload.getBooleanValue("dispensingCompleted"))
        {
            return existing;
        }

        List<PrescriptionGroup> prescriptionGroups = buildPrescriptionGroups(payload);
        for (PrescriptionGroup group : prescriptionGroups)
        {
            if (group.getHerbals().isEmpty() || "none".equals(group.getPrescriptionType()))
            {
                continue;
            }
            Map<String, Object> result = inventoryService.deductFromPrescription(
                    group.getHerbals(), group.getPrescriptionType());
            if (!Boolean.TRUE.equals(result.get("success")))
            {
                List<?> errors = (List<?>) result.get("errors");
                String message = (errors != null && !errors.isEmpty())
                        ? String.join("；", stringify(errors))
                        : "库存扣减失败";
                throw new ServiceException(message);
            }
        }

        String operationTime = nowString();
        payload.put("dispensingCompleted", true);
        payload.put("dispensingCompletedAt", operationTime);
        payload.put("dispensedBy", actorId);

        String changeSummary = buildDispenseSummary(prescriptionGroups);
        appendPayloadModification(payload, "dispense", "完成发药", actorId, changeSummary, operationTime);
        existing.setPayload(payload.toJSONString());
        consultationMapper.updateTcmConsultation(existing);

        insertConsultationMod(existing, actorId, "dispense", "Dispensing completed", changeSummary, operationTime);
        return consultationMapper.selectTcmConsultationById(id);
    }

    /**
     * 软删除问诊
     *
     * @param id 问诊ID
     * @return 软删除后的问诊对象
     */
    @Override
    public TcmConsultation softDeleteTcmConsultation(String id)
    {
        TcmConsultation existing = consultationMapper.selectTcmConsultationById(id);
        if (existing == null)
        {
            throw new ServiceException("问诊记录不存在");
        }
        // 已付款或已锁定的问诊不允许删除
        if ("paid".equals(existing.getStatus()) || (existing.getLockedAt() != null && !existing.getLockedAt().isEmpty()))
        {
            throw new ServiceException("已付款或已锁定的问诊不能删除");
        }
        existing.setDeletedAt(nowString());
        consultationMapper.updateTcmConsultation(existing);
        return existing;
    }

    /**
     * 恢复已软删除的问诊
     *
     * @param id 问诊ID
     * @return 恢复后的问诊对象
     */
    @Override
    public TcmConsultation restoreTcmConsultation(String id)
    {
        TcmConsultation existing = consultationMapper.selectTcmConsultationById(id);
        if (existing == null)
        {
            throw new ServiceException("问诊记录不存在");
        }
        existing.setDeletedAt(null);
        consultationMapper.updateTcmConsultation(existing);
        return existing;
    }

    /**
     * 硬删除问诊（需删除时间超过3个月）
     *
     * @param id 问诊ID
     * @return 影响行数
     */
    @Override
    public int hardDeleteTcmConsultation(String id)
    {
        TcmConsultation existing = consultationMapper.selectTcmConsultationById(id);
        if (existing == null)
        {
            throw new ServiceException("问诊记录不存在");
        }
        if (existing.getDeletedAt() == null || existing.getDeletedAt().isEmpty())
        {
            throw new ServiceException("该记录未被软删除，无法物理删除");
        }
        try
        {
            SimpleDateFormat sdf = new SimpleDateFormat(DATETIME_FORMAT);
            Date deletedDate = sdf.parse(existing.getDeletedAt());
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
        return consultationMapper.deleteTcmConsultationById(id);
    }

    private JSONObject parsePayload(String payloadStr)
    {
        if (payloadStr != null && !payloadStr.isEmpty())
        {
            try
            {
                return JSON.parseObject(payloadStr);
            }
            catch (Exception e)
            {
                return new JSONObject();
            }
        }
        return new JSONObject();
    }

    private void insertConsultationMod(
            TcmConsultation consultation,
            String actorId,
            String modType,
            String action,
            String changes,
            String modDate)
    {
        TcmConsultationMod mod = new TcmConsultationMod();
        mod.setConsultationId(consultation.getConsultationId());
        mod.setModDate(modDate);
        mod.setModType(modType);
        mod.setAction(action);
        mod.setUserId(actorId);
        mod.setVersion(consultation.getVersion() != null ? consultation.getVersion() : 1);
        mod.setChanges(changes);
        modMapper.insertTcmConsultationMod(mod);
    }

    private void appendPayloadModification(
            JSONObject payload,
            String type,
            String action,
            String actorId,
            String changes,
            String date)
    {
        JSONArray modifications = payload.getJSONArray("modifications");
        if (modifications == null)
        {
            modifications = new JSONArray();
        }
        JSONObject modification = new JSONObject();
        modification.put("date", date);
        modification.put("type", type);
        modification.put("action", action);
        modification.put("userId", actorId);
        if (changes != null && !changes.isEmpty())
        {
            modification.put("changes", changes);
        }
        modifications.add(modification);
        payload.put("modifications", modifications);
    }

    private String buildPaymentSummary(JSONObject payload)
    {
        List<String> parts = new ArrayList<>();
        String method = payload.getString("paymentMethod");
        if (method != null && !method.isEmpty())
        {
            parts.add("支付方式: " + method);
        }
        String reference = payload.getString("paymentReference");
        if (reference != null && !reference.isEmpty())
        {
            parts.add("流水号: " + reference);
        }
        Object totalAmount = payload.get("totalAmount");
        if (totalAmount != null)
        {
            parts.add("收款金额: " + totalAmount);
        }
        String invoiceUrl = payload.getString("invoicePdfUrl");
        if (invoiceUrl != null && !invoiceUrl.isEmpty())
        {
            parts.add("已生成发票");
        }
        return String.join("；", parts);
    }

    private String buildDispenseSummary(List<PrescriptionGroup> groups)
    {
        if (groups.isEmpty())
        {
            return "无库存扣减";
        }
        List<String> parts = new ArrayList<>();
        for (PrescriptionGroup group : groups)
        {
            if (group.getHerbals().isEmpty() || "none".equals(group.getPrescriptionType()))
            {
                continue;
            }
            parts.add(group.getPrescriptionType() + " " + group.getHerbals().size() + "味");
        }
        return parts.isEmpty() ? "无库存扣减" : String.join("；", parts);
    }

    private List<PrescriptionGroup> buildPrescriptionGroups(JSONObject payload)
    {
        Map<String, Map<String, Map<String, Object>>> groups = new LinkedHashMap<>();
        JSONArray prescriptions = payload.getJSONArray("prescriptions");
        if (prescriptions != null && !prescriptions.isEmpty())
        {
            for (int i = 0; i < prescriptions.size(); i++)
            {
                JSONObject prescription = prescriptions.getJSONObject(i);
                if (prescription == null)
                {
                    continue;
                }
                String prescriptionType = getString(prescription, "prescriptionType",
                        payload.getString("prescriptionType"));
                if (prescriptionType == null || prescriptionType.isEmpty())
                {
                    prescriptionType = "raw_herbs";
                }
                if ("none".equals(prescriptionType))
                {
                    continue;
                }
                BigDecimal quantity = toBigDecimal(prescription.get("quantity"));
                if (quantity.compareTo(BigDecimal.ZERO) <= 0)
                {
                    quantity = BigDecimal.ONE;
                }
                JSONArray items = prescription.getJSONArray("items");
                mergePrescriptionItems(groups, prescriptionType, items, quantity);
            }
        }
        else
        {
            String prescriptionType = payload.getString("prescriptionType");
            if (prescriptionType == null || prescriptionType.isEmpty())
            {
                prescriptionType = "raw_herbs";
            }
            mergePrescriptionItems(groups, prescriptionType, payload.getJSONArray("herbals"), BigDecimal.ONE);
        }

        List<PrescriptionGroup> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, Map<String, Object>>> entry : groups.entrySet())
        {
            PrescriptionGroup group = new PrescriptionGroup(entry.getKey(), new ArrayList<>(entry.getValue().values()));
            result.add(group);
        }
        return result;
    }

    private void mergePrescriptionItems(
            Map<String, Map<String, Map<String, Object>>> groups,
            String prescriptionType,
            JSONArray items,
            BigDecimal quantity)
    {
        if (items == null || items.isEmpty())
        {
            return;
        }
        Map<String, Map<String, Object>> currentGroup = groups.computeIfAbsent(
                prescriptionType, key -> new LinkedHashMap<>());
        for (int i = 0; i < items.size(); i++)
        {
            JSONObject item = items.getJSONObject(i);
            if (item == null)
            {
                continue;
            }
            String name = item.getString("name");
            if (name == null || name.isEmpty())
            {
                continue;
            }
            String unit = item.getString("unit");
            String supplierId = item.getString("supplierId");
            String groupKey = name + "::" + (unit != null ? unit : "") + "::" + (supplierId != null ? supplierId : "");
            Map<String, Object> merged = currentGroup.get(groupKey);
            if (merged == null)
            {
                merged = new LinkedHashMap<>();
                merged.put("name", name);
                if (unit != null && !unit.isEmpty())
                {
                    merged.put("unit", unit);
                }
                if (supplierId != null && !supplierId.isEmpty())
                {
                    merged.put("supplierId", supplierId);
                }
                merged.put("dosage", BigDecimal.ZERO);
                currentGroup.put(groupKey, merged);
            }
            BigDecimal dosage = toBigDecimal(item.get("dosage")).multiply(quantity);
            merged.put("dosage", toBigDecimal(merged.get("dosage")).add(dosage));
        }
    }

    private void putIfPresent(JSONObject payload, String key, Object value)
    {
        if (value == null)
        {
            return;
        }
        String strValue = String.valueOf(value);
        if (!strValue.isEmpty() && !"null".equalsIgnoreCase(strValue))
        {
            payload.put(key, value);
        }
    }

    private String getString(Map<String, Object> source, String key, String defaultValue)
    {
        if (source == null)
        {
            return defaultValue;
        }
        Object value = source.get(key);
        if (value == null)
        {
            return defaultValue;
        }
        String str = String.valueOf(value);
        return str.isEmpty() ? defaultValue : str;
    }

    private BigDecimal toBigDecimal(Object value)
    {
        if (value == null)
        {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal)
        {
            return (BigDecimal) value;
        }
        if (value instanceof Number)
        {
            return new BigDecimal(String.valueOf(value));
        }
        try
        {
            return new BigDecimal(String.valueOf(value));
        }
        catch (Exception e)
        {
            return BigDecimal.ZERO;
        }
    }

    private List<String> stringify(List<?> values)
    {
        List<String> result = new ArrayList<>();
        for (Object value : values)
        {
            if (value != null)
            {
                result.add(String.valueOf(value));
            }
        }
        return result;
    }

    /**
     * 生成问诊编号
     * 格式: ORD-XXXXX-XXXXXX
     */
    private String generateConsultationId()
    {
        SecureRandom random = new SecureRandom();
        for (int attempt = 0; attempt < 10; attempt++)
        {
            String part1 = String.format("%05d", random.nextInt(100000));
            String part2 = String.format("%06d", random.nextInt(1000000));
            String consultationId = "ORD-" + part1 + "-" + part2;
            // 检查是否已存在相同编号
            TcmConsultation query = new TcmConsultation();
            query.setConsultationId(consultationId);
            List<TcmConsultation> existing = consultationMapper.selectTcmConsultationList(query);
            if (existing == null || existing.isEmpty())
            {
                return consultationId;
            }
        }
        // 兜底：使用时间戳避免冲突
        return "ORD-" + System.currentTimeMillis();
    }

    private String nowString()
    {
        return new SimpleDateFormat(DATETIME_FORMAT).format(new Date());
    }

    private static class PrescriptionGroup
    {
        private final String prescriptionType;
        private final List<Map<String, Object>> herbals;

        PrescriptionGroup(String prescriptionType, List<Map<String, Object>> herbals)
        {
            this.prescriptionType = prescriptionType;
            this.herbals = herbals;
        }

        public String getPrescriptionType()
        {
            return prescriptionType;
        }

        public List<Map<String, Object>> getHerbals()
        {
            return herbals;
        }
    }
}
