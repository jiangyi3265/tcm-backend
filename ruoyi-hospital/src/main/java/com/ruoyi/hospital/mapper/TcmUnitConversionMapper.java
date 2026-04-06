package com.ruoyi.hospital.mapper;

import java.util.List;
import com.ruoyi.hospital.domain.TcmUnitConversion;

/**
 * 单位换算Mapper接口
 *
 * @author ruoyi
 */
public interface TcmUnitConversionMapper
{
    List<TcmUnitConversion> selectAll();

    TcmUnitConversion selectByPair(String fromUnit, String toUnit);

    TcmUnitConversion selectTcmUnitConversionById(Long id);

    int insertTcmUnitConversion(TcmUnitConversion conversion);

    int updateTcmUnitConversion(TcmUnitConversion conversion);

    int deleteTcmUnitConversionById(Long id);
}
