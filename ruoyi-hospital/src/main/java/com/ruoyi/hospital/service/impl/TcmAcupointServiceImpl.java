package com.ruoyi.hospital.service.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.hospital.domain.TcmAcupoint;
import com.ruoyi.hospital.mapper.TcmAcupointMapper;
import com.ruoyi.hospital.service.ITcmAcupointService;

/**
 * 针灸穴位 Service业务层处理
 *
 * @author ruoyi
 */
@Service
public class TcmAcupointServiceImpl implements ITcmAcupointService
{
    @Autowired
    private TcmAcupointMapper acupointMapper;

    private static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    @Override
    public List<TcmAcupoint> selectTcmAcupointList(TcmAcupoint acupoint)
    {
        return acupointMapper.selectTcmAcupointList(acupoint);
    }

    @Override
    public TcmAcupoint selectTcmAcupointById(String id)
    {
        return acupointMapper.selectTcmAcupointById(id);
    }

    @Override
    public int insertTcmAcupoint(TcmAcupoint acupoint)
    {
        if (acupoint.getId() == null || acupoint.getId().isEmpty())
        {
            acupoint.setId(java.util.UUID.randomUUID().toString());
        }
        acupoint.setCreateTime(DateUtils.getNowDate());
        return acupointMapper.insertTcmAcupoint(acupoint);
    }

    @Override
    public int updateTcmAcupoint(TcmAcupoint acupoint)
    {
        return acupointMapper.updateTcmAcupoint(acupoint);
    }

    @Override
    public TcmAcupoint softDeleteTcmAcupoint(String id)
    {
        TcmAcupoint acupoint = acupointMapper.selectTcmAcupointById(id);
        if (acupoint == null)
        {
            throw new ServiceException("穴位不存在");
        }
        acupoint.setDeletedAt(new SimpleDateFormat(DATETIME_FORMAT).format(new Date()));
        acupoint.setIsActive(0);
        acupointMapper.updateTcmAcupoint(acupoint);
        return acupoint;
    }

    @Override
    public TcmAcupoint restoreTcmAcupoint(String id)
    {
        TcmAcupoint acupoint = acupointMapper.selectTcmAcupointById(id);
        if (acupoint == null)
        {
            throw new ServiceException("穴位不存在");
        }
        acupoint.setDeletedAt(null);
        acupoint.setIsActive(1);
        acupointMapper.updateTcmAcupoint(acupoint);
        return acupoint;
    }

    @Override
    public int hardDeleteTcmAcupoint(String id)
    {
        TcmAcupoint acupoint = acupointMapper.selectTcmAcupointById(id);
        if (acupoint == null)
        {
            throw new ServiceException("穴位不存在");
        }
        if (acupoint.getDeletedAt() == null || acupoint.getDeletedAt().isEmpty())
        {
            throw new ServiceException("该记录未被软删除，无法物理删除");
        }
        try
        {
            SimpleDateFormat sdf = new SimpleDateFormat(DATETIME_FORMAT);
            Date deletedDate = sdf.parse(acupoint.getDeletedAt());
            long threeMonthsMs = 90L * 24 * 60 * 60 * 1000;
            if (System.currentTimeMillis() - deletedDate.getTime() < threeMonthsMs)
            {
                throw new ServiceException("该记录删除不满3个月，无法物理删除");
            }
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("删除时间格式解析错误"); }
        return acupointMapper.deleteTcmAcupointById(id);
    }
}
