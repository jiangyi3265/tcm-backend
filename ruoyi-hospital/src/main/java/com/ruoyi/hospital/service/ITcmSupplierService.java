package com.ruoyi.hospital.service;

import java.util.List;
import com.ruoyi.hospital.domain.TcmSupplier;

/**
 * 供应商 Service接口
 *
 * @author ruoyi
 */
public interface ITcmSupplierService
{
    /**
     * 查询供应商列表
     */
    List<TcmSupplier> selectTcmSupplierList(TcmSupplier supplier);

    /**
     * 查询供应商
     */
    TcmSupplier selectTcmSupplierById(String id);

    /**
     * 新增供应商
     */
    int insertTcmSupplier(TcmSupplier supplier);

    /**
     * 修改供应商
     */
    int updateTcmSupplier(TcmSupplier supplier);

    /**
     * 软删除供应商
     */
    TcmSupplier softDeleteTcmSupplier(String id);

    /**
     * 恢复供应商
     */
    TcmSupplier restoreTcmSupplier(String id);

    /**
     * 硬删除供应商
     */
    int hardDeleteTcmSupplier(String id);
}
