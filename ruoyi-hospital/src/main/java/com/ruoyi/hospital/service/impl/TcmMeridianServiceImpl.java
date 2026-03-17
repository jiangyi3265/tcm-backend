package com.ruoyi.hospital.service.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.hospital.domain.TcmMeridian;
import com.ruoyi.hospital.mapper.TcmMeridianMapper;
import com.ruoyi.hospital.service.ITcmMeridianService;

@Service
public class TcmMeridianServiceImpl implements ITcmMeridianService
{
    @Autowired
    private TcmMeridianMapper meridianMapper;
    private static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    @Override
    public List<TcmMeridian> selectTcmMeridianList(TcmMeridian meridian) {
        return meridianMapper.selectTcmMeridianList(meridian);
    }
    @Override
    public TcmMeridian selectTcmMeridianById(String id) {
        return meridianMapper.selectTcmMeridianById(id);
    }
    @Override
    public int insertTcmMeridian(TcmMeridian meridian) {
        if (meridian.getId() == null || meridian.getId().isEmpty())
            meridian.setId(java.util.UUID.randomUUID().toString());
        meridian.setCreateTime(DateUtils.getNowDate());
        return meridianMapper.insertTcmMeridian(meridian);
    }
    @Override
    public int updateTcmMeridian(TcmMeridian meridian) {
        return meridianMapper.updateTcmMeridian(meridian);
    }
    @Override
    public TcmMeridian softDeleteTcmMeridian(String id) {
        TcmMeridian m = meridianMapper.selectTcmMeridianById(id);
        if (m == null) throw new ServiceException("经络不存在");
        m.setDeletedAt(new SimpleDateFormat(DATETIME_FORMAT).format(new Date()));
        m.setIsActive(0);
        meridianMapper.updateTcmMeridian(m);
        return m;
    }
    @Override
    public TcmMeridian restoreTcmMeridian(String id) {
        TcmMeridian m = meridianMapper.selectTcmMeridianById(id);
        if (m == null) throw new ServiceException("经络不存在");
        m.setDeletedAt(null); m.setIsActive(1);
        meridianMapper.updateTcmMeridian(m);
        return m;
    }
    @Override
    public int hardDeleteTcmMeridian(String id) {
        TcmMeridian m = meridianMapper.selectTcmMeridianById(id);
        if (m == null) throw new ServiceException("经络不存在");
        if (m.getDeletedAt() == null || m.getDeletedAt().isEmpty())
            throw new ServiceException("该记录未被软删除");
        try {
            Date d = new SimpleDateFormat(DATETIME_FORMAT).parse(m.getDeletedAt());
            if (System.currentTimeMillis() - d.getTime() < 90L*24*60*60*1000)
                throw new ServiceException("删除不满3个月");
        } catch (ServiceException e) { throw e; }
          catch (Exception e) { throw new ServiceException("时间解析错误"); }
        return meridianMapper.deleteTcmMeridianById(id);
    }
}
