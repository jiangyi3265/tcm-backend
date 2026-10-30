package com.ruoyi.hospital.service;

import java.util.List;
import com.ruoyi.hospital.domain.TcmPatientFile;

public interface ITcmPatientFileService
{
    List<TcmPatientFile> selectFilesByPatientId(String patientId);

    List<TcmPatientFile> selectDeletedFilesByPatientId(String patientId);

    List<TcmPatientFile> selectFilesByConsultationId(String consultationId);

    TcmPatientFile selectTcmPatientFileById(Long id);

    TcmPatientFile selectTcmPatientFileByPath(String filePath);

    int insertTcmPatientFile(TcmPatientFile file);

    int updateTcmPatientFile(TcmPatientFile file);

    int reassignDeletedFiles(String fromPatientId, String toPatientId);

    int deleteTcmPatientFileById(Long id);
}
