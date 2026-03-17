package com.ruoyi.hospital.mapper;

import java.util.List;
import com.ruoyi.hospital.domain.TcmTreatmentTemplate;

public interface TcmTreatmentTemplateMapper
{
    List<TcmTreatmentTemplate> selectTcmTreatmentTemplateList(TcmTreatmentTemplate template);
    TcmTreatmentTemplate selectTcmTreatmentTemplateById(String id);
    int insertTcmTreatmentTemplate(TcmTreatmentTemplate template);
    int updateTcmTreatmentTemplate(TcmTreatmentTemplate template);
    int deleteTcmTreatmentTemplateById(String id);
}
