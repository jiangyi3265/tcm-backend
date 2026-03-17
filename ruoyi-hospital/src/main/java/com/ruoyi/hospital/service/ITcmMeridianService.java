package com.ruoyi.hospital.service;

import java.util.List;
import com.ruoyi.hospital.domain.TcmMeridian;

public interface ITcmMeridianService
{
    List<TcmMeridian> selectTcmMeridianList(TcmMeridian meridian);
    TcmMeridian selectTcmMeridianById(String id);
    int insertTcmMeridian(TcmMeridian meridian);
    int updateTcmMeridian(TcmMeridian meridian);
    TcmMeridian softDeleteTcmMeridian(String id);
    TcmMeridian restoreTcmMeridian(String id);
    int hardDeleteTcmMeridian(String id);
}
