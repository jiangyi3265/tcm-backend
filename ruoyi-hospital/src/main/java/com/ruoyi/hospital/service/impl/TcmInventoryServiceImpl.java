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
import com.ruoyi.hospital.domain.TcmInventoryItem;
import com.ruoyi.hospital.mapper.TcmInventoryItemMapper;
import com.ruoyi.hospital.service.ITcmInventoryService;

/**
 * 中药库存 Service业务层处理
 *
 * @author ruoyi
 */
@Service
public class TcmInventoryServiceImpl implements ITcmInventoryService
{
    @Autowired
    private TcmInventoryItemMapper inventoryMapper;

    private static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * 查询库存项列表
     *
     * @param item 库存项查询条件
     * @return 库存项集合
     */
    @Override
    public List<TcmInventoryItem> selectTcmInventoryItemList(TcmInventoryItem item)
    {
        return inventoryMapper.selectTcmInventoryItemList(item);
    }

    /**
     * 查询库存项详情
     *
     * @param id 库存项ID
     * @return 库存项信息
     */
    @Override
    public TcmInventoryItem selectTcmInventoryItemById(String id)
    {
        return inventoryMapper.selectTcmInventoryItemById(id);
    }

    /**
     * 新增库存项
     *
     * @param item 库存项信息
     * @return 影响行数
     */
    @Override
    public int insertTcmInventoryItem(TcmInventoryItem item)
    {
        if (item.getId() == null || item.getId().isEmpty())
        {
            item.setId(java.util.UUID.randomUUID().toString());
        }
        item.setCreateTime(DateUtils.getNowDate());
        return inventoryMapper.insertTcmInventoryItem(item);
    }

    /**
     * 修改库存项
     *
     * @param item 库存项信息
     * @return 影响行数
     */
    @Override
    public int updateTcmInventoryItem(TcmInventoryItem item)
    {
        return inventoryMapper.updateTcmInventoryItem(item);
    }

    /**
     * 软删除库存项
     *
     * @param id 库存项ID
     * @return 软删除后的库存项对象
     */
    @Override
    public TcmInventoryItem softDeleteTcmInventoryItem(String id)
    {
        TcmInventoryItem item = inventoryMapper.selectTcmInventoryItemById(id);
        if (item == null)
        {
            throw new ServiceException("库存项不存在");
        }
        item.setDeletedAt(new SimpleDateFormat(DATETIME_FORMAT).format(new Date()));
        item.setIsActive(0);
        inventoryMapper.updateTcmInventoryItem(item);
        return item;
    }

    /**
     * 恢复已软删除的库存项
     *
     * @param id 库存项ID
     * @return 恢复后的库存项对象
     */
    @Override
    public TcmInventoryItem restoreTcmInventoryItem(String id)
    {
        TcmInventoryItem item = inventoryMapper.selectTcmInventoryItemById(id);
        if (item == null)
        {
            throw new ServiceException("库存项不存在");
        }
        item.setDeletedAt(null);
        item.setIsActive(1);
        inventoryMapper.updateTcmInventoryItem(item);
        return item;
    }

