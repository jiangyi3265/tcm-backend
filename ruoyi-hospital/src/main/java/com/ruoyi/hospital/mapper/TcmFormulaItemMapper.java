package com.ruoyi.hospital.mapper;

import java.util.List;
import com.ruoyi.hospital.domain.TcmFormulaItem;

/**
 * 方剂药材明细Mapper接口
 *
 * @author ruoyi
 */
public interface TcmFormulaItemMapper
{
    /**
     * 根据方剂ID查询明细列表
     */
    List<TcmFormulaItem> selectByFormulaId(String formulaId);

    /**
     * 新增方剂药材明细
     */
    int insertTcmFormulaItem(TcmFormulaItem item);

    /**
     * 批量新增方剂药材明细
     */
    int batchInsert(List<TcmFormulaItem> items);

    /**
     * 根据方剂ID删除所有明细
     */
    int deleteByFormulaId(String formulaId);
}
