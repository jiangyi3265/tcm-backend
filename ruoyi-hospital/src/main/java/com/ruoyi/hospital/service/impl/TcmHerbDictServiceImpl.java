package com.ruoyi.hospital.service.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.hospital.domain.TcmHerbDict;
import com.ruoyi.hospital.mapper.TcmHerbDictMapper;
import com.ruoyi.hospital.service.ITcmHerbDictService;

@Service
public class TcmHerbDictServiceImpl implements ITcmHerbDictService
{
    @Autowired
    private TcmHerbDictMapper herbDictMapper;
    private static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    @Override
    public List<TcmHerbDict> selectTcmHerbDictList(TcmHerbDict herbDict) {
        return herbDictMapper.selectTcmHerbDictList(herbDict);
    }
    @Override
    public TcmHerbDict selectTcmHerbDictById(String id) {
        return herbDictMapper.selectTcmHerbDictById(id);
    }
    @Override
    public int insertTcmHerbDict(TcmHerbDict herbDict) {
        if (herbDict.getId() == null || herbDict.getId().isEmpty()) {
            herbDict.setId(java.util.UUID.randomUUID().toString());
        }
        herbDict.setCreateTime(DateUtils.getNowDate());
        return herbDictMapper.insertTcmHerbDict(herbDict);
    }
    @Override
    public int updateTcmHerbDict(TcmHerbDict herbDict) {
        return herbDictMapper.updateTcmHerbDict(herbDict);
    }
    @Override
    public TcmHerbDict softDeleteTcmHerbDict(String id) {
        TcmHerbDict h = herbDictMapper.selectTcmHerbDictById(id);
        if (h == null) throw new ServiceException("药材不存在");
        h.setDeletedAt(new SimpleDateFormat(DATETIME_FORMAT).format(new Date()));
        h.setIsActive(0);
        herbDictMapper.updateTcmHerbDict(h);
        return h;
    }
    @Override
    public TcmHerbDict restoreTcmHerbDict(String id) {
        TcmHerbDict h = herbDictMapper.selectTcmHerbDictById(id);
        if (h == null) throw new ServiceException("药材不存在");
        h.setDeletedAt(null);
        h.setIsActive(1);
        herbDictMapper.updateTcmHerbDict(h);
        return h;
    }
    @Override
    public int hardDeleteTcmHerbDict(String id) {
        TcmHerbDict h = herbDictMapper.selectTcmHerbDictById(id);
        if (h == null) throw new ServiceException("药材不存在");
        if (h.getDeletedAt() == null || h.getDeletedAt().isEmpty())
            throw new ServiceException("该记录未被软删除，无法物理删除");
        try {
            Date deletedDate = new SimpleDateFormat(DATETIME_FORMAT).parse(h.getDeletedAt());
            long threeMonthsMs = 90L * 24 * 60 * 60 * 1000;
            if (System.currentTimeMillis() - deletedDate.getTime() < threeMonthsMs)
                throw new ServiceException("该记录删除不满3个月，无法物理删除");
        } catch (ServiceException e) { throw e; }
          catch (Exception e) { throw new ServiceException("删除时间格式解析错误"); }
        return herbDictMapper.deleteTcmHerbDictById(id);
    }
}
