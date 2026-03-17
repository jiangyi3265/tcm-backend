package com.ruoyi.hospital.service;

import java.util.List;
import com.ruoyi.hospital.domain.TcmHerbDict;

public interface ITcmHerbDictService
{
    List<TcmHerbDict> selectTcmHerbDictList(TcmHerbDict herbDict);
    TcmHerbDict selectTcmHerbDictById(String id);
    int insertTcmHerbDict(TcmHerbDict herbDict);
    int updateTcmHerbDict(TcmHerbDict herbDict);
    TcmHerbDict softDeleteTcmHerbDict(String id);
    TcmHerbDict restoreTcmHerbDict(String id);
    int hardDeleteTcmHerbDict(String id);
}
