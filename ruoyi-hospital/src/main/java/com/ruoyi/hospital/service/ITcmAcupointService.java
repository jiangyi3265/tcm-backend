package com.ruoyi.hospital.service;

import java.util.List;
import com.ruoyi.hospital.domain.TcmAcupoint;

/**
 * 针灸穴位Service接口
 *
 * @author ruoyi
 */
public interface ITcmAcupointService
{
    List<TcmAcupoint> selectTcmAcupointList(TcmAcupoint acupoint);

    TcmAcupoint selectTcmAcupointById(String id);

    int insertTcmAcupoint(TcmAcupoint acupoint);

    int updateTcmAcupoint(TcmAcupoint acupoint);

    TcmAcupoint softDeleteTcmAcupoint(String id);

    TcmAcupoint restoreTcmAcupoint(String id);

    int hardDeleteTcmAcupoint(String id);
}
