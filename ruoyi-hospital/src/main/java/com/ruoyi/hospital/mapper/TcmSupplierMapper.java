package com.ruoyi.hospital.mapper;

import java.util.List;
import com.ruoyi.hospital.domain.TcmSupplier;

/**
 * 供应商Mapper接口
 *
 * @author ruoyi
 */
public interface TcmSupplierMapper
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
     * 删除供应商
     */
    int deleteTcmSupplierById(String id);
}
