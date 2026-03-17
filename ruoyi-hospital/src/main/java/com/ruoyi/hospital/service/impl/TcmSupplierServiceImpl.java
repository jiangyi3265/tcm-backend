package com.ruoyi.hospital.service.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.hospital.domain.TcmSupplier;
import com.ruoyi.hospital.mapper.TcmSupplierMapper;
import com.ruoyi.hospital.service.ITcmSupplierService;

/**
 * 供应商 Service业务层处理
 *
 * @author ruoyi
 */
@Service
public class TcmSupplierServiceImpl implements ITcmSupplierService
{
    @Autowired
    private TcmSupplierMapper supplierMapper;

    private static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    @Override
    public List<TcmSupplier> selectTcmSupplierList(TcmSupplier supplier)
    {
        return supplierMapper.selectTcmSupplierList(supplier);
    }

    @Override
    public TcmSupplier selectTcmSupplierById(String id)
    {
        return supplierMapper.selectTcmSupplierById(id);
    }

    @Override
    public int insertTcmSupplier(TcmSupplier supplier)
    {
        if (supplier.getId() == null || supplier.getId().isEmpty())
        {
            supplier.setId(java.util.UUID.randomUUID().toString());
        }
        supplier.setCreateTime(DateUtils.getNowDate());
        return supplierMapper.insertTcmSupplier(supplier);
    }

    @Override
    public int updateTcmSupplier(TcmSupplier supplier)
    {
        return supplierMapper.updateTcmSupplier(supplier);
    }

    @Override
    public TcmSupplier softDeleteTcmSupplier(String id)
    {
        TcmSupplier supplier = supplierMapper.selectTcmSupplierById(id);
        if (supplier == null)
        {
            throw new ServiceException("供应商不存在");
        }
        supplier.setDeletedAt(new SimpleDateFormat(DATETIME_FORMAT).format(new Date()));
        supplier.setIsActive(0);
        supplierMapper.updateTcmSupplier(supplier);
        return supplier;
    }

    @Override
    public TcmSupplier restoreTcmSupplier(String id)
    {
        TcmSupplier supplier = supplierMapper.selectTcmSupplierById(id);
        if (supplier == null)
        {
            throw new ServiceException("供应商不存在");
        }
        supplier.setDeletedAt(null);
        supplier.setIsActive(1);
        supplierMapper.updateTcmSupplier(supplier);
        return supplier;
    }

    @Override
    public int hardDeleteTcmSupplier(String id)
    {
        TcmSupplier supplier = supplierMapper.selectTcmSupplierById(id);
        if (supplier == null)
        {
            throw new ServiceException("供应商不存在");
        }
        if (supplier.getDeletedAt() == null || supplier.getDeletedAt().isEmpty())
        {
            throw new ServiceException("该记录未被软删除，无法物理删除");
        }
        try
        {
            SimpleDateFormat sdf = new SimpleDateFormat(DATETIME_FORMAT);
            Date deletedDate = sdf.parse(supplier.getDeletedAt());
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
        return supplierMapper.deleteTcmSupplierById(id);
    }
}
