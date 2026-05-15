package com.ruoyi.hospital.service.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.hospital.domain.TcmFormula;
import com.ruoyi.hospital.domain.TcmFormulaItem;
import com.ruoyi.hospital.domain.TcmHerbDict;
import com.ruoyi.hospital.mapper.TcmFormulaMapper;
import com.ruoyi.hospital.mapper.TcmFormulaItemMapper;
import com.ruoyi.hospital.service.ITcmHerbDictService;
import com.ruoyi.hospital.service.ITcmFormulaService;

/**
 * 方剂 Service业务层处理
 *
 * @author ruoyi
 */
@Service
public class TcmFormulaServiceImpl implements ITcmFormulaService
{
    @Autowired
    private TcmFormulaMapper formulaMapper;

    @Autowired
    private TcmFormulaItemMapper formulaItemMapper;

    @Autowired
    private ITcmHerbDictService herbDictService;

    private static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    @Override
    public List<TcmFormula> selectTcmFormulaList(TcmFormula formula)
    {
        List<TcmFormula> list = formulaMapper.selectTcmFormulaList(formula);
        for (TcmFormula f : list)
        {
            f.setItems(formulaItemMapper.selectByFormulaId(f.getId()));
        }
        return list;
    }

    @Override
    public TcmFormula selectTcmFormulaById(String id)
    {
        TcmFormula formula = formulaMapper.selectTcmFormulaById(id);
        if (formula != null)
        {
            formula.setItems(formulaItemMapper.selectByFormulaId(id));
        }
        return formula;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertTcmFormula(TcmFormula formula)
    {
        if (formula.getId() == null || formula.getId().isEmpty())
        {
            formula.setId(java.util.UUID.randomUUID().toString());
        }
        formula.setCreateTime(DateUtils.getNowDate());
        int rows = formulaMapper.insertTcmFormula(formula);

        // 保存药材明细
        saveFormulaItems(formula);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateTcmFormula(TcmFormula formula)
    {
        if (formula == null || formula.getId() == null || formula.getId().trim().isEmpty())
        {
            throw new ServiceException("方剂不存在");
        }
        TcmFormula existing = formulaMapper.selectTcmFormulaById(formula.getId());
        if (existing == null)
        {
            throw new ServiceException("方剂不存在");
        }
        mergeExistingForSparseUpdate(formula, existing);
        int rows = formulaMapper.updateTcmFormula(formula);

        // 先删后插，更新药材明细
        if (formula.getItems() != null)
        {
            formulaItemMapper.deleteByFormulaId(formula.getId());
            saveFormulaItems(formula);
        }
        return rows;
    }

    @Override
    public TcmFormula softDeleteTcmFormula(String id)
    {
        TcmFormula formula = formulaMapper.selectTcmFormulaById(id);
        if (formula == null)
        {
            throw new ServiceException("方剂不存在");
        }
        formula.setDeletedAt(new SimpleDateFormat(DATETIME_FORMAT).format(new Date()));
        formula.setIsActive(0);
        formulaMapper.updateTcmFormula(formula);
        formula.setItems(formulaItemMapper.selectByFormulaId(id));
        return formula;
    }

    @Override
    public TcmFormula restoreTcmFormula(String id)
    {
        TcmFormula formula = formulaMapper.selectTcmFormulaById(id);
        if (formula == null)
        {
            throw new ServiceException("方剂不存在");
        }
        formula.setDeletedAt(null);
        formula.setIsActive(1);
        formulaMapper.updateTcmFormula(formula);
        formula.setItems(formulaItemMapper.selectByFormulaId(id));
        return formula;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int hardDeleteTcmFormula(String id)
    {
        TcmFormula formula = formulaMapper.selectTcmFormulaById(id);
        if (formula == null)
        {
            throw new ServiceException("方剂不存在");
        }
        if (formula.getDeletedAt() == null || formula.getDeletedAt().isEmpty())
        {
            throw new ServiceException("该记录未被软删除，无法物理删除");
        }
        try
        {
            SimpleDateFormat sdf = new SimpleDateFormat(DATETIME_FORMAT);
            Date deletedDate = sdf.parse(formula.getDeletedAt());
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
        // 级联删除会自动删除明细，但也显式删一下
        formulaItemMapper.deleteByFormulaId(id);
        return formulaMapper.deleteTcmFormulaById(id);
    }

    /**
     * 保存方剂药材明细
     */
    private void saveFormulaItems(TcmFormula formula)
    {
        List<TcmFormulaItem> items = formula.getItems();
        if (items != null && !items.isEmpty())
        {
            int order = 1;
            for (TcmFormulaItem item : items)
            {
                normalizeFormulaItem(item);
                item.setFormulaId(formula.getId());
                if (item.getSortOrder() == null || item.getSortOrder() == 0)
                {
                    item.setSortOrder(order);
                }
                order++;
            }
            formulaItemMapper.batchInsert(items);
        }
    }

    private void mergeExistingForSparseUpdate(TcmFormula formula, TcmFormula existing)
    {
        if (formula.getDeletedAt() == null)
        {
            formula.setDeletedAt(existing.getDeletedAt());
        }
        if (formula.getIsActive() == null)
        {
            formula.setIsActive(existing.getIsActive());
        }
    }

    private void normalizeFormulaItem(TcmFormulaItem item)
    {
        if (item == null)
        {
            return;
        }
        if (item.getHerbDictId() == null || item.getHerbDictId().trim().isEmpty())
        {
            throw new ServiceException("formula item herbDictId is required");
        }
        TcmHerbDict herb = herbDictService.selectTcmHerbDictById(item.getHerbDictId());
        if (herb == null
                || herb.getIsActive() == null
                || herb.getIsActive() != 1
                || (herb.getDeletedAt() != null && !herb.getDeletedAt().trim().isEmpty()))
        {
            throw new ServiceException("herb dictionary entry is invalid or inactive");
        }
        item.setHerbDictId(herb.getId());
        item.setHerbName(herb.getName());
    }
}
