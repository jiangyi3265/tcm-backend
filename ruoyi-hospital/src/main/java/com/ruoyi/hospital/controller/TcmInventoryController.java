package com.ruoyi.hospital.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.domain.TcmInventoryItem;
import com.ruoyi.hospital.service.ITcmInventoryService;
import com.ruoyi.hospital.utils.PayloadUtils;

@RestController
@RequestMapping("/api/inventory")
public class TcmInventoryController
{
    @Autowired
    private ITcmInventoryService inventoryService;

    @Autowired
    private ITcmAuditLogService auditLogService;

    @PreAuthorize("@ss.hasPermi('tcm:inventory:list')")
    @GetMapping("")
    public List<Map<String, Object>> list()
    {
        return PayloadUtils.flattenInventory(
                inventoryService.selectTcmInventoryItemList(new TcmInventoryItem()));
    }

    @PreAuthorize("@ss.hasPermi('tcm:inventory:add')")
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

    @PreAuthorize("@ss.hasPermi('tcm:inventory:edit')")
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

    @PreAuthorize("@ss.hasPermi('tcm:inventory:remove')")
    @PatchMapping("/{id}/delete")
    public Map<String, Object> softDelete(@PathVariable String id)
    {
        TcmInventoryItem item = inventoryService.softDeleteTcmInventoryItem(id);
        auditLogService.log("inventory", item.getId(), item.getName(),
                "SOFT_DELETE", String.valueOf(SecurityUtils.getUserId()), "逻辑删除库存项");
        return PayloadUtils.flatten(item);
    }

    @PreAuthorize("@ss.hasPermi('tcm:inventory:remove')")
    @PatchMapping("/{id}/restore")
    public Map<String, Object> restore(@PathVariable String id)
    {
        TcmInventoryItem item = inventoryService.restoreTcmInventoryItem(id);
        auditLogService.log("inventory", item.getId(), item.getName(),
                "RESTORE", String.valueOf(SecurityUtils.getUserId()), "恢复库存项");
        return PayloadUtils.flatten(item);
    }

    @PreAuthorize("@ss.hasPermi('tcm:inventory:remove')")
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

    @PreAuthorize("@ss.hasPermi('tcm:inventory:adjust')")
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
        auditLogService.log("inventory", item.getId(), item.getName(),
                "ADJUST_STOCK", String.valueOf(SecurityUtils.getUserId()),
                "调整库存: " + delta.toPlainString());
        return PayloadUtils.flatten(item);
    }

    @PreAuthorize("@ss.hasPermi('tcm:inventory:deduct')")
    @PostMapping("/deduct-prescription")
    @SuppressWarnings("unchecked")
    public Map<String, Object> deductPrescription(@RequestBody Map<String, Object> body)
    {
        List<Map<String, Object>> herbals = (List<Map<String, Object>>) body.get("herbals");
        String prescriptionType = (String) body.get("prescriptionType");
        return inventoryService.deductFromPrescription(herbals, prescriptionType);
    }

    @PreAuthorize("@ss.hasPermi('tcm:inventory:restore')")
    @PostMapping("/restore-prescription")
    @SuppressWarnings("unchecked")
    public Map<String, Object> restorePrescription(@RequestBody Map<String, Object> body)
    {
        List<Map<String, Object>> herbals = (List<Map<String, Object>>) body.get("herbals");
        String prescriptionType = (String) body.get("prescriptionType");
        return inventoryService.restoreFromPrescription(herbals, prescriptionType);
    }

    @PreAuthorize("@ss.hasPermi('tcm:inventory:add')")
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
        Map<String, TcmInventoryItem> nameIndex = new HashMap<>();
        for (TcmInventoryItem inv : allItems)
        {
            if (inv.getIsActive() != null && inv.getIsActive() == 1
                    && (inv.getDeletedAt() == null || inv.getDeletedAt().isEmpty()))
            {
                nameIndex.put(inv.getName(), inv);
            }
        }
        for (Map<String, Object> item : items)
        {
            String name = item.get("name") != null ? String.valueOf(item.get("name")) : "";
            if (name.isEmpty()) continue;
            TcmInventoryItem existing = nameIndex.get(name);
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
                nameIndex.put(name, newItem);
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

    @PreAuthorize("@ss.hasPermi('tcm:inventory:list')")
    @GetMapping("/adjustment-history")
    public List<Map<String, Object>> adjustmentHistory(
            @RequestParam(required = false) String itemId)
    {
        // Adjustment history is tracked via audit logs; return empty list for now
        return new ArrayList<>();
    }
}
