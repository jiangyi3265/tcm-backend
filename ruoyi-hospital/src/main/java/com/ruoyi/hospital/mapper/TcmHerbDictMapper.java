package com.ruoyi.hospital.mapper;

import java.util.List;
import com.ruoyi.hospital.domain.TcmHerbDict;
import org.apache.ibatis.annotations.Param;

public interface TcmHerbDictMapper
{
    List<TcmHerbDict> selectTcmHerbDictList(TcmHerbDict herbDict);
    TcmHerbDict selectTcmHerbDictById(String id);
    int insertTcmHerbDict(TcmHerbDict herbDict);
    int updateTcmHerbDict(TcmHerbDict herbDict);
    int updateInventoryHerbName(@Param("id") String id, @Param("oldName") String oldName, @Param("name") String name);
    int updateFormulaHerbName(@Param("id") String id, @Param("oldName") String oldName, @Param("name") String name);
    int deleteTcmHerbDictById(String id);
}
