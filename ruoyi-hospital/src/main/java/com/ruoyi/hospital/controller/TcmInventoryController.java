package com.ruoyi.hospital.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.domain.TcmConsultationMod;
import com.ruoyi.hospital.domain.TcmHerbDict;
import com.ruoyi.hospital.domain.TcmInventoryItem;
import com.ruoyi.hospital.service.ITcmConsultationModService;
import com.ruoyi.hospital.service.ITcmHerbDictService;
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

    @Autowired
    private ITcmHerbDictService herbDictService;

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner,pharmacist')")
    @GetMapping("")
    public List<Map<String, Object>> list(
            @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted)
    {
        List<TcmInventoryItem> items = includeDeleted
                ? inventoryService.selectTcmInventoryItemListIncludingDeleted(new TcmInventoryItem())
                : inventoryService.selectTcmInventoryItemList(new TcmInventoryItem());
        return withLast30DaysUsage(items);
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
        return withLast30DaysUsage(Collections.singletonList(created)).get(0);
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
        return withLast30DaysUsage(Collections.singletonList(updated)).get(0);
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
        List<Map<String, Object>> herbals = requireHerbals(body.get("herbals"));
        String prescriptionType = requirePrescriptionType(body.get("prescriptionType"));
        return inventoryService.deductFromPrescription(herbals, prescriptionType);
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner,pharmacist')")
    @PostMapping("/restore-prescription")
    @SuppressWarnings("unchecked")
    public Map<String, Object> restorePrescription(@RequestBody Map<String, Object> body)
    {
        List<Map<String, Object>> herbals = requireHerbals(body.get("herbals"));
        String prescriptionType = requirePrescriptionType(body.get("prescriptionType"));
        return inventoryService.restoreFromPrescription(herbals, prescriptionType);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping("/batch-import")
    @SuppressWarnings("unchecked")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchImport(@RequestBody Map<String, Object> body)
    {
        Object itemsObj = body.get("items");
        if (itemsObj != null && !(itemsObj instanceof List))
        {
            throw new ServiceException("items must be an array");
        }
        List<Map<String, Object>> items = (List<Map<String, Object>>) itemsObj;
        if (items == null || items.isEmpty())
        {
            Map<String, Object> r = new HashMap<>();
            r.put("created", 0);
            r.put("updated", 0);
            r.put("errors", new ArrayList<>());
            return r;
        }
        int created = 0;
        int updated = 0;
        List<String> errors = new ArrayList<>();
        Map<String, List<TcmHerbDict>> herbDictIndex = buildHerbDictNameIndex(
                herbDictService.selectTcmHerbDictList(new TcmHerbDict()));
        // 提前查一次全量库存，避免N+1查询
        List<TcmInventoryItem> allItems = inventoryService.selectTcmInventoryItemList(new TcmInventoryItem());
        Map<String, TcmInventoryItem> exactInventoryIndex = new HashMap<>();
        Map<String, List<TcmInventoryItem>> relaxedInventoryIndex = new HashMap<>();
        for (TcmInventoryItem inv : allItems)
        {
            if (inv.getIsActive() != null && inv.getIsActive() == 1
                    && (inv.getDeletedAt() == null || inv.getDeletedAt().isEmpty()))
            {
                indexInventoryItem(exactInventoryIndex, relaxedInventoryIndex, inv);
            }
        }
        for (Map<String, Object> item : items)
        {
            normalizeInventoryImportCategory(item);
            String name = item.get("name") != null ? String.valueOf(item.get("name")) : "";
            if (name.isEmpty()) continue;
            if (!normalizeRawHerbImportItem(item, herbDictIndex, errors))
            {
                continue;
            }
            InventoryImportMatch match = resolveInventoryImportMatch(item, exactInventoryIndex, relaxedInventoryIndex);
            if (match.getError() != null)
            {
                errors.add(match.getError());
                continue;
            }
            TcmInventoryItem existing = match.getItem();
            if (existing != null)
            {
                BigDecimal addQty = item.get("quantity") != null
                        ? parseQuantity(item.get("quantity"), name)
                        : BigDecimal.ZERO;
                BigDecimal currentQty = existing.getQuantity() != null
                        ? existing.getQuantity() : BigDecimal.ZERO;
                existing.setQuantity(currentQty.add(addQty));
                inventoryService.updateTcmInventoryItem(existing);
                indexInventoryItem(exactInventoryIndex, relaxedInventoryIndex, existing);
                updated++;
            }
            else
            {
                TcmInventoryItem newItem = PayloadUtils.toInventoryItem(item);
                inventoryService.insertTcmInventoryItem(newItem);
                indexInventoryItem(exactInventoryIndex, relaxedInventoryIndex, newItem);
                created++;
            }
        }
        if (created > 0 || updated > 0)
        {
            String actorId = safeCurrentUserId();
            auditLogService.log("inventory", null, "批量导入",
                    "BATCH_IMPORT", actorId, "批量导入: 新增" + created + "项，更新" + updated + "项");
        }
        Map<String, Object> r = new HashMap<>();
        r.put("created", created);
        r.put("updated", updated);
        r.put("errors", errors);
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
        return withLast30DaysUsage(inventoryService.selectByHerbDictId(herbDictId));
    }

    private List<Map<String, Object>> withLast30DaysUsage(List<TcmInventoryItem> items)
    {
        List<Map<String, Object>> rows = PayloadUtils.flattenInventory(items);
        Map<String, BigDecimal> usageMap = inventoryService.calculateLast30DaysUsage(items);
        List<TcmHerbDict> herbs = herbDictService.selectTcmHerbDictList(new TcmHerbDict());
        Map<String, TcmHerbDict> herbById = buildHerbDictIdIndex(herbs);
        Map<String, List<TcmHerbDict>> herbByName = buildHerbDictNameIndex(herbs);
        for (Map<String, Object> row : rows)
        {
            String inventoryId = row.get("id") != null ? String.valueOf(row.get("id")) : null;
            row.put("last30DaysUsage", usageMap.getOrDefault(inventoryId, BigDecimal.ZERO));
            enrichInventoryHerbMeta(row, herbById, herbByName);
        }
        return rows;
    }

    private Map<String, TcmHerbDict> buildHerbDictIdIndex(List<TcmHerbDict> herbs)
    {
        Map<String, TcmHerbDict> index = new HashMap<>();
        if (herbs == null)
        {
            return index;
        }
        for (TcmHerbDict herb : herbs)
        {
            if (isActiveHerbDict(herb) && hasText(herb.getId()))
            {
                index.put(herb.getId(), herb);
            }
        }
        return index;
    }

    private void enrichInventoryHerbMeta(
            Map<String, Object> row,
            Map<String, TcmHerbDict> herbById,
            Map<String, List<TcmHerbDict>> herbByName)
    {
        TcmHerbDict herb = resolveInventoryHerb(row, herbById, herbByName);
        if (herb == null)
        {
            return;
        }
        putIfBlank(row, "herbDictId", herb.getId());
        putIfBlank(row, "alias", herb.getAlias());
        putIfBlank(row, "aliases", herb.getAlias());
        putIfBlank(row, "nature", herb.getNature());
        putIfBlank(row, "taste", herb.getTaste());
        putIfBlank(row, "toxicity", herb.getToxicity());
        putIfBlank(row, "meridianTropism", herb.getMeridianTropism());
        putIfBlank(row, "guijing", herb.getMeridianTropism());
        putIfBlank(row, "functionsAndIndications", firstText(herb.getEfficacy(), herb.getIndication()));
        putIfBlank(row, "contraindications", herb.getContraindication());
    }

    private TcmHerbDict resolveInventoryHerb(
            Map<String, Object> row,
            Map<String, TcmHerbDict> herbById,
            Map<String, List<TcmHerbDict>> herbByName)
    {
        if (row == null)
        {
            return null;
        }
        Object herbDictId = row.get("herbDictId");
        if (herbDictId != null)
        {
            TcmHerbDict herb = herbById.get(String.valueOf(herbDictId));
            if (herb != null)
            {
                return herb;
            }
        }
        Object name = row.get("name");
        List<TcmHerbDict> matches = herbByName.getOrDefault(normalizeKey(name != null ? String.valueOf(name) : null), new ArrayList<>());
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private void putIfBlank(Map<String, Object> row, String key, String value)
    {
        if (row == null || !hasText(value))
        {
            return;
        }
        Object existing = row.get(key);
        if (existing == null || !hasText(String.valueOf(existing)))
        {
            row.put(key, value);
        }
    }

    private String firstText(String... values)
    {
        if (values == null)
        {
            return null;
        }
        for (String value : values)
        {
            if (hasText(value))
            {
                return value;
            }
        }
        return null;
    }

    private boolean hasText(String value)
    {
        return value != null && !value.trim().isEmpty();
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
        String normalizedHerbDictId = normalizeKey(herbDictId);
        String normalizedName = normalizedHerbDictId.isEmpty() ? normalizeKey(name) : "";
        return normalizeKey(branchId) + "::"
                + normalizeKey(normalizeInventoryCategory(category)) + "::"
                + normalizedName + "::"
                + normalizeKey(supplierId) + "::"
                + normalizeKey(supplier) + "::"
                + normalizedHerbDictId;
    }

    private String normalizeKey(String value)
    {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private void normalizeInventoryImportCategory(Map<String, Object> item)
    {
        if (item == null)
        {
            return;
        }
        item.put("category", normalizeInventoryCategory(
                item.get("category") != null ? String.valueOf(item.get("category")) : null));
    }

    private String normalizeInventoryCategory(String category)
    {
        if (category == null || category.trim().isEmpty())
        {
            return "raw_herbs";
        }
        String normalized = category.trim().toLowerCase()
                .replace("-", "_")
                .replace(" ", "_");
        String compact = normalized.replace("_", "");
        if ("raw_herbs".equals(normalized)
                || "rawherbs".equals(compact)
                || "rawherb".equals(compact)
                || "herbs".equals(compact)
                || "herb".equals(compact)
                || normalized.contains("草药")
                || normalized.contains("中药")
                || normalized.contains("饮片"))
        {
            return "raw_herbs";
        }
        if ("powder".equals(normalized)
                || "powders".equals(compact)
                || "granule".equals(compact)
                || "granules".equals(compact)
                || normalized.contains("颗粒")
                || normalized.contains("粉"))
        {
            return "powder";
        }
        if ("pills".equals(normalized)
                || "pill".equals(compact)
                || "patentmedicine".equals(compact)
                || "patentmedicines".equals(compact)
                || normalized.contains("成药")
                || normalized.contains("丸")
                || normalized.contains("片"))
        {
            return "pills";
        }
        return normalized;
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

    private String safeCurrentUserId()
    {
        try
        {
            return String.valueOf(SecurityUtils.getUserId());
        }
        catch (Exception e)
        {
            return "system";
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> requireHerbals(Object herbals)
    {
        if (herbals == null)
        {
            return null;
        }
        if (!(herbals instanceof List))
        {
            throw new ServiceException("herbals must be an array");
        }
        return (List<Map<String, Object>>) herbals;
    }

    private String requirePrescriptionType(Object prescriptionType)
    {
        if (prescriptionType == null)
        {
            return null;
        }
        if (!(prescriptionType instanceof String))
        {
            throw new ServiceException("prescriptionType must be a string");
        }
        return (String) prescriptionType;
    }

    private BigDecimal parseQuantity(Object quantity, String itemName)
    {
        try
        {
            return new BigDecimal(String.valueOf(quantity));
        }
        catch (NumberFormatException e)
        {
            String target = itemName == null || itemName.trim().isEmpty() ? "inventory item" : itemName;
            throw new ServiceException(target + " quantity must be a valid number");
        }
    }

    private void indexInventoryItem(
            Map<String, TcmInventoryItem> exactInventoryIndex,
            Map<String, List<TcmInventoryItem>> relaxedInventoryIndex,
            TcmInventoryItem item)
    {
        exactInventoryIndex.put(buildInventoryImportKey(item), item);
        if (!isBlank(item.getSupplier()))
        {
            addRelaxedInventoryCandidate(relaxedInventoryIndex, buildInventoryImportKey(
                    item.getBranchId(),
                    item.getCategory(),
                    item.getName(),
                    null,
                    item.getSupplier(),
                    item.getHerbDictId()), item);
        }
        if (!isBlank(item.getSupplierId()))
        {
            addRelaxedInventoryCandidate(relaxedInventoryIndex, buildInventoryImportKey(
                    item.getBranchId(),
                    item.getCategory(),
                    item.getName(),
                    item.getSupplierId(),
                    null,
                    item.getHerbDictId()), item);
        }
    }

    private void addRelaxedInventoryCandidate(
            Map<String, List<TcmInventoryItem>> relaxedInventoryIndex,
            String key,
            TcmInventoryItem item)
    {
        List<TcmInventoryItem> candidates = relaxedInventoryIndex.computeIfAbsent(key, ignored -> new ArrayList<>());
        for (TcmInventoryItem candidate : candidates)
        {
            if (candidate.getId() != null && candidate.getId().equals(item.getId()))
            {
                return;
            }
        }
        candidates.add(item);
    }

    private InventoryImportMatch resolveInventoryImportMatch(
            Map<String, Object> item,
            Map<String, TcmInventoryItem> exactInventoryIndex,
            Map<String, List<TcmInventoryItem>> relaxedInventoryIndex)
    {
        TcmInventoryItem exact = exactInventoryIndex.get(buildInventoryImportKey(item));
        if (exact != null)
        {
            return InventoryImportMatch.matched(exact);
        }

        String supplierId = item.get("supplierId") != null ? String.valueOf(item.get("supplierId")) : null;
        String supplier = item.get("supplier") != null ? String.valueOf(item.get("supplier")) : null;
        if (isBlank(supplierId) == isBlank(supplier))
        {
            return InventoryImportMatch.unmatched();
        }

        List<TcmInventoryItem> candidates = relaxedInventoryIndex.get(buildInventoryImportKey(item));
        if (candidates == null || candidates.isEmpty())
        {
            return InventoryImportMatch.unmatched();
        }
        if (candidates.size() > 1)
        {
            String name = item.get("name") != null ? String.valueOf(item.get("name")).trim() : "inventory item";
            return InventoryImportMatch.error(name + " inventory supplier match is ambiguous");
        }
        return InventoryImportMatch.matched(candidates.get(0));
    }

    private boolean normalizeRawHerbImportItem(
            Map<String, Object> item,
            Map<String, List<TcmHerbDict>> herbDictIndex,
            List<String> errors)
    {
        String category = item.get("category") != null ? String.valueOf(item.get("category")) : null;
        if (!"raw_herbs".equals(category))
        {
            return true;
        }
        String name = item.get("name") != null ? String.valueOf(item.get("name")).trim() : "";
        List<TcmHerbDict> matches = herbDictIndex.getOrDefault(normalizeKey(name), new ArrayList<>());
        if (matches.isEmpty())
        {
            errors.add(name + " herb dictionary match not found");
            return false;
        }
        if (matches.size() > 1)
        {
            errors.add(name + " herb dictionary match is ambiguous");
            return false;
        }
        TcmHerbDict herb = matches.get(0);
        item.put("herbDictId", herb.getId());
        item.put("name", herb.getName());
        return true;
    }

    private Map<String, List<TcmHerbDict>> buildHerbDictNameIndex(List<TcmHerbDict> herbs)
    {
        Map<String, List<TcmHerbDict>> index = new HashMap<>();
        if (herbs == null)
        {
            return index;
        }
        for (TcmHerbDict herb : herbs)
        {
            if (!isActiveHerbDict(herb))
            {
                continue;
            }
            index.computeIfAbsent(normalizeKey(herb.getName()), key -> new ArrayList<>()).add(herb);
        }
        return index;
    }

    private boolean isActiveHerbDict(TcmHerbDict herb)
    {
        return herb != null
                && herb.getName() != null
                && !herb.getName().trim().isEmpty()
                && herb.getIsActive() != null
                && herb.getIsActive() == 1
                && (herb.getDeletedAt() == null || herb.getDeletedAt().trim().isEmpty());
    }

    private boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }

    private static class InventoryImportMatch
    {
        private final TcmInventoryItem item;
        private final String error;

        private InventoryImportMatch(TcmInventoryItem item, String error)
        {
            this.item = item;
            this.error = error;
        }

        private static InventoryImportMatch matched(TcmInventoryItem item)
        {
            return new InventoryImportMatch(item, null);
        }

        private static InventoryImportMatch unmatched()
        {
            return new InventoryImportMatch(null, null);
        }

        private static InventoryImportMatch error(String error)
        {
            return new InventoryImportMatch(null, error);
        }

        private TcmInventoryItem getItem()
        {
            return item;
        }

        private String getError()
        {
            return error;
        }
    }
}
