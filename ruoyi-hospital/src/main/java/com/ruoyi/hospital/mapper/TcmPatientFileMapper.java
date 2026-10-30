package com.ruoyi.hospital.mapper;

import java.util.List;
import com.ruoyi.hospital.domain.TcmPatientFile;

public interface TcmPatientFileMapper
{
    List<TcmPatientFile> selectAllTcmPatientFiles();

    List<TcmPatientFile> selectFilesByPatientId(String patientId);

    List<TcmPatientFile> selectDeletedFilesByPatientId(String patientId);

    List<TcmPatientFile> selectFilesByConsultationId(String consultationId);

    TcmPatientFile selectTcmPatientFileById(Long id);

    TcmPatientFile selectTcmPatientFileByPath(String filePath);

    int insertTcmPatientFile(TcmPatientFile file);

    int updateTcmPatientFile(TcmPatientFile file);

    int deleteTcmPatientFileById(Long id);

    int markTcmPatientFileDeleted(Long id);

    int reassignDeletedFiles(@org.apache.ibatis.annotations.Param("fromPatientId") String fromPatientId,
            @org.apache.ibatis.annotations.Param("toPatientId") String toPatientId);
}
