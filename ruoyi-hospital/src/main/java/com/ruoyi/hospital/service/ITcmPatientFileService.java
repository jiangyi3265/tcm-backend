package com.ruoyi.hospital.service;

import java.util.List;
import com.ruoyi.hospital.domain.TcmPatientFile;

public interface ITcmPatientFileService
{
    List<TcmPatientFile> selectFilesByPatientId(String patientId);

    List<TcmPatientFile> selectFilesByConsultationId(String consultationId);

    TcmPatientFile selectTcmPatientFileById(Long id);

    TcmPatientFile selectTcmPatientFileByPath(String filePath);

    int insertTcmPatientFile(TcmPatientFile file);

    int updateTcmPatientFile(TcmPatientFile file);

    int deleteTcmPatientFileById(Long id);
}
