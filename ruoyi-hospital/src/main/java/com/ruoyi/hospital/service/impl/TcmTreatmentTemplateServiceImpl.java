package com.ruoyi.hospital.service.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.hospital.domain.TcmTreatmentTemplate;
import com.ruoyi.hospital.mapper.TcmTreatmentTemplateMapper;
import com.ruoyi.hospital.service.ITcmTreatmentTemplateService;

@Service
public class TcmTreatmentTemplateServiceImpl implements ITcmTreatmentTemplateService
{
    @Autowired
    private TcmTreatmentTemplateMapper templateMapper;
    private static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    @Override
    public List<TcmTreatmentTemplate> selectTcmTreatmentTemplateList(TcmTreatmentTemplate t) {
        return templateMapper.selectTcmTreatmentTemplateList(t);
    }
    @Override
    public TcmTreatmentTemplate selectTcmTreatmentTemplateById(String id) {
        return templateMapper.selectTcmTreatmentTemplateById(id);
    }
    @Override
    public int insertTcmTreatmentTemplate(TcmTreatmentTemplate t) {
        if (t.getId() == null || t.getId().isEmpty())
            t.setId(java.util.UUID.randomUUID().toString());
        t.setCreateTime(DateUtils.getNowDate());
        return templateMapper.insertTcmTreatmentTemplate(t);
    }
    @Override
    public int updateTcmTreatmentTemplate(TcmTreatmentTemplate t) {
        return templateMapper.updateTcmTreatmentTemplate(t);
    }
    @Override
    public TcmTreatmentTemplate softDeleteTcmTreatmentTemplate(String id) {
        TcmTreatmentTemplate t = templateMapper.selectTcmTreatmentTemplateById(id);
        if (t == null) throw new ServiceException("模板不存在");
        t.setDeletedAt(new SimpleDateFormat(DATETIME_FORMAT).format(new Date()));
        t.setIsActive(0);
        templateMapper.updateTcmTreatmentTemplate(t);
        return t;
    }
    @Override
    public TcmTreatmentTemplate restoreTcmTreatmentTemplate(String id) {
        TcmTreatmentTemplate t = templateMapper.selectTcmTreatmentTemplateById(id);
        if (t == null) throw new ServiceException("模板不存在");
        t.setDeletedAt(null); t.setIsActive(1);
        templateMapper.updateTcmTreatmentTemplate(t);
        return t;
    }
    @Override
    public int hardDeleteTcmTreatmentTemplate(String id) {
        TcmTreatmentTemplate t = templateMapper.selectTcmTreatmentTemplateById(id);
        if (t == null) throw new ServiceException("模板不存在");
        if (t.getDeletedAt() == null || t.getDeletedAt().isEmpty())
            throw new ServiceException("该记录未被软删除");
        try {
            Date d = new SimpleDateFormat(DATETIME_FORMAT).parse(t.getDeletedAt());
            if (System.currentTimeMillis() - d.getTime() < 90L*24*60*60*1000)
                throw new ServiceException("删除不满3个月");
        } catch (ServiceException e) { throw e; }
          catch (Exception e) { throw new ServiceException("时间解析错误"); }
        return templateMapper.deleteTcmTreatmentTemplateById(id);
    }
}
