package com.ruoyi.hospital.mapper;

import java.util.List;
import com.ruoyi.hospital.domain.TcmMeridian;

public interface TcmMeridianMapper
{
    List<TcmMeridian> selectTcmMeridianList(TcmMeridian meridian);
    TcmMeridian selectTcmMeridianById(String id);
    int insertTcmMeridian(TcmMeridian meridian);
    int updateTcmMeridian(TcmMeridian meridian);
    int deleteTcmMeridianById(String id);
}
