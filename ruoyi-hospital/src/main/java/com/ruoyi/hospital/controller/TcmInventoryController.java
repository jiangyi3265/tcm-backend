package com.ruoyi.hospital.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.domain.TcmConsultationMod;
import com.ruoyi.hospital.domain.TcmInventoryItem;
import com.ruoyi.hospital.service.ITcmConsultationModService;
import com.ruoyi.hospital.service.ITcmInventoryService;
import com.ruoyi.hospital.utils.PayloadUtils;
import com.ruoyi.system.service.ISysUserService;

@RestController
@RequestMapping("/api/inventory")
public class TcmInventoryController
{
    @Autowired
    private ITcmInventoryService inventoryService;

    @Autowired
    private ITcmAuditLogService auditLogService;

    @Autowired
    private ITcmConsultationModService consultationModService;

    @Autowired
    private ISysUserService userService;

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner,pharmacist')")
    @GetMapping("")
    public List<Map<String, Object>> list()
    {
        return PayloadUtils.flattenInventory(
                inventoryService.selectTcmInventoryItemList(new TcmInventoryItem()));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping("")
    public Map<String, Object> create(@RequestBody Map<String, Object> body)
    {
        TcmInventoryItem item = PayloadUtils.toInventoryItem(body);
        inventoryService.insertTcmInventoryItem(item);
        TcmInventoryItem created = inventoryService.selectTcmInventoryItemById(item.getId());
        auditLogService.log("inventory", created.getId(), created.getName(),
                "CREATE", String.valueOf(SecurityUtils.getUserId()), "新建库存项");
        return PayloadUtils.flatten(created);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id,
            @RequestBody Map<String, Object> body)
    {
        TcmInventoryItem item = PayloadUtils.toInventoryItem(body);
        item.setId(id);
        inventoryService.updateTcmInventoryItem(item);
        TcmInventoryItem updated = inventoryService.selectTcmInventoryItemById(id);
        auditLogService.log("inventory", updated.getId(), updated.getName(),
                "UPDATE", String.valueOf(SecurityUtils.getUserId()), "更新库存项");
        return PayloadUtils.flatten(updated);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PatchMapping("/{id}/delete")
    public Map<String, Object> softDelete(@PathVariable String id)
    {
        TcmInventoryItem item = inventoryService.softDeleteTcmInventoryItem(id);
        auditLogService.log("inventory", item.getId(), item.getName(),
                "SOFT_DELETE", String.valueOf(SecurityUtils.getUserId()), "逻辑删除库存项");
        return PayloadUtils.flatten(item);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PatchMapping("/{id}/restore")
    public Map<String, Object> restore(@PathVariable String id)
    {
        TcmInventoryItem item = inventoryService.restoreTcmInventoryItem(id);
        auditLogService.log("inventory", item.getId(), item.getName(),
                "RESTORE", String.valueOf(SecurityUtils.getUserId()), "恢复库存项");
        return PayloadUtils.flatten(item);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @DeleteMapping("/{id}")
    public Map<String, Object> hardDelete(@PathVariable String id)
    {
        TcmInventoryItem item = inventoryService.selectTcmInventoryItemById(id);
        inventoryService.hardDeleteTcmInventoryItem(id);
        auditLogService.log("inventory", id, item != null ? item.getName() : id,
                "DELETE", String.valueOf(SecurityUtils.getUserId()), "物理删除库存项");
        Map<String, Object> r = new HashMap<>();
        r.put("success", true);
        return r;
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping("/{id}/adjust")
    public Map<String, Object> adjust(@PathVariable String id,
            @RequestBody Map<String, Object> body)
    {
        Object deltaObj = body.get("delta");
        if (deltaObj == null)
        {
            throw new com.ruoyi.common.exception.ServiceException("调整量(delta)不能为空");
        }
        BigDecimal delta;
        try
        {
            delta = new BigDecimal(String.valueOf(deltaObj));
        }
        catch (NumberFormatException e)
        {
            throw new com.ruoyi.common.exception.ServiceException("调整量(delta)必须为合法数字，当前值: " + deltaObj);
        }
        TcmInventoryItem item = inventoryService.adjustStock(id, delta);
        String reason = body.get("reason") != null ? String.valueOf(body.get("reason")).trim() : "";
        String details = "调整库存: " + delta.toPlainString();
        if (!reason.isEmpty())
        {
            details += "；原因: " + reason;
        }
        auditLogService.log("inventory", item.getId(), item.getName(),
                "ADJUST_STOCK", String.valueOf(SecurityUtils.getUserId()),
                details);
        return PayloadUtils.flatten(item);
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner,pharmacist')")
    @PostMapping("/deduct-prescription")
    @SuppressWarnings("unchecked")
    public Map<String, Object> deductPrescription(@RequestBody Map<String, Object> body)
    {
        List<Map<String, Object>> herbals = (List<Map<String, Object>>) body.get("herbals");
        String prescriptionType = (String) body.get("prescriptionType");
        return inventoryService.deductFromPrescription(herbals, prescriptionType);
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner,pharmacist')")
    @PostMapping("/restore-prescription")
    @SuppressWarnings("unchecked")
    public Map<String, Object> restorePrescription(@RequestBody Map<String, Object> body)
    {
        List<Map<String, Object>> herbals = (List<Map<String, Object>>) body.get("herbals");
        String prescriptionType = (String) body.get("prescriptionType");
        return inventoryService.restoreFromPrescription(herbals, prescriptionType);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping("/batch-import")
    @SuppressWarnings("unchecked")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchImport(@RequestBody Map<String, Object> body)
    {
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty())
        {
            Map<String, Object> r = new HashMap<>();
            r.put("created", 0);
            r.put("updated", 0);
            return r;
        }
        int created = 0;
        int updated = 0;
        String actorId = String.valueOf(SecurityUtils.getUserId());
        // 提前查一次全量库存，避免N+1查询
        List<TcmInventoryItem> allItems = inventoryService.selectTcmInventoryItemList(new TcmInventoryItem());
        Map<String, TcmInventoryItem> inventoryIndex = new HashMap<>();
        for (TcmInventoryItem inv : allItems)
        {
            if (inv.getIsActive() != null && inv.getIsActive() == 1
                    && (inv.getDeletedAt() == null || inv.getDeletedAt().isEmpty()))
            {
                inventoryIndex.put(buildInventoryImportKey(inv), inv);
                inventoryIndex.put(
                        buildInventoryImportKey(inv.getBranchId(), inv.getCategory(), inv.getName(), null, inv.getSupplier(), null),
                        inv);
                if (inv.getSupplierId() != null && !inv.getSupplierId().trim().isEmpty())
                {
                    inventoryIndex.put(
                            buildInventoryImportKey(inv.getBranchId(), inv.getCategory(), inv.getName(), inv.getSupplierId(), null, null),
                            inv);
                }
            }
        }
        for (Map<String, Object> item : items)
        {
            String name = item.get("name") != null ? String.valueOf(item.get("name")) : "";
            if (name.isEmpty()) continue;
            String importKey = buildInventoryImportKey(item);
            TcmInventoryItem existing = inventoryIndex.get(importKey);
            if (existing != null)
            {
                BigDecimal addQty = item.get("quantity") != null
                        ? new BigDecimal(String.valueOf(item.get("quantity")))
                        : BigDecimal.ZERO;
                BigDecimal currentQty = existing.getQuantity() != null
                        ? existing.getQuantity() : BigDecimal.ZERO;
                existing.setQuantity(currentQty.add(addQty));
                inventoryService.updateTcmInventoryItem(existing);
                updated++;
            }
            else
            {
                TcmInventoryItem newItem = PayloadUtils.toInventoryItem(item);
                inventoryService.insertTcmInventoryItem(newItem);
                inventoryIndex.put(importKey, newItem);
                created++;
            }
        }
        auditLogService.log("inventory", null, "批量导入",
                "BATCH_IMPORT", actorId, "批量导入: 新增" + created + "项，更新" + updated + "项");
        Map<String, Object> r = new HashMap<>();
        r.put("created", created);
        r.put("updated", updated);
        return r;
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner,pharmacist')")
    @GetMapping("/adjustment-history")
    public List<Map<String, Object>> adjustmentHistory(
            @RequestParam(required = false) String itemId)
    {
        TcmConsultationMod query = new TcmConsultationMod();
        query.setModType("inventory");
        if (itemId != null && !itemId.trim().isEmpty())
        {
            query.setConsultationId(itemId);
        }
        List<TcmConsultationMod> logs = consultationModService.selectTcmConsultationModList(query);
        logs.sort(Comparator.comparing(TcmConsultationMod::getModDate, Comparator.nullsLast(String::compareTo)).reversed());

        List<Map<String, Object>> result = new ArrayList<>();
        for (TcmConsultationMod log : logs)
        {
            Map<String, Object> row = new HashMap<>();
            row.put("createdAt", log.getModDate());
            row.put("action", log.getAction());
            row.put("targetId", log.getConsultationId());

            JSONObject payload = new JSONObject();
            try
            {
                JSONObject parsed = JSON.parseObject(log.getChanges());
                if (parsed != null)
                {
                    payload = parsed;
                }
            }
            catch (Exception ignored)
            {
            }

            row.put("targetName", payload.getString("targetName"));
            row.put("details", payload.getString("details"));
            row.put("userName", resolveUserName(log.getUserId()));
            result.add(row);
        }
        return result;
    }

    /**
     * Bug 7/10: 根据中药字典ID查询所有库存（多供应商）
     * 前端处方时可展示同一品种的所有供应商库存，支持智能选型
     */
    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner,pharmacist')")
    @GetMapping("/by-herb/{herbDictId}")
    public List<Map<String, Object>> listByHerbDictId(@PathVariable String herbDictId)
    {
        return PayloadUtils.flattenInventory(
                inventoryService.selectByHerbDictId(herbDictId));
    }

    private String buildInventoryImportKey(Map<String, Object> item)
    {
        return buildInventoryImportKey(
                item.get("branchId") != null ? String.valueOf(item.get("branchId")) : null,
                item.get("category") != null ? String.valueOf(item.get("category")) : null,
                item.get("name") != null ? String.valueOf(item.get("name")) : null,
                item.get("supplierId") != null ? String.valueOf(item.get("supplierId")) : null,
                item.get("supplier") != null ? String.valueOf(item.get("supplier")) : null,
                item.get("herbDictId") != null ? String.valueOf(item.get("herbDictId")) : null);
    }

    private String buildInventoryImportKey(TcmInventoryItem item)
    {
        return buildInventoryImportKey(
                item.getBranchId(),
                item.getCategory(),
                item.getName(),
                item.getSupplierId(),
                item.getSupplier(),
                item.getHerbDictId());
    }

    private String buildInventoryImportKey(
            String branchId,
            String category,
            String name,
            String supplierId,
            String supplier,
            String herbDictId)
    {
        return normalizeKey(branchId) + "::"
                + normalizeKey(category) + "::"
                + normalizeKey(name) + "::"
                + normalizeKey(supplierId) + "::"
                + normalizeKey(supplier) + "::"
                + normalizeKey(herbDictId);
    }

    private String normalizeKey(String value)
    {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String resolveUserName(String userId)
    {
        if (userId == null || userId.trim().isEmpty())
        {
            return "-";
        }
        try
        {
            SysUser user = userService.selectUserById(Long.valueOf(userId));
            return user != null ? user.getNickName() : userId;
        }
        catch (NumberFormatException e)
        {
            return userId;
        }
    }
}
