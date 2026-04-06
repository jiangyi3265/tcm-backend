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
import com.ruoyi.common.utils.StringUtils;
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
        List<TcmConsultation> list = consultationMapper.selectTcmConsultationList(consultation);
        List<TcmConsultation> normalized = new ArrayList<>();
        for (TcmConsultation item : list)
        {
            normalized.add(prepareConsultationView(item));
        }
        return normalized;
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
        return prepareConsultationView(consultationMapper.selectTcmConsultationById(id));
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
        JSONObject payload = normalizeConsultationPayload(consultation, parsePayload(consultation.getPayload()));
        consultation.setPayload(payload.toJSONString());
        HistorySourceContext historyContext = normalizeHistorySnapshot(consultation, null);
        int rows = consultationMapper.insertTcmConsultation(consultation);
        if (historyContext.isSourceConsultation())
        {
            propagateHistorySnapshot(consultation.getPatientId(), historyContext);
        }
        return rows;
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
        JSONObject existingPayload = normalizeConsultationPayload(existing, parsePayload(existing.getPayload()));
        JSONObject payload = normalizeConsultationPayload(consultation, parsePayload(consultation.getPayload()));
        if (hasInventoryRelevantPrescriptionChange(existingPayload, payload))
        {
            restoreReservationsFromPayload(existingPayload);
            rebuildReservationsForPayload(payload);
        }
        consultation.setPayload(payload.toJSONString());
        HistorySourceContext historyContext = normalizeHistorySnapshot(consultation, existing);
        int rows = consultationMapper.updateTcmConsultation(consultation);
        if (historyContext.isSourceConsultation())
        {
            propagateHistorySnapshot(consultation.getPatientId(), historyContext);
        }
        return rows;
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
        return recordPayment(id, actorId, paymentInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TcmConsultation syncPrescription(String id, Map<String, Object> prescriptionData, String actorId)
    {
        TcmConsultation existing = requireEditableConsultation(id);
        JSONObject payload = normalizeConsultationPayload(existing, parsePayload(existing.getPayload()));
        Map<String, Object> body = prescriptionData != null ? prescriptionData : new LinkedHashMap<>();
        Map<String, Object> incomingPrescription = extractPrescriptionPayload(body);
        if (incomingPrescription.isEmpty())
        {
            throw new ServiceException("处方内容不能为空");
        }

        List<Map<String, Object>> prescriptions = toMapList(payload.get("prescriptions"));
        String prescriptionId = getString(incomingPrescription, "id", null);
        if (prescriptionId == null || prescriptionId.isEmpty())
        {
            prescriptionId = buildPrescriptionId(existing.getId(), prescriptions.size());
        }

        int index = findPrescriptionIndex(prescriptions, prescriptionId);
        Map<String, Object> current = index >= 0 ? prescriptions.get(index) : null;
        ensurePrescriptionCanEdit(current);

        Map<String, Object> nextPrescription = buildWritablePrescription(existing, incomingPrescription, current, prescriptionId);
        List<Map<String, Object>> reservation;
        if (canReuseReservation(current, nextPrescription))
        {
            reservation = toMapList(current.get("inventoryReservation"));
        }
        else
        {
            restoreReservationIfNeeded(current);
            reservation = reservePrescription(nextPrescription);
        }
        nextPrescription.put("inventoryReservation", reservation);
        nextPrescription.put("inventorySyncedAt", nowString());
        if (index >= 0)
        {
            prescriptions.set(index, nextPrescription);
        }
        else
        {
            prescriptions.add(nextPrescription);
        }

        payload.put("prescriptions", prescriptions);
        applyTotals(payload, body);
        syncPrimaryPrescriptionFields(payload);
        normalizePaymentState(payload);
        persistConsultationPayload(existing, payload);

        String operationTime = nowString();
        String changeSummary = nextPrescription.get("formulaName") != null
                ? "同步处方：" + nextPrescription.get("formulaName")
                : "同步处方：" + prescriptionId;
        appendPayloadModification(payload, "prescription", "同步处方库存", actorId, changeSummary, operationTime);
        existing.setPayload(payload.toJSONString());
        consultationMapper.updateTcmConsultation(existing);
        insertConsultationMod(existing, actorId, "prescription", "Prescription synced", changeSummary, operationTime);
        return prepareConsultationView(consultationMapper.selectTcmConsultationById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TcmConsultation completePrescription(String id, String prescriptionId, Map<String, Object> payloadBody, String actorId)
    {
        TcmConsultation existing = requireEditableConsultation(id);
        JSONObject payload = normalizeConsultationPayload(existing, parsePayload(existing.getPayload()));
        List<Map<String, Object>> prescriptions = toMapList(payload.get("prescriptions"));
        Map<String, Object> prescription = findPrescriptionOrThrow(prescriptions, prescriptionId);
        if (isPrescriptionDeleted(prescription))
        {
            throw new ServiceException("处方已删除");
        }
        ensureReservationExists(prescription);
        prescription.put("rxStatus", "pending");
        prescription.put("dispensingCompleted", false);
        prescription.remove("dispensingCompletedAt");
        prescription.remove("dispensedBy");
        applyTotals(payload, payloadBody);
        payload.put("prescriptions", prescriptions);
        syncPrimaryPrescriptionFields(payload);
        normalizePaymentState(payload);
        persistConsultationPayload(existing, payload);

        String operationTime = nowString();
        String changeSummary = "处方进入待发：" + getString(prescription, "formulaName", prescriptionId);
        appendPayloadModification(payload, "prescription", "完成处方", actorId, changeSummary, operationTime);
        existing.setPayload(payload.toJSONString());
        consultationMapper.updateTcmConsultation(existing);
        insertConsultationMod(existing, actorId, "prescription", "Prescription completed", changeSummary, operationTime);
        return prepareConsultationView(consultationMapper.selectTcmConsultationById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TcmConsultation dispensePrescription(String id, String prescriptionId, String actorId)
    {
        TcmConsultation existing = requireEditableConsultation(id);
        JSONObject payload = normalizeConsultationPayload(existing, parsePayload(existing.getPayload()));
        List<Map<String, Object>> prescriptions = toMapList(payload.get("prescriptions"));
        Map<String, Object> prescription = findPrescriptionOrThrow(prescriptions, prescriptionId);
        String rxStatus = resolvePrescriptionStatus(prescription, existing.getStatus());
        if (!"pending".equals(rxStatus))
        {
            throw new ServiceException("仅待发处方可以发药");
        }

        String operationTime = nowString();
        prescription.put("rxStatus", "dispensed");
        prescription.put("dispensingCompleted", true);
        prescription.put("dispensingCompletedAt", operationTime);
        prescription.put("dispensedBy", actorId);
        payload.put("prescriptions", prescriptions);
        payload.put("dispensingCompleted", hasAnyDispensedPrescription(prescriptions, existing.getStatus()));
        persistConsultationPayload(existing, payload);

        String changeSummary = "处方已发：" + getString(prescription, "formulaName", prescriptionId);
        appendPayloadModification(payload, "dispense", "完成发药", actorId, changeSummary, operationTime);
        existing.setPayload(payload.toJSONString());
        consultationMapper.updateTcmConsultation(existing);
        insertConsultationMod(existing, actorId, "dispense", "Prescription dispensed", changeSummary, operationTime);
        return prepareConsultationView(consultationMapper.selectTcmConsultationById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TcmConsultation reopenPrescription(String id, String prescriptionId, String actorId)
    {
        TcmConsultation existing = requireEditableConsultation(id);
        JSONObject payload = normalizeConsultationPayload(existing, parsePayload(existing.getPayload()));
        List<Map<String, Object>> prescriptions = toMapList(payload.get("prescriptions"));
        Map<String, Object> prescription = findPrescriptionOrThrow(prescriptions, prescriptionId);
        String rxStatus = resolvePrescriptionStatus(prescription, existing.getStatus());
        if (!"dispensed".equals(rxStatus))
        {
            throw new ServiceException("仅已发处方可以回退");
        }

        prescription.put("rxStatus", "pending");
        prescription.put("dispensingCompleted", false);
        prescription.remove("dispensingCompletedAt");
        prescription.remove("dispensedBy");
        payload.put("prescriptions", prescriptions);
        payload.put("dispensingCompleted", hasAnyDispensedPrescription(prescriptions, existing.getStatus()));
        normalizePaymentState(payload);
        persistConsultationPayload(existing, payload);

        String operationTime = nowString();
        String changeSummary = "已回退处方：" + getString(prescription, "formulaName", prescriptionId);
        appendPayloadModification(payload, "prescription", "回退处方", actorId, changeSummary, operationTime);
        existing.setPayload(payload.toJSONString());
        consultationMapper.updateTcmConsultation(existing);
        insertConsultationMod(existing, actorId, "prescription", "Prescription reopened", changeSummary, operationTime);
        return prepareConsultationView(consultationMapper.selectTcmConsultationById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TcmConsultation deletePrescription(String id, String prescriptionId, Map<String, Object> payloadBody, String actorId)
    {
        TcmConsultation existing = requireEditableConsultation(id);
        JSONObject payload = normalizeConsultationPayload(existing, parsePayload(existing.getPayload()));
        List<Map<String, Object>> prescriptions = toMapList(payload.get("prescriptions"));
        Map<String, Object> prescription = findPrescriptionOrThrow(prescriptions, prescriptionId);
        String rxStatus = resolvePrescriptionStatus(prescription, existing.getStatus());
        if ("dispensed".equals(rxStatus))
        {
            throw new ServiceException("已发处方请先回退后再删除");
        }

        restoreReservationIfNeeded(prescription);
        prescription.put("deletedAt", nowString());
        prescription.put("inventoryReservation", new ArrayList<>());
        applyTotals(payload, payloadBody);
        payload.put("prescriptions", prescriptions);
        syncPrimaryPrescriptionFields(payload);
        normalizePaymentState(payload);
        persistConsultationPayload(existing, payload);

        String operationTime = nowString();
        String changeSummary = "已删除处方：" + getString(prescription, "formulaName", prescriptionId);
        appendPayloadModification(payload, "prescription", "删除处方", actorId, changeSummary, operationTime);
        existing.setPayload(payload.toJSONString());
        consultationMapper.updateTcmConsultation(existing);
        insertConsultationMod(existing, actorId, "prescription", "Prescription deleted", changeSummary, operationTime);
        return prepareConsultationView(consultationMapper.selectTcmConsultationById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TcmConsultation recordPayment(String id, String actorId, Map<String, Object> paymentInfo)
    {
        TcmConsultation existing = consultationMapper.selectTcmConsultationById(id);
        if (existing == null)
        {
            throw new ServiceException("问诊记录不存在");
        }
        if ("draft".equals(existing.getStatus()))
        {
            throw new ServiceException("草稿问诊不能收款");
        }

        JSONObject payload = normalizeConsultationPayload(existing, parsePayload(existing.getPayload()));
        Map<String, Object> safePaymentInfo = paymentInfo != null ? paymentInfo : new LinkedHashMap<>();
        BigDecimal totalAmount = toBigDecimal(payload.get("totalAmount"));
        BigDecimal paidAmount = sumPaymentRecords(payload);
        BigDecimal outstanding = totalAmount.subtract(paidAmount);
        if (outstanding.compareTo(BigDecimal.ZERO) < 0)
        {
            outstanding = BigDecimal.ZERO;
        }
        if (outstanding.compareTo(BigDecimal.ZERO) == 0)
        {
            normalizePaymentState(payload);
            existing.setPayload(payload.toJSONString());
            consultationMapper.updateTcmConsultation(existing);
            return prepareConsultationView(consultationMapper.selectTcmConsultationById(id));
        }

        String operationTime = nowString();
        JSONArray paymentRecords = payload.getJSONArray("paymentRecords");
        if (paymentRecords == null)
        {
            paymentRecords = new JSONArray();
        }
        JSONObject paymentRecord = new JSONObject();
        paymentRecord.put("id", "pay-" + System.currentTimeMillis());
        paymentRecord.put("date", operationTime);
        paymentRecord.put("amount", outstanding);
        paymentRecord.put("method", getString(safePaymentInfo, "paymentMethod", "manual"));
        putIfPresent(paymentRecord, "reference", safePaymentInfo.get("paymentReference"));
        putIfPresent(paymentRecord, "note", safePaymentInfo.get("paymentNote"));
        paymentRecord.put("actorId", actorId);
        paymentRecords.add(paymentRecord);
        payload.put("paymentRecords", paymentRecords);
        payload.put("paidAt", operationTime);
        payload.put("paidBy", actorId);
        payload.put("paymentMethod", paymentRecord.getString("method"));
        putIfPresent(payload, "paymentReference", paymentRecord.get("reference"));
        putIfPresent(payload, "paymentNote", paymentRecord.get("note"));

        Map<String, String> invoice = pdfService.generateInvoice(id);
        putIfPresent(payload, "invoicePdfUrl", invoice.get("url"));
        putIfPresent(payload, "invoicePdfPath", invoice.get("filePath"));
        payload.put("invoiceGeneratedAt", operationTime);
        normalizePaymentState(payload);
        existing.setPayload(payload.toJSONString());
        consultationMapper.updateTcmConsultation(existing);

        String changeSummary = buildPaymentSummary(payload);
        appendPayloadModification(payload, "payment", "记录付款", actorId, changeSummary, operationTime);
        existing.setPayload(payload.toJSONString());
        consultationMapper.updateTcmConsultation(existing);
        insertConsultationMod(existing, actorId, "payment", "Payment recorded", changeSummary, operationTime);
        return prepareConsultationView(consultationMapper.selectTcmConsultationById(id));
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
        return markDispensingComplete(id, actorId, false);
    }

    /**
     * 标记配药完成（可跳过库存扣减）
     * 当前主流程会在处方同步/完成时预占库存，发药时通常只做状态流转。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TcmConsultation markDispensingComplete(String id, String actorId, boolean skipDeduct)
    {
        TcmConsultation existing = consultationMapper.selectTcmConsultationById(id);
        if (existing == null)
        {
            throw new ServiceException("问诊记录不存在");
        }

        JSONObject payload = normalizeConsultationPayload(existing, parsePayload(existing.getPayload()));
        List<Map<String, Object>> prescriptions = toMapList(payload.get("prescriptions"));
        boolean updated = false;
        for (Map<String, Object> prescription : prescriptions)
        {
            if ("pending".equals(resolvePrescriptionStatus(prescription, existing.getStatus())))
            {
                prescription.put("rxStatus", "dispensed");
                prescription.put("dispensingCompleted", true);
                prescription.put("dispensingCompletedAt", nowString());
                prescription.put("dispensedBy", actorId);
                updated = true;
            }
        }
        if (!updated)
        {
            return prepareConsultationView(existing);
        }

        payload.put("prescriptions", prescriptions);
        payload.put("dispensingCompleted", true);
        normalizePaymentState(payload);
        existing.setPayload(payload.toJSONString());
        consultationMapper.updateTcmConsultation(existing);
        return prepareConsultationView(consultationMapper.selectTcmConsultationById(id));
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

    private TcmConsultation prepareConsultationView(TcmConsultation consultation)
    {
        if (consultation == null)
        {
            return null;
        }
        JSONObject payload = normalizeConsultationPayload(consultation, parsePayload(consultation.getPayload()));
        consultation.setPayload(payload.toJSONString());
        return consultation;
    }

    private TcmConsultation requireEditableConsultation(String id)
    {
        TcmConsultation consultation = consultationMapper.selectTcmConsultationById(id);
        if (consultation == null)
        {
            throw new ServiceException("问诊记录不存在");
        }
        if (consultation.getDeletedAt() != null && !consultation.getDeletedAt().isEmpty())
        {
            throw new ServiceException("问诊记录已删除");
        }
        return consultation;
    }

    private JSONObject normalizeConsultationPayload(TcmConsultation consultation, JSONObject payload)
    {
        JSONObject normalized = payload != null ? payload : new JSONObject();
        List<Map<String, Object>> prescriptions = toMapList(normalized.get("prescriptions"));
        if (prescriptions.isEmpty())
        {
            Map<String, Object> legacyPrescription = buildLegacyPrescription(consultation, normalized);
            if (!legacyPrescription.isEmpty())
            {
                prescriptions.add(legacyPrescription);
            }
        }

        List<Map<String, Object>> normalizedPrescriptions = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> prescription : prescriptions)
        {
            normalizedPrescriptions.add(normalizePrescriptionEntry(consultation, prescription, index));
            index++;
        }
        normalized.put("prescriptions", normalizedPrescriptions);
        syncPrimaryPrescriptionFields(normalized);

        if (normalized.get("paymentRecords") == null && "paid".equals(consultation.getStatus()))
        {
            JSONArray paymentRecords = new JSONArray();
            JSONObject paymentRecord = new JSONObject();
            paymentRecord.put("id", "legacy-payment-" + safeString(consultation.getId()));
            paymentRecord.put("date", firstNonBlank(
                    normalized.getString("paidAt"),
                    consultation.getLockedAt(),
                    consultation.getConsultDate(),
                    nowString()));
            paymentRecord.put("amount", toBigDecimal(normalized.get("totalAmount")));
            paymentRecord.put("method", firstNonBlank(normalized.getString("paymentMethod"), "legacy"));
            paymentRecords.add(paymentRecord);
            normalized.put("paymentRecords", paymentRecords);
        }

        normalizePaymentState(normalized);
        normalized.put("dispensingCompleted", hasAnyDispensedPrescription(normalizedPrescriptions, consultation.getStatus()));
        return normalized;
    }

    private Map<String, Object> buildLegacyPrescription(TcmConsultation consultation, JSONObject payload)
    {
        List<Map<String, Object>> herbals = toMapList(payload.get("herbals"));
        if (herbals.isEmpty())
        {
            return new LinkedHashMap<>();
        }
        String prescriptionType = payload.getString("prescriptionType");
        if (prescriptionType == null || prescriptionType.isEmpty() || "none".equals(prescriptionType))
        {
            return new LinkedHashMap<>();
        }
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("id", buildPrescriptionId(consultation.getId(), 0));
        legacy.put("formulaName", payload.getString("formulaName"));
        legacy.put("prescriptionType", prescriptionType);
        legacy.put("quantity", toBigDecimal(payload.get("quantity")).compareTo(BigDecimal.ZERO) > 0 ? payload.get("quantity") : 1);
        legacy.put("direction", payload.getString("direction"));
        legacy.put("whereToGet", payload.getString("whereToGet"));
        legacy.put("preferredUnit", payload.getString("preferredUnit"));
        legacy.put("items", herbals);
        legacy.put("subtotal", payload.get("totalRxAmount"));
        legacy.put("dispensingCompleted", payload.getBooleanValue("dispensingCompleted"));
        return legacy;
    }

    private Map<String, Object> normalizePrescriptionEntry(TcmConsultation consultation, Map<String, Object> prescription, int index)
    {
        Map<String, Object> normalized = new LinkedHashMap<>(prescription != null ? prescription : new LinkedHashMap<>());
        String prescriptionId = getString(normalized, "id", null);
        if (prescriptionId == null || prescriptionId.isEmpty())
        {
            prescriptionId = buildPrescriptionId(consultation.getId(), index);
        }
        normalized.put("id", prescriptionId);
        List<Map<String, Object>> items = toMapList(normalized.get("items"));
        normalized.put("items", items);
        String rxStatus = resolvePrescriptionStatus(normalized, consultation.getStatus());
        normalized.put("rxStatus", rxStatus);
        normalized.put("dispensingCompleted", "dispensed".equals(rxStatus));
        if ("dispensed".equals(rxStatus) && normalized.get("dispensingCompletedAt") == null)
        {
            putIfPresent(normalized, "dispensingCompletedAt", firstNonBlank(
                    getString(normalized, "dispensingCompletedAt", null),
                    consultation.getLockedAt(),
                    consultation.getConsultDate()));
        }
        if (normalized.get("inventoryReservation") == null)
        {
            normalized.put("inventoryReservation", new ArrayList<>());
        }
        return normalized;
    }

    private String buildPrescriptionId(String consultationId, int index)
    {
        return "rx-" + safeString(consultationId) + "-" + index;
    }

    private String resolvePrescriptionStatus(Map<String, Object> prescription, String consultationStatus)
    {
        String rxStatus = getString(prescription, "rxStatus", null);
        if (rxStatus != null && !rxStatus.isEmpty())
        {
            return rxStatus;
        }
        if (toBoolean(prescription.get("dispensingCompleted")))
        {
            return "dispensed";
        }
        if ("paid".equals(consultationStatus))
        {
            return "pending";
        }
        return "editing";
    }

    private Map<String, Object> extractPrescriptionPayload(Map<String, Object> body)
    {
        if (body == null)
        {
            return new LinkedHashMap<>();
        }
        Object prescription = body.get("prescription");
        if (prescription instanceof Map<?, ?>)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> mapped = (Map<String, Object>) prescription;
            return new LinkedHashMap<>(mapped);
        }
        return new LinkedHashMap<>(body);
    }

    private Map<String, Object> buildWritablePrescription(
            TcmConsultation consultation,
            Map<String, Object> incomingPrescription,
            Map<String, Object> current,
            String prescriptionId)
    {
        Map<String, Object> nextPrescription = new LinkedHashMap<>();
        if (current != null)
        {
            nextPrescription.putAll(current);
        }
        nextPrescription.putAll(incomingPrescription);
        nextPrescription.put("id", prescriptionId);
        nextPrescription.put("items", toMapList(incomingPrescription.get("items")));
        nextPrescription.put("rxStatus", "editing");
        nextPrescription.put("dispensingCompleted", false);
        nextPrescription.remove("dispensingCompletedAt");
        nextPrescription.remove("dispensedBy");
        nextPrescription.remove("deletedAt");
        if (nextPrescription.get("quantity") == null || toBigDecimal(nextPrescription.get("quantity")).compareTo(BigDecimal.ZERO) <= 0)
        {
            nextPrescription.put("quantity", BigDecimal.ONE);
        }
        if (nextPrescription.get("prescriptionType") == null || String.valueOf(nextPrescription.get("prescriptionType")).trim().isEmpty())
        {
            nextPrescription.put("prescriptionType", firstNonBlank(
                    consultation != null ? parsePayload(consultation.getPayload()).getString("prescriptionType") : null,
                    "raw_herbs"));
        }
        return nextPrescription;
    }

    private void applyTotals(JSONObject payload, Map<String, Object> body)
    {
        if (body == null)
        {
            return;
        }
        Map<String, Object> totals = null;
        Object totalsObject = body.get("totals");
        if (totalsObject instanceof Map<?, ?>)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> mapped = (Map<String, Object>) totalsObject;
            totals = mapped;
        }
        if (totals == null)
        {
            totals = body;
        }
        if (totals.containsKey("totalAmount"))
        {
            payload.put("totalAmount", totals.get("totalAmount"));
        }
        if (totals.containsKey("taxAmount"))
        {
            payload.put("taxAmount", totals.get("taxAmount"));
        }
        if (totals.containsKey("totalWithoutTax"))
        {
            payload.put("totalWithoutTax", totals.get("totalWithoutTax"));
        }
    }

    private void persistConsultationPayload(TcmConsultation consultation, JSONObject payload)
    {
        normalizePaymentState(payload);
        consultation.setPayload(payload.toJSONString());
        if ("paid".equals(consultation.getStatus()))
        {
            consultation.setStatus("completed");
        }
        consultationMapper.updateTcmConsultation(consultation);
    }

    private void syncPrimaryPrescriptionFields(JSONObject payload)
    {
        List<Map<String, Object>> prescriptions = toMapList(payload.get("prescriptions"));
        Map<String, Object> primary = null;
        for (Map<String, Object> prescription : prescriptions)
        {
            if (isPrescriptionDeleted(prescription))
            {
                continue;
            }
            primary = prescription;
            break;
        }

        if (primary == null)
        {
            payload.put("herbals", new ArrayList<>());
            payload.put("formulaName", "");
            payload.put("prescriptionType", "none");
            return;
        }

        List<Map<String, Object>> herbals = new ArrayList<>();
        for (Map<String, Object> item : toMapList(primary.get("items")))
        {
            Map<String, Object> herbal = new LinkedHashMap<>();
            herbal.put("name", item.get("name"));
            herbal.put("dosage", item.get("dosage"));
            herbal.put("unit", item.get("unit"));
            putIfPresent(herbal, "herbDictId", item.get("herbDictId"));
            putIfPresent(herbal, "inventoryId", item.get("inventoryId"));
            putIfPresent(herbal, "convertedQty", item.get("convertedQty"));
            putIfPresent(herbal, "convertedUnit", item.get("convertedUnit"));
            putIfPresent(herbal, "supplierId", item.get("supplierId"));
            putIfPresent(herbal, "supplierName", item.get("supplierName"));
            herbals.add(herbal);
        }
        payload.put("herbals", herbals);
        payload.put("formulaName", getString(primary, "formulaName", ""));
        payload.put("prescriptionType", getString(primary, "prescriptionType", "none"));
    }

    private boolean hasInventoryRelevantPrescriptionChange(JSONObject existingPayload, JSONObject nextPayload)
    {
        return !buildPrescriptionInventorySignature(existingPayload).equals(buildPrescriptionInventorySignature(nextPayload));
    }

    private String buildPrescriptionInventorySignature(JSONObject payload)
    {
        List<String> entries = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> prescription : toMapList(payload.get("prescriptions")))
        {
            entries.add(buildPrescriptionInventoryKey(payload, prescription, index));
            index++;
        }
        java.util.Collections.sort(entries);
        return JSON.toJSONString(entries);
    }

    private String buildPrescriptionInventoryKey(JSONObject payload, Map<String, Object> prescription, int index)
    {
        String prescriptionId = getString(prescription, "id", "rx-" + index);
        String prescriptionType = getString(prescription, "prescriptionType", payload.getString("prescriptionType"));
        if (prescriptionType == null || prescriptionType.isEmpty())
        {
            prescriptionType = "raw_herbs";
        }
        boolean deleted = isPrescriptionDeleted(prescription);
        List<String> items = new ArrayList<>();
        if (!deleted && !"none".equals(prescriptionType))
        {
            for (Map<String, Object> item : buildReservationSnapshot(prescription))
            {
                items.add(buildReservationSnapshotKey(item));
            }
            java.util.Collections.sort(items);
        }
        return prescriptionId + "::" + deleted + "::" + prescriptionType + "::" + JSON.toJSONString(items);
    }

    private String buildReservationSnapshotKey(Map<String, Object> item)
    {
        return safeStringValue(item.get("inventoryId"))
                + "::" + safeStringValue(item.get("name"))
                + "::" + safeStringValue(item.get("supplierId"))
                + "::" + toBigDecimal(item.get("quantity")).stripTrailingZeros().toPlainString();
    }

    private void restoreReservationsFromPayload(JSONObject payload)
    {
        for (Map<String, Object> prescription : toMapList(payload.get("prescriptions")))
        {
            restoreReservationIfNeeded(prescription);
        }
    }

    private void rebuildReservationsForPayload(JSONObject payload)
    {
        List<Map<String, Object>> prescriptions = toMapList(payload.get("prescriptions"));
        String syncedAt = nowString();
        for (Map<String, Object> prescription : prescriptions)
        {
            if (isPrescriptionDeleted(prescription))
            {
                prescription.put("inventoryReservation", new ArrayList<>());
                continue;
            }
            List<Map<String, Object>> reservation = reservePrescription(prescription);
            prescription.put("inventoryReservation", reservation);
            prescription.put("inventorySyncedAt", syncedAt);
        }
        payload.put("prescriptions", prescriptions);
        syncPrimaryPrescriptionFields(payload);
        normalizePaymentState(payload);
    }

    private int findPrescriptionIndex(List<Map<String, Object>> prescriptions, String prescriptionId)
    {
        for (int i = 0; i < prescriptions.size(); i++)
        {
            Map<String, Object> prescription = prescriptions.get(i);
            if (prescriptionId.equals(getString(prescription, "id", null)))
            {
                return i;
            }
        }
        return -1;
    }

    private Map<String, Object> findPrescriptionOrThrow(List<Map<String, Object>> prescriptions, String prescriptionId)
    {
        int index = findPrescriptionIndex(prescriptions, prescriptionId);
        if (index < 0)
        {
            throw new ServiceException("处方不存在");
        }
        return prescriptions.get(index);
    }

    private void ensurePrescriptionCanEdit(Map<String, Object> prescription)
    {
        if (prescription == null)
        {
            return;
        }
        if ("dispensed".equals(resolvePrescriptionStatus(prescription, null)))
        {
            throw new ServiceException("已发处方请先回退后再修改");
        }
    }

    private void ensureReservationExists(Map<String, Object> prescription)
    {
        List<Map<String, Object>> reservation = toMapList(prescription.get("inventoryReservation"));
        if (!reservation.isEmpty())
        {
            return;
        }
        reservation = reservePrescription(prescription);
        prescription.put("inventoryReservation", reservation);
        prescription.put("inventorySyncedAt", nowString());
    }

    private List<Map<String, Object>> reservePrescription(Map<String, Object> prescription)
    {
        String prescriptionType = getString(prescription, "prescriptionType", "raw_herbs");
        List<Map<String, Object>> reservationItems = buildReservationSnapshot(prescription);
        if (reservationItems.isEmpty() || "none".equals(prescriptionType))
        {
            return new ArrayList<>();
        }
        Map<String, Object> result = inventoryService.deductFromPrescription(reservationItems, prescriptionType);
        if (!Boolean.TRUE.equals(result.get("success")))
        {
            List<?> errors = (List<?>) result.get("errors");
            String message = (errors != null && !errors.isEmpty())
                    ? String.join("；", stringify(errors))
                    : "库存扣减失败";
            throw new ServiceException(message);
        }
        List<Map<String, Object>> deducted = toMapList(result.get("deducted"));
        List<Map<String, Object>> reservation = new ArrayList<>();
        for (Map<String, Object> item : deducted)
        {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("inventoryId", item.get("inventoryId"));
            record.put("name", item.get("name"));
            record.put("reservedQty", item.get("quantity"));
            putIfPresent(record, "supplierId", item.get("supplierId"));
            putIfPresent(record, "supplier", item.get("supplier"));
            reservation.add(record);
        }
        return reservation;
    }

    private boolean canReuseReservation(Map<String, Object> current, Map<String, Object> nextPrescription)
    {
        if (current == null)
        {
            return false;
        }
        List<Map<String, Object>> currentReservation = toMapList(current.get("inventoryReservation"));
        if (currentReservation.isEmpty())
        {
            return false;
        }
        String currentType = getString(current, "prescriptionType", "raw_herbs");
        String nextType = getString(nextPrescription, "prescriptionType", "raw_herbs");
        if (!currentType.equals(nextType))
        {
            return false;
        }
        return buildReservationSnapshot(current).equals(buildReservationSnapshot(nextPrescription));
    }

    private void restoreReservationIfNeeded(Map<String, Object> prescription)
    {
        if (prescription == null)
        {
            return;
        }
        List<Map<String, Object>> reservation = toMapList(prescription.get("inventoryReservation"));
        if (reservation.isEmpty())
        {
            return;
        }
        List<Map<String, Object>> restoreItems = new ArrayList<>();
        for (Map<String, Object> item : reservation)
        {
            Map<String, Object> restoreItem = new LinkedHashMap<>();
            restoreItem.put("inventoryId", item.get("inventoryId"));
            restoreItem.put("name", item.get("name"));
            restoreItem.put("quantity", item.get("reservedQty"));
            putIfPresent(restoreItem, "supplierId", item.get("supplierId"));
            restoreItems.add(restoreItem);
        }
        Map<String, Object> result = inventoryService.restoreFromPrescription(
                restoreItems,
                getString(prescription, "prescriptionType", "raw_herbs"));
        if (!Boolean.TRUE.equals(result.get("success")))
        {
            throw new ServiceException("库存恢复失败");
        }
    }

    private List<Map<String, Object>> buildReservationSnapshot(Map<String, Object> prescription)
    {
        List<Map<String, Object>> items = toMapList(prescription.get("items"));
        if (items.isEmpty())
        {
            return new ArrayList<>();
        }
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        BigDecimal quantity = toBigDecimal(prescription.get("quantity"));
        if (quantity.compareTo(BigDecimal.ZERO) <= 0)
        {
            quantity = BigDecimal.ONE;
        }

        for (Map<String, Object> item : items)
        {
            String name = getString(item, "name", null);
            if (name == null || name.isEmpty())
            {
                continue;
            }
            String inventoryId = getString(item, "inventoryId", null);
            String supplierId = getString(item, "supplierId", null);
            String key = safeString(inventoryId) + "::" + name + "::" + safeString(supplierId);
            Map<String, Object> mergedItem = merged.get(key);
            if (mergedItem == null)
            {
                mergedItem = new LinkedHashMap<>();
                mergedItem.put("name", name);
                putIfPresent(mergedItem, "inventoryId", inventoryId);
                putIfPresent(mergedItem, "supplierId", supplierId);
                mergedItem.put("quantity", BigDecimal.ZERO);
                merged.put(key, mergedItem);
            }

            BigDecimal requestedQty = toBigDecimal(item.get("convertedQty"));
            if (requestedQty.compareTo(BigDecimal.ZERO) <= 0)
            {
                requestedQty = toBigDecimal(item.get("dosage")).multiply(quantity);
            }
            mergedItem.put("quantity", toBigDecimal(mergedItem.get("quantity")).add(requestedQty));
        }
        return new ArrayList<>(merged.values());
    }

    private boolean hasAnyDispensedPrescription(List<Map<String, Object>> prescriptions, String consultationStatus)
    {
        for (Map<String, Object> prescription : prescriptions)
        {
            if (isPrescriptionDeleted(prescription))
            {
                continue;
            }
            if ("dispensed".equals(resolvePrescriptionStatus(prescription, consultationStatus)))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isPrescriptionDeleted(Map<String, Object> prescription)
    {
        String deletedAt = getString(prescription, "deletedAt", null);
        return deletedAt != null && !deletedAt.isEmpty();
    }

    private void normalizePaymentState(JSONObject payload)
    {
        BigDecimal totalAmount = toBigDecimal(payload.get("totalAmount"));
        BigDecimal paidAmount = sumPaymentRecords(payload);
        BigDecimal outstanding = totalAmount.subtract(paidAmount);
        if (outstanding.compareTo(BigDecimal.ZERO) < 0)
        {
            outstanding = BigDecimal.ZERO;
        }
        payload.put("paidAmount", paidAmount);
        payload.put("outstandingAmount", outstanding);
        if (paidAmount.compareTo(BigDecimal.ZERO) <= 0)
        {
            payload.put("paymentStatus", "unpaid");
        }
        else if (outstanding.compareTo(BigDecimal.ZERO) > 0)
        {
            payload.put("paymentStatus", "partial");
        }
        else
        {
            payload.put("paymentStatus", "paid");
        }
    }

    private BigDecimal sumPaymentRecords(JSONObject payload)
    {
        JSONArray paymentRecords = payload.getJSONArray("paymentRecords");
        if (paymentRecords == null || paymentRecords.isEmpty())
        {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Object record : paymentRecords)
        {
            if (record instanceof Map<?, ?>)
            {
                @SuppressWarnings("unchecked")
                Map<String, Object> paymentRecord = (Map<String, Object>) record;
                sum = sum.add(toBigDecimal(paymentRecord.get("amount")));
            }
            else if (record instanceof JSONObject)
            {
                sum = sum.add(toBigDecimal(((JSONObject) record).get("amount")));
            }
        }
        return sum;
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

    private HistorySourceContext normalizeHistorySnapshot(TcmConsultation consultation, TcmConsultation existing)
    {
        JSONObject payload = parsePayload(consultation.getPayload());
        TcmConsultation sourceConsultation = resolveSourceConsultation(
                listPatientConsultations(consultation.getPatientId()), consultation);
        boolean isSourceConsultation = sourceConsultation == null
                || consultation.getId().equals(sourceConsultation.getId());
        String snapshot;
        String sourceConsultationId;
        String sourceConsultationDate;

        if (isSourceConsultation)
        {
            snapshot = firstNonBlank(
                    payload.getString("historyAndMedicationSnapshot"),
                    payload.getString("historyAndMedication"),
                    existing != null ? extractHistorySnapshot(parsePayload(existing.getPayload())) : null);
            sourceConsultationId = consultation.getId();
            sourceConsultationDate = consultation.getConsultDate();
        }
        else
        {
            JSONObject sourcePayload = parsePayload(sourceConsultation.getPayload());
            snapshot = firstNonBlank(
                    extractHistorySnapshot(sourcePayload),
                    payload.getString("historyAndMedicationSnapshot"),
                    payload.getString("historyAndMedication"));
            sourceConsultationId = sourceConsultation.getId();
            sourceConsultationDate = sourceConsultation.getConsultDate();
        }

        writeHistorySnapshot(payload, snapshot, sourceConsultationId, sourceConsultationDate);
        consultation.setPayload(payload.toJSONString());
        return new HistorySourceContext(sourceConsultationId, sourceConsultationDate, snapshot, isSourceConsultation);
    }

    private void propagateHistorySnapshot(String patientId, HistorySourceContext historyContext)
    {
        if (patientId == null || patientId.isEmpty())
        {
            return;
        }
        for (TcmConsultation consultation : listPatientConsultations(patientId))
        {
            JSONObject payload = parsePayload(consultation.getPayload());
            writeHistorySnapshot(
                    payload,
                    historyContext.getSnapshot(),
                    historyContext.getSourceConsultationId(),
                    historyContext.getSourceConsultationDate());
            consultation.setPayload(payload.toJSONString());
            consultationMapper.updateTcmConsultation(consultation);
        }
    }

    private List<TcmConsultation> listPatientConsultations(String patientId)
    {
        if (patientId == null || patientId.isEmpty())
        {
            return new ArrayList<>();
        }
        TcmConsultation query = new TcmConsultation();
        query.setPatientId(patientId);
        return consultationMapper.selectTcmConsultationList(query);
    }

    private TcmConsultation resolveSourceConsultation(List<TcmConsultation> patientConsultations, TcmConsultation current)
    {
        List<TcmConsultation> candidates = new ArrayList<>();
        boolean replaced = false;
        for (TcmConsultation consultation : patientConsultations)
        {
            if (consultation == null)
            {
                continue;
            }
            if (current.getId() != null && current.getId().equals(consultation.getId()))
            {
                candidates.add(current);
                replaced = true;
            }
            else
            {
                candidates.add(consultation);
            }
        }
        if (!replaced)
        {
            candidates.add(current);
        }
        candidates.sort(this::compareConsultationOrder);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private int compareConsultationOrder(TcmConsultation left, TcmConsultation right)
    {
        int dateCompare = safeString(left.getConsultDate()).compareTo(safeString(right.getConsultDate()));
        if (dateCompare != 0)
        {
            return dateCompare;
        }
        long leftCreateTime = left.getCreateTime() != null ? left.getCreateTime().getTime() : 0L;
        long rightCreateTime = right.getCreateTime() != null ? right.getCreateTime().getTime() : 0L;
        int createTimeCompare = Long.compare(leftCreateTime, rightCreateTime);
        if (createTimeCompare != 0)
        {
            return createTimeCompare;
        }
        return safeString(left.getId()).compareTo(safeString(right.getId()));
    }

    private void writeHistorySnapshot(
            JSONObject payload,
            String snapshot,
            String sourceConsultationId,
            String sourceConsultationDate)
    {
        if (snapshot == null || snapshot.trim().isEmpty())
        {
            payload.remove("historyAndMedication");
            payload.remove("historyAndMedicationSnapshot");
        }
        else
        {
            payload.put("historyAndMedication", snapshot);
            payload.put("historyAndMedicationSnapshot", snapshot);
        }

        if (sourceConsultationId == null || sourceConsultationId.isEmpty())
        {
            payload.remove("historyAndMedicationSourceConsultId");
        }
        else
        {
            payload.put("historyAndMedicationSourceConsultId", sourceConsultationId);
        }

        if (sourceConsultationDate == null || sourceConsultationDate.isEmpty())
        {
            payload.remove("historyAndMedicationSourceConsultDate");
        }
        else
        {
            payload.put("historyAndMedicationSourceConsultDate", sourceConsultationDate);
        }
    }

    private String extractHistorySnapshot(JSONObject payload)
    {
        if (payload == null)
        {
            return null;
        }
        return firstNonBlank(
                payload.getString("historyAndMedicationSnapshot"),
                payload.getString("historyAndMedication"));
    }

    private String firstNonBlank(String... values)
    {
        if (values == null)
        {
            return null;
        }
        for (String value : values)
        {
            if (value != null && !value.trim().isEmpty())
            {
                return value;
            }
        }
        return null;
    }

    private String safeStringValue(Object value)
    {
        return value == null ? "" : String.valueOf(value);
    }

    private String safeString(String value)
    {
        return value != null ? value : "";
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

    private void deductInventoryOrThrow(List<PrescriptionGroup> groups)
    {
        for (PrescriptionGroup group : groups)
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
    }

    private void resetPrescriptionDispenseFlags(JSONObject payload)
    {
        List<Map<String, Object>> prescriptions = toMapList(payload.get("prescriptions"));
        if (prescriptions.isEmpty())
        {
            return;
        }
        for (Map<String, Object> prescription : prescriptions)
        {
            prescription.put("dispensingCompleted", false);
        }
        payload.put("prescriptions", prescriptions);
    }

    private boolean hasInventoryPreDeducted(JSONObject payload)
    {
        return payload != null
                && (payload.getBooleanValue("inventoryDeductedAtPayment")
                || StringUtils.isNotEmpty(payload.getString("inventoryDeductedAt")));
    }

    private List<PrescriptionGroup> buildPrescriptionGroups(JSONObject payload)
    {
        Map<String, Map<String, Map<String, Object>>> groups = new LinkedHashMap<>();
        List<Map<String, Object>> prescriptions = toMapList(payload.get("prescriptions"));
        if (!prescriptions.isEmpty())
        {
            for (Map<String, Object> prescription : prescriptions)
            {
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
                List<Map<String, Object>> items = toMapList(prescription.get("items"));
                mergePrescriptionItems(groups, prescriptionType, items, quantity);
            }
        }

        if (groups.isEmpty())
        {
            String prescriptionType = payload.getString("prescriptionType");
            if (prescriptionType == null || prescriptionType.isEmpty())
            {
                prescriptionType = "raw_herbs";
            }
            List<Map<String, Object>> herbals = toMapList(payload.get("herbals"));
            if (!herbals.isEmpty() && !"none".equals(prescriptionType))
            {
                List<PrescriptionGroup> fallback = new ArrayList<>();
                fallback.add(new PrescriptionGroup(prescriptionType, herbals));
                return fallback;
            }
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
            List<Map<String, Object>> items,
            BigDecimal quantity)
    {
        if (items == null || items.isEmpty())
        {
            return;
        }
        Map<String, Map<String, Object>> currentGroup = groups.computeIfAbsent(
                prescriptionType, key -> new LinkedHashMap<>());
        for (Map<String, Object> item : items)
        {
            String name = getString(item, "name", null);
            if (name == null || name.isEmpty())
            {
                continue;
            }
            String unit = getString(item, "unit", null);
            String supplierId = getString(item, "supplierId", null);
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toMapList(Object value)
    {
        if (value == null)
        {
            return new ArrayList<>();
        }
        if (value instanceof List<?>)
        {
            List<?> list = (List<?>) value;
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object entry : list)
            {
                if (entry instanceof Map<?, ?>)
                {
                    result.add(new LinkedHashMap<>((Map<String, Object>) entry));
                }
                else if (entry != null)
                {
                    try
                    {
                        result.add(JSON.parseObject(JSON.toJSONString(entry), Map.class));
                    }
                    catch (Exception e)
                    {
                        // ignore invalid entry
                    }
                }
            }
            return result;
        }
        if (value instanceof String && !((String) value).isEmpty())
        {
            try
            {
                return toMapList(JSON.parseArray((String) value));
            }
            catch (Exception e)
            {
                return new ArrayList<>();
            }
        }
        return new ArrayList<>();
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

    private void putIfPresent(Map<String, Object> payload, String key, Object value)
    {
        if (payload == null || value == null)
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

    private boolean toBoolean(Object value)
    {
        if (value == null)
        {
            return false;
        }
        if (value instanceof Boolean)
        {
            return (Boolean) value;
        }
        return "true".equalsIgnoreCase(String.valueOf(value))
                || "1".equals(String.valueOf(value));
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

    private static class HistorySourceContext
    {
        private final String sourceConsultationId;
        private final String sourceConsultationDate;
        private final String snapshot;
        private final boolean sourceConsultation;

        HistorySourceContext(
                String sourceConsultationId,
                String sourceConsultationDate,
                String snapshot,
                boolean sourceConsultation)
        {
            this.sourceConsultationId = sourceConsultationId;
            this.sourceConsultationDate = sourceConsultationDate;
            this.snapshot = snapshot;
            this.sourceConsultation = sourceConsultation;
        }

        String getSourceConsultationId()
        {
            return sourceConsultationId;
        }

        String getSourceConsultationDate()
        {
            return sourceConsultationDate;
        }

        String getSnapshot()
        {
            return snapshot;
        }

        boolean isSourceConsultation()
        {
            return sourceConsultation;
        }
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
