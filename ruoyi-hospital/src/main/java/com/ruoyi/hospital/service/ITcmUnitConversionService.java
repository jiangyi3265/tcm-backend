package com.ruoyi.hospital.service;

import java.math.BigDecimal;
import java.util.List;
import com.ruoyi.hospital.domain.TcmUnitConversion;

/**
 * 单位换算Service接口
 *
 * @author ruoyi
 */
public interface ITcmUnitConversionService
{
    List<TcmUnitConversion> selectAll();

    TcmUnitConversion selectTcmUnitConversionById(Long id);

    BigDecimal convert(String fromUnit, String toUnit, BigDecimal value);

    int insertTcmUnitConversion(TcmUnitConversion conversion);

    int updateTcmUnitConversion(TcmUnitConversion conversion);

    int deleteTcmUnitConversionById(Long id);
}
