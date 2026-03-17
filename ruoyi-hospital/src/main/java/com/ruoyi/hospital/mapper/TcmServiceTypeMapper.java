package com.ruoyi.hospital.mapper;

import java.util.List;
import com.ruoyi.hospital.domain.TcmServiceType;

/**
 * 服务类型Mapper接口
 *
 * @author ruoyi
 */
public interface TcmServiceTypeMapper
{
    /**
     * 查询服务类型列表
     *
     * @param type 服务类型
     * @return 服务类型集合
     */
    List<TcmServiceType> selectTcmServiceTypeList(TcmServiceType type);

    /**
     * 根据服务键查询服务类型
     *
     * @param serviceKey 服务键
     * @return 服务类型
     */
    TcmServiceType selectTcmServiceTypeByKey(String serviceKey);

    /**
     * 新增服务类型
     *
     * @param type 服务类型
     * @return 结果
     */
    int insertTcmServiceType(TcmServiceType type);

    /**
     * 修改服务类型
     *
     * @param type 服务类型
     * @return 结果
     */
    int updateTcmServiceType(TcmServiceType type);

    /**
     * 删除服务类型
     *
     * @param serviceKey 服务键
     * @return 结果
     */
    int deleteTcmServiceTypeByKey(String serviceKey);
}
