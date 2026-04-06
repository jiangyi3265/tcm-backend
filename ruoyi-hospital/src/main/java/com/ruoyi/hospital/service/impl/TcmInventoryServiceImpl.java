package com.ruoyi.hospital.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.hospital.domain.TcmHerbDict;
import com.ruoyi.hospital.domain.TcmInventoryItem;
import com.ruoyi.hospital.mapper.TcmInventoryItemMapper;
import com.ruoyi.hospital.service.ITcmHerbDictService;
import com.ruoyi.hospital.service.ITcmInventoryService;

@Service
public class TcmInventoryServiceImpl implements ITcmInventoryService
{
    @Autowired
    private TcmInventoryItemMapper inventoryMapper;

    @Autowired
    private ITcmHerbDictService herbDictService;

    private static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    @Override
    public List<TcmInventoryItem> selectTcmInventoryItemList(TcmInventoryItem item)
    {
        return inventoryMapper.selectTcmInventoryItemList(item);
    }

    @Override
    public TcmInventoryItem selectTcmInventoryItemById(String id)
    {
        return inventoryMapper.selectTcmInventoryItemById(id);
    }

    @Override
    public int insertTcmInventoryItem(TcmInventoryItem item)
    {
        normalizeRawHerbItem(item, null);
        if (item.getId() == null || item.getId().isEmpty())
        {
            item.setId(java.util.UUID.randomUUID().toString());
        }
        item.setCreateTime(DateUtils.getNowDate());
        return inventoryMapper.insertTcmInventoryItem(item);
    }

    @Override
    public int updateTcmInventoryItem(TcmInventoryItem item)
    {
        if (item == null || item.getId() == null || item.getId().trim().isEmpty())
        {
            throw new ServiceException("inventory item not found");
        }
        TcmInventoryItem existing = inventoryMapper.selectTcmInventoryItemById(item.getId());
        if (existing == null)
        {
            throw new ServiceException("inventory item not found");
        }
        mergeExistingForSparseUpdate(item, existing);
        normalizeRawHerbItem(item, existing);
        return inventoryMapper.updateTcmInventoryItem(item);
    }

    @Override
    public TcmInventoryItem softDeleteTcmInventoryItem(String id)
    {
        TcmInventoryItem item = inventoryMapper.selectTcmInventoryItemById(id);
        if (item == null)
        {
            throw new ServiceException("inventory item not found");
        }
        item.setDeletedAt(new SimpleDateFormat(DATETIME_FORMAT).format(new Date()));
        item.setIsActive(0);
        inventoryMapper.updateTcmInventoryItem(item);
        return item;
    }

    @Override
    public TcmInventoryItem restoreTcmInventoryItem(String id)
    {
        TcmInventoryItem item = inventoryMapper.selectTcmInventoryItemById(id);
        if (item == null)
        {
            throw new ServiceException("inventory item not found");
        }
        item.setDeletedAt(null);
        item.setIsActive(1);
        inventoryMapper.updateTcmInventoryItem(item);
        return item;
    }

    @Override
    public int hardDeleteTcmInventoryItem(String id)
    {
        TcmInventoryItem item = inventoryMapper.selectTcmInventoryItemById(id);
        if (item == null)
        {
            throw new ServiceException("inventory item not found");
        }
        if (item.getDeletedAt() == null || item.getDeletedAt().isEmpty())
        {
            throw new ServiceException("record must be soft deleted before physical deletion");
        }
        try
        {
            SimpleDateFormat sdf = new SimpleDateFormat(DATETIME_FORMAT);
            Date deletedDate = sdf.parse(item.getDeletedAt());
            long threeMonthsMs = 90L * 24 * 60 * 60 * 1000;
            if (System.currentTimeMillis() - deletedDate.getTime() < threeMonthsMs)
            {
                throw new ServiceException("record must stay in recycle bin for at least 3 months");
            }
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ServiceException("failed to parse deleted time");
        }
        return inventoryMapper.deleteTcmInventoryItemById(id);
    }

