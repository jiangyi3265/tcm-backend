package com.ruoyi.hospital.service;

import java.util.List;
import com.ruoyi.hospital.domain.TcmServiceType;

/**
 * 服务类型 Service接口
 *
 * @author ruoyi
 */
public interface ITcmServiceTypeService
{
    /**
     * 查询所有服务类型
     *
     * @return 服务类型集合
     */
    List<TcmServiceType> selectAll();

    /**
     * 根据服务键查询服务类型
     *
     * @param serviceKey 服务键
     * @return 服务类型信息
     */
    TcmServiceType selectByKey(String serviceKey);

    /**
     * 修改服务类型
     *
     * @param serviceType 服务类型信息
     * @return 影响行数
     */
    int updateServiceType(TcmServiceType serviceType);
}
