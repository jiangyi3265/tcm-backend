package com.ruoyi.hospital.service;

import java.util.List;
import com.ruoyi.hospital.domain.TcmFormula;

/**
 * 方剂 Service接口
 *
 * @author ruoyi
 */
public interface ITcmFormulaService
{
    /**
     * 查询方剂列表（含药材明细）
     */
    List<TcmFormula> selectTcmFormulaList(TcmFormula formula);

    /**
     * 查询方剂详情（含药材明细）
     */
    TcmFormula selectTcmFormulaById(String id);

    /**
     * 新增方剂（含药材明细）
     */
    int insertTcmFormula(TcmFormula formula);

    /**
     * 修改方剂（含药材明细）
     */
    int updateTcmFormula(TcmFormula formula);

    /**
     * 软删除方剂
     */
    TcmFormula softDeleteTcmFormula(String id);

    /**
     * 恢复方剂
     */
    TcmFormula restoreTcmFormula(String id);

    /**
     * 硬删除方剂
     */
    int hardDeleteTcmFormula(String id);
}
