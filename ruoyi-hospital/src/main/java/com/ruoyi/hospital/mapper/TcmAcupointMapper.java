package com.ruoyi.hospital.mapper;

import java.util.List;
import com.ruoyi.hospital.domain.TcmAcupoint;

/**
 * 针灸穴位Mapper接口
 *
 * @author ruoyi
 */
public interface TcmAcupointMapper
{
    List<TcmAcupoint> selectTcmAcupointList(TcmAcupoint acupoint);

    TcmAcupoint selectTcmAcupointById(String id);

    int insertTcmAcupoint(TcmAcupoint acupoint);

    int updateTcmAcupoint(TcmAcupoint acupoint);

    int deleteTcmAcupointById(String id);
}
