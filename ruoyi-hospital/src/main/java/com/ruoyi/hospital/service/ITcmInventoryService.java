package com.ruoyi.hospital.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.ruoyi.hospital.domain.TcmInventoryItem;

/**
 * 中药库存 Service接口
 *
 * @author ruoyi
 */
public interface ITcmInventoryService
{
    /**
     * 查询库存项列表
     *
     * @param item 库存项查询条件
     * @return 库存项集合
     */
    List<TcmInventoryItem> selectTcmInventoryItemList(TcmInventoryItem item);

    /**
     * 查询库存项列表（包含已软删除项）
     *
     * @param item 库存项查询条件
     * @return 库存项集合
     */
    List<TcmInventoryItem> selectTcmInventoryItemListIncludingDeleted(TcmInventoryItem item);

    /**
     * 查询库存项详情
     *
     * @param id 库存项ID
     * @return 库存项信息
     */
    TcmInventoryItem selectTcmInventoryItemById(String id);

    /**
     * 新增库存项
     *
     * @param item 库存项信息
     * @return 影响行数
     */
    int insertTcmInventoryItem(TcmInventoryItem item);

    /**
     * 修改库存项
     *
     * @param item 库存项信息
     * @return 影响行数
     */
    int updateTcmInventoryItem(TcmInventoryItem item);

    /**
     * 软删除库存项
     *
     * @param id 库存项ID
     * @return 软删除后的库存项对象
     */
    TcmInventoryItem softDeleteTcmInventoryItem(String id);

    /**
     * 恢复已软删除的库存项
     *
     * @param id 库存项ID
     * @return 恢复后的库存项对象
     */
    TcmInventoryItem restoreTcmInventoryItem(String id);

    /**
     * 硬删除库存项
     *
     * @param id 库存项ID
     * @return 影响行数
     */
    int hardDeleteTcmInventoryItem(String id);

    /**
     * 调整库存数量
     *
     * @param id    库存项ID
     * @param delta 调整量（正数增加，负数减少）
     * @return 调整后的库存项对象
     */
    TcmInventoryItem adjustStock(String id, BigDecimal delta);

    /**
     * 根据处方扣减库存
     *
     * @param herbals          药材列表
     * @param prescriptionType 处方类型
     * @return 扣减结果
     */
    Map<String, Object> deductFromPrescription(List<Map<String, Object>> herbals, String prescriptionType);

    /**
     * 根据处方恢复库存
     *
     * @param herbals          药材列表
     * @param prescriptionType 处方类型
     * @return 恢复结果
     */
    Map<String, Object> restoreFromPrescription(List<Map<String, Object>> herbals, String prescriptionType);

    /**
     * Bug 7/10: 根据中药字典ID查询所有库存（多供应商）
     */
    List<TcmInventoryItem> selectByHerbDictId(String herbDictId);

    /**
     * 统计库存项近30天使用量
     *
     * @param items 当前库存项列表
     * @return key=inventoryId, value=近30天使用量
     */
    Map<String, BigDecimal> calculateLast30DaysUsage(List<TcmInventoryItem> items);
}
