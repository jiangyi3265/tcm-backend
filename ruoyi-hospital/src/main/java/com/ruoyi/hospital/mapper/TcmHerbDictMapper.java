package com.ruoyi.hospital.mapper;

import java.util.List;
import com.ruoyi.hospital.domain.TcmHerbDict;

public interface TcmHerbDictMapper
{
    List<TcmHerbDict> selectTcmHerbDictList(TcmHerbDict herbDict);
    TcmHerbDict selectTcmHerbDictById(String id);
    int insertTcmHerbDict(TcmHerbDict herbDict);
    int updateTcmHerbDict(TcmHerbDict herbDict);
    int deleteTcmHerbDictById(String id);
}
