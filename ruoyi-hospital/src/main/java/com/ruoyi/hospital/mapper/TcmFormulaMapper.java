package com.ruoyi.hospital.mapper;

import java.util.List;
import com.ruoyi.hospital.domain.TcmFormula;

/**
 * 方剂Mapper接口
 *
 * @author ruoyi
 */
public interface TcmFormulaMapper
{
    /**
     * 查询方剂列表
     */
    List<TcmFormula> selectTcmFormulaList(TcmFormula formula);

    /**
     * 查询方剂
     */
    TcmFormula selectTcmFormulaById(String id);

    /**
     * 新增方剂
     */
    int insertTcmFormula(TcmFormula formula);

    /**
     * 修改方剂
     */
    int updateTcmFormula(TcmFormula formula);

    /**
     * 删除方剂
     */
    int deleteTcmFormulaById(String id);
}