    /**
     * 硬删除库存项（需删除时间超过3个月）
     *
     * @param id 库存项ID
     * @return 影响行数
     */
    @Override
    public int hardDeleteTcmInventoryItem(String id)
    {
        TcmInventoryItem item = inventoryMapper.selectTcmInventoryItemById(id);
        if (item == null)
        {
            throw new ServiceException("库存项不存在");
        }
        if (item.getDeletedAt() == null || item.getDeletedAt().isEmpty())
        {
            throw new ServiceException("该记录未被软删除，无法物理删除");
        }
        try
        {
            SimpleDateFormat sdf = new SimpleDateFormat(DATETIME_FORMAT);
            Date deletedDate = sdf.parse(item.getDeletedAt());
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
        return inventoryMapper.deleteTcmInventoryItemById(id);
    }

    /**
     * 调整库存数量
     *
     * @param id    库存项ID
     * @param delta 调整量
     * @return 调整后的库存项对象
     */
    @Override
    public TcmInventoryItem adjustStock(String id, BigDecimal delta)
    {
        TcmInventoryItem item = inventoryMapper.selectTcmInventoryItemById(id);
        if (item == null)
        {
            throw new ServiceException("库存项不存在");
        }
        BigDecimal currentQty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
        BigDecimal newQty = currentQty.add(delta);
        if (newQty.compareTo(BigDecimal.ZERO) < 0)
        {
            throw new ServiceException("库存数量不能为负数，当前库存: " + currentQty + "，调整量: " + delta);
        }
        item.setQuantity(newQty);
        inventoryMapper.updateTcmInventoryItem(item);
        return item;
    }

    /**
     * 根据处方扣减库存
     *
     * @param herbals          药材列表
     * @param prescriptionType 处方类型
     * @return 扣减结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> deductFromPrescription(List<Map<String, Object>> herbals, String prescriptionType)
    {
        if (herbals == null || herbals.isEmpty() || "none".equals(prescriptionType))
        {
            Map<String, Object> empty = new HashMap<>();
            empty.put("success", true);
            empty.put("deducted", new ArrayList<>());
            empty.put("notFound", new ArrayList<>());
            empty.put("warnings", new ArrayList<>());
            return empty;
        }

        String category = mapPrescriptionTypeToCategory(prescriptionType);
        List<Map<String, Object>> deductionPlan = new ArrayList<>();
        List<Map<String, Object>> notFound = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Map<String, Object> herbal : herbals)
        {
            String name = (String) herbal.get("name");
            Object dosageObj = herbal.get("dosage");
            BigDecimal dosage = toBigDecimal(dosageObj);
            String preferredSupplierId = herbal.get("supplierId") != null
                    ? String.valueOf(herbal.get("supplierId")) : null;

            // 查找所有匹配的库存项（按库存量降序）
            List<TcmInventoryItem> candidates = inventoryMapper.selectTcmInventoryItemsByName(name, category);

            TcmInventoryItem item = null;
            if (candidates != null && !candidates.isEmpty())
            {
                if (preferredSupplierId != null && !preferredSupplierId.isEmpty()
                        && !"null".equals(preferredSupplierId))
                {
                    // 优先选择指定供应商的库存
                    for (TcmInventoryItem c : candidates)
                    {
                        if (preferredSupplierId.equals(c.getSupplierId()))
                        {
                            item = c;
                            break;
                        }
                    }
                }
                // 如果没有指定供应商或指定的供应商没有库存，选库存最多的
                if (item == null)
                {
                    item = candidates.get(0);
                }
            }

            if (item != null)
            {
                BigDecimal currentQty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                Map<String, Object> record = new HashMap<>();
                record.put("name", name);
                record.put("dosage", dosage);
                record.put("currentQuantity", currentQty);
                record.put("remainingQuantity", currentQty.subtract(dosage));
                record.put("supplierId", item.getSupplierId());
                record.put("supplier", item.getSupplier());
                record.put("item", item);
                deductionPlan.add(record);
                if (currentQty.subtract(dosage).compareTo(BigDecimal.ZERO) < 0)
                {
                    errors.add(name + " 库存不足，当前库存: " + currentQty + "，需要扣减: " + dosage);
                }
            }
            else
            {
                Map<String, Object> record = new HashMap<>();
                record.put("name", name);
                record.put("dosage", dosage);
                notFound.add(record);
                errors.add(name + " 未找到可用库存");
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

    /**
     * 根据处方恢复库存
     *
     * @param herbals          药材列表
     * @param prescriptionType 处方类型
     * @return 恢复结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> restoreFromPrescription(List<Map<String, Object>> herbals, String prescriptionType)
    {
        String category = mapPrescriptionTypeToCategory(prescriptionType);
        List<Map<String, Object>> deducted = new ArrayList<>();
        List<Map<String, Object>> notFound = new ArrayList<>();

        for (Map<String, Object> herbal : herbals)
        {
            String name = (String) herbal.get("name");
            Object dosageObj = herbal.get("dosage");
            BigDecimal dosage = toBigDecimal(dosageObj);

            TcmInventoryItem item = inventoryMapper.selectTcmInventoryItemByName(name, category);
            if (item != null)
            {
                BigDecimal currentQty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                item.setQuantity(currentQty.add(dosage));
                inventoryMapper.updateTcmInventoryItem(item);

                Map<String, Object> record = new HashMap<>();
                record.put("name", name);
                record.put("dosage", dosage);
                record.put("remainingQuantity", item.getQuantity());
                deducted.add(record);
            }
            else
            {
                Map<String, Object> record = new HashMap<>();
                record.put("name", name);
                record.put("dosage", dosage);
                notFound.add(record);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("deducted", deducted);
        result.put("notFound", notFound);
        return result;
    }

    /**
     * 将处方类型映射为库存分类
     */
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

    /**
     * 将Object转换为BigDecimal
     */
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
}
