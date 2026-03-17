package com.ruoyi.hospital.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.hospital.domain.TcmServiceType;
import com.ruoyi.hospital.mapper.TcmServiceTypeMapper;
import com.ruoyi.hospital.service.ITcmServiceTypeService;

/**
 * 服务类型 Service业务层处理
 *
 * @author ruoyi
 */
@Service
public class TcmServiceTypeServiceImpl implements ITcmServiceTypeService
{
    @Autowired
    private TcmServiceTypeMapper tcmServiceTypeMapper;

    /**
     * 查询所有服务类型
     *
     * @return 服务类型集合
     */
    @Override
    public List<TcmServiceType> selectAll()
    {
        return tcmServiceTypeMapper.selectTcmServiceTypeList(new TcmServiceType());
    }

    /**
     * 根据服务键查询服务类型
     *
     * @param serviceKey 服务键
     * @return 服务类型信息
     */
    @Override
    public TcmServiceType selectByKey(String serviceKey)
    {
        return tcmServiceTypeMapper.selectTcmServiceTypeByKey(serviceKey);
    }

    /**
     * 修改服务类型
     *
     * @param serviceType 服务类型信息
     * @return 影响行数
     */
    @Override
    public int updateServiceType(TcmServiceType serviceType)
    {
        return tcmServiceTypeMapper.updateTcmServiceType(serviceType);
    }
}