    @Override
    public TcmInventoryItem adjustStock(String id, BigDecimal delta)
    {
        TcmInventoryItem item = inventoryMapper.selectTcmInventoryItemById(id);
        if (item == null)
        {
            throw new ServiceException("inventory item not found");
        }
        BigDecimal currentQty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
        BigDecimal newQty = currentQty.add(delta);
        if (newQty.compareTo(BigDecimal.ZERO) < 0)
        {
            throw new ServiceException("inventory quantity cannot be negative");
        }
        item.setQuantity(newQty);
        inventoryMapper.updateTcmInventoryItem(item);
        return item;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> deductFromPrescription(List<Map<String, Object>> herbals, String prescriptionType)
    {
        if (herbals == null || herbals.isEmpty() || "none".equals(prescriptionType))
        {
            return emptyResult();
        }

        String category = mapPrescriptionTypeToCategory(prescriptionType);
        List<Map<String, Object>> deductionPlan = new ArrayList<>();
        List<Map<String, Object>> notFound = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Map<String, Object> herbal : herbals)
        {
            String name = stringValue(herbal.get("name"));
            String inventoryId = stringValue(herbal.get("inventoryId"));
            String preferredSupplierId = stringValue(herbal.get("supplierId"));
            BigDecimal quantity = readRequestedQuantity(herbal);
            TcmInventoryItem item = resolveInventoryItem(inventoryId, name, category, preferredSupplierId);

            if (item == null)
            {
                Map<String, Object> record = new HashMap<>();
                record.put("name", name);
                record.put("quantity", quantity);
                record.put("inventoryId", inventoryId);
                notFound.add(record);
                errors.add(name + " inventory item not found");
                continue;
            }

            BigDecimal currentQty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            BigDecimal remainingQty = currentQty.subtract(quantity);
            Map<String, Object> record = new HashMap<>();
            record.put("inventoryId", item.getId());
            record.put("name", name);
            record.put("quantity", quantity);
            record.put("currentQuantity", currentQty);
            record.put("remainingQuantity", remainingQty);
            record.put("supplierId", item.getSupplierId());
            record.put("supplier", item.getSupplier());
            record.put("item", item);
            deductionPlan.add(record);

            if (remainingQty.compareTo(BigDecimal.ZERO) < 0)
            {
                errors.add(name + " inventory is insufficient, current: " + currentQty + ", requested: " + quantity);
            }
        }

        if (!errors.isEmpty())
        {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("errors", errors);
            result.put("deducted", new ArrayList<>());
            result.put("notFound", notFound);
            result.put("warnings", new ArrayList<>());
            return result;
        }

        List<Map<String, Object>> deducted = new ArrayList<>();
        for (Map<String, Object> plan : deductionPlan)
        {
            TcmInventoryItem item = (TcmInventoryItem) plan.get("item");
            item.setQuantity((BigDecimal) plan.get("remainingQuantity"));
            inventoryMapper.updateTcmInventoryItem(item);

            Map<String, Object> record = new HashMap<>(plan);
            record.remove("item");
            deducted.add(record);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("deducted", deducted);
        result.put("notFound", notFound);
        result.put("warnings", new ArrayList<>());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> restoreFromPrescription(List<Map<String, Object>> herbals, String prescriptionType)
    {
        if (herbals == null || herbals.isEmpty() || "none".equals(prescriptionType))
        {
            return emptyResult();
        }

        String category = mapPrescriptionTypeToCategory(prescriptionType);
        List<Map<String, Object>> restored = new ArrayList<>();
        List<Map<String, Object>> notFound = new ArrayList<>();

        for (Map<String, Object> herbal : herbals)
        {
            String name = stringValue(herbal.get("name"));
            String inventoryId = stringValue(herbal.get("inventoryId"));
            String preferredSupplierId = stringValue(herbal.get("supplierId"));
            BigDecimal quantity = readRequestedQuantity(herbal);
            TcmInventoryItem item = resolveInventoryItem(inventoryId, name, category, preferredSupplierId);

            if (item == null)
            {
                Map<String, Object> record = new HashMap<>();
                record.put("name", name);
                record.put("quantity", quantity);
                record.put("inventoryId", inventoryId);
                notFound.add(record);
                continue;
            }

            BigDecimal currentQty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            item.setQuantity(currentQty.add(quantity));
            inventoryMapper.updateTcmInventoryItem(item);

            Map<String, Object> record = new HashMap<>();
            record.put("inventoryId", item.getId());
            record.put("name", name);
            record.put("quantity", quantity);
            record.put("remainingQuantity", item.getQuantity());
            restored.add(record);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("deducted", restored);
        result.put("notFound", notFound);
        result.put("warnings", new ArrayList<>());
        return result;
    }

    @Override
    public List<TcmInventoryItem> selectByHerbDictId(String herbDictId)
    {
        return inventoryMapper.selectByHerbDictId(herbDictId);
    }

    private Map<String, Object> emptyResult()
    {
        Map<String, Object> empty = new HashMap<>();
        empty.put("success", true);
        empty.put("deducted", new ArrayList<>());
        empty.put("notFound", new ArrayList<>());
        empty.put("warnings", new ArrayList<>());
        return empty;
    }

    private BigDecimal readRequestedQuantity(Map<String, Object> herbal)
    {
        if (herbal.containsKey("convertedQty"))
        {
            BigDecimal convertedQty = toBigDecimal(herbal.get("convertedQty"));
            if (convertedQty.compareTo(BigDecimal.ZERO) > 0)
            {
                return convertedQty;
            }
        }
        if (herbal.containsKey("quantity"))
        {
            return toBigDecimal(herbal.get("quantity"));
        }
        return toBigDecimal(herbal.get("dosage"));
    }

    private TcmInventoryItem resolveInventoryItem(String inventoryId, String name, String category, String preferredSupplierId)
    {
        if (inventoryId != null && !inventoryId.isEmpty() && !"null".equals(inventoryId))
        {
            TcmInventoryItem exact = inventoryMapper.selectTcmInventoryItemById(inventoryId);
            if (exact != null
                    && category.equals(exact.getCategory())
                    && (exact.getDeletedAt() == null || exact.getDeletedAt().isEmpty())
                    && exact.getIsActive() != null
                    && exact.getIsActive() == 1)
            {
                return exact;
            }
        }

        List<TcmInventoryItem> candidates = inventoryMapper.selectTcmInventoryItemsByName(name, category);
        if (candidates == null || candidates.isEmpty())
        {
            return null;
        }
        if (preferredSupplierId != null && !preferredSupplierId.isEmpty() && !"null".equals(preferredSupplierId))
        {
            for (TcmInventoryItem candidate : candidates)
            {
                if (preferredSupplierId.equals(candidate.getSupplierId()))
                {
                    return candidate;
                }
            }
        }
        return candidates.get(0);
    }

    private String mapPrescriptionTypeToCategory(String prescriptionType)
    {
        if (prescriptionType == null)
        {
            return "raw_herbs";
        }
        switch (prescriptionType)
        {
            case "raw_herbs":
                return "raw_herbs";
            case "powder":
                return "powder";
            case "pills":
                return "pills";
            default:
                return "raw_herbs";
        }
    }

    private BigDecimal toBigDecimal(Object obj)
    {
        if (obj == null)
        {
            return BigDecimal.ZERO;
        }
        if (obj instanceof BigDecimal)
        {
            return (BigDecimal) obj;
        }
        if (obj instanceof Number)
        {
            return new BigDecimal(obj.toString());
        }
        try
        {
            return new BigDecimal(obj.toString());
        }
        catch (NumberFormatException e)
        {
            return BigDecimal.ZERO;
        }
    }

    private String stringValue(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }

    private void mergeExistingForSparseUpdate(TcmInventoryItem item, TcmInventoryItem existing)
    {
        item.setDeletedAt(existing.getDeletedAt());
        item.setPayload(existing.getPayload());
        item.setIsActive(existing.getIsActive());
        item.setCategory(mergeValue(item.getCategory(), existing.getCategory()));
        item.setHerbDictId(mergeValue(item.getHerbDictId(), existing.getHerbDictId()));
        item.setName(mergeValue(item.getName(), existing.getName()));
    }

    private void normalizeRawHerbItem(TcmInventoryItem item, TcmInventoryItem existing)
    {
        if (item == null)
        {
            return;
        }
        String category = mergeValue(item.getCategory(), existing != null ? existing.getCategory() : null);
        if (!"raw_herbs".equals(category))
        {
            return;
        }

        String herbDictId = mergeValue(item.getHerbDictId(), existing != null ? existing.getHerbDictId() : null);
        if (herbDictId == null || herbDictId.trim().isEmpty())
        {
            throw new ServiceException("raw herb herbDictId is required");
        }

        TcmHerbDict herb = requireActiveHerbDict(herbDictId);
        item.setHerbDictId(herb.getId());
        item.setName(herb.getName());
    }

    private TcmHerbDict requireActiveHerbDict(String herbDictId)
    {
        TcmHerbDict herb = herbDictService.selectTcmHerbDictById(herbDictId);
        if (herb == null
                || herb.getIsActive() == null
                || herb.getIsActive() != 1
                || (herb.getDeletedAt() != null && !herb.getDeletedAt().trim().isEmpty()))
        {
            throw new ServiceException("herb dictionary entry is invalid or inactive");
        }
        return herb;
    }

    private String mergeValue(String requestValue, String existingValue)
    {
        if (requestValue != null && !requestValue.trim().isEmpty())
        {
            return requestValue;
        }
        return existingValue;
    }
}
