package com.ruoyi.hospital.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.hospital.domain.TcmInventoryItem;

/**
 * 库存项目Mapper接口
 *
 * @author ruoyi
 */
public interface TcmInventoryItemMapper
{
    /**
     * 查询库存项目列表
     *
     * @param item 库存项目
     * @return 库存项目集合
     */
    List<TcmInventoryItem> selectTcmInventoryItemList(TcmInventoryItem item);

    /**
     * 查询库存项目
     *
     * @param id 库存项目主键
     * @return 库存项目
     */
    TcmInventoryItem selectTcmInventoryItemById(String id);

    /**
     * 根据名称和分类查询库存项目
     *
     * @param name 名称
     * @param category 分类
     * @return 库存项目
     */
    TcmInventoryItem selectTcmInventoryItemByName(@Param("name") String name, @Param("category") String category);

    /**
     * 根据名称和分类查询所有匹配的库存项目（按库存量降序）
     * 用于智能选择供应商（优先扣减库存最多的）
     */
    List<TcmInventoryItem> selectTcmInventoryItemsByName(@Param("name") String name, @Param("category") String category);

    /**
     * 新增库存项目
     *
     * @param item 库存项目
     * @return 结果
     */
    int insertTcmInventoryItem(TcmInventoryItem item);

    /**
     * 修改库存项目
     *
     * @param item 库存项目
     * @return 结果
     */
    int updateTcmInventoryItem(TcmInventoryItem item);

    /**
     * 删除库存项目
     *
     * @param id 库存项目主键
     * @return 结果
     */
    int deleteTcmInventoryItemById(String id);
}
