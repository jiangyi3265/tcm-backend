package com.ruoyi.hospital.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.hospital.domain.TcmPatientFile;
import com.ruoyi.hospital.mapper.TcmPatientFileMapper;
import com.ruoyi.hospital.service.ITcmPatientFileService;

@Service
public class TcmPatientFileServiceImpl implements ITcmPatientFileService
{
    @Autowired
    private TcmPatientFileMapper tcmPatientFileMapper;

    @Override
    public List<TcmPatientFile> selectFilesByPatientId(String patientId)
    {
        return tcmPatientFileMapper.selectFilesByPatientId(patientId);
    }

    @Override
    public List<TcmPatientFile> selectFilesByConsultationId(String consultationId)
    {
        return tcmPatientFileMapper.selectFilesByConsultationId(consultationId);
    }

    @Override
    public TcmPatientFile selectTcmPatientFileById(Long id)
    {
        return tcmPatientFileMapper.selectTcmPatientFileById(id);
    }

    @Override
    public TcmPatientFile selectTcmPatientFileByPath(String filePath)
    {
        return tcmPatientFileMapper.selectTcmPatientFileByPath(filePath);
    }

    @Override
    public int insertTcmPatientFile(TcmPatientFile file)
    {
        return tcmPatientFileMapper.insertTcmPatientFile(file);
    }

    @Override
    public int updateTcmPatientFile(TcmPatientFile file)
    {
        return tcmPatientFileMapper.updateTcmPatientFile(file);
    }

    @Override
    public int deleteTcmPatientFileById(Long id)
    {
        return tcmPatientFileMapper.deleteTcmPatientFileById(id);
    }
}
