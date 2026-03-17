package com.ruoyi.hospital.service;

import java.util.List;
import com.ruoyi.hospital.domain.TcmTreatmentTemplate;

public interface ITcmTreatmentTemplateService
{
    List<TcmTreatmentTemplate> selectTcmTreatmentTemplateList(TcmTreatmentTemplate template);
    TcmTreatmentTemplate selectTcmTreatmentTemplateById(String id);
    int insertTcmTreatmentTemplate(TcmTreatmentTemplate template);
    int updateTcmTreatmentTemplate(TcmTreatmentTemplate template);
    TcmTreatmentTemplate softDeleteTcmTreatmentTemplate(String id);
    TcmTreatmentTemplate restoreTcmTreatmentTemplate(String id);
    int hardDeleteTcmTreatmentTemplate(String id);
}
