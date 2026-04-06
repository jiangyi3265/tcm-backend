package com.ruoyi.hospital.service.impl;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmUnitConversion;
import com.ruoyi.hospital.mapper.TcmUnitConversionMapper;
import com.ruoyi.hospital.service.ITcmUnitConversionService;

/**
 * 单位换算 Service业务层处理
 *
 * @author ruoyi
 */
@Service
public class TcmUnitConversionServiceImpl implements ITcmUnitConversionService
{
    @Autowired
    private TcmUnitConversionMapper conversionMapper;

    @Override
    public List<TcmUnitConversion> selectAll()
    {
        return conversionMapper.selectAll();
    }

    @Override
    public TcmUnitConversion selectTcmUnitConversionById(Long id)
    {
        return conversionMapper.selectTcmUnitConversionById(id);
    }

    @Override
    public BigDecimal convert(String fromUnit, String toUnit, BigDecimal value)
    {
        if (fromUnit.equals(toUnit))
        {
            return value;
        }
        TcmUnitConversion conversion = conversionMapper.selectByPair(fromUnit, toUnit);
        if (conversion == null)
        {
            throw new ServiceException("未找到从 " + fromUnit + " 到 " + toUnit + " 的换算关系");
        }
        return value.multiply(conversion.getFactor());
    }

    @Override
    public int insertTcmUnitConversion(TcmUnitConversion conversion)
    {
        return conversionMapper.insertTcmUnitConversion(conversion);
    }

    @Override
    public int updateTcmUnitConversion(TcmUnitConversion conversion)
    {
        return conversionMapper.updateTcmUnitConversion(conversion);
    }

    @Override
    public int deleteTcmUnitConversionById(Long id)
    {
        return conversionMapper.deleteTcmUnitConversionById(id);
    }
}
