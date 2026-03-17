package com.ruoyi.hospital.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.hospital.domain.TcmPatientFile;
import com.ruoyi.hospital.mapper.TcmPatientFileMapper;
import com.ruoyi.hospital.service.ITcmPatientFileService;

/**
 * 患者文件 Service业务层处理
 *
 * @author ruoyi
 */
@Service
public class TcmPatientFileServiceImpl implements ITcmPatientFileService
{
    @Autowired
    private TcmPatientFileMapper tcmPatientFileMapper;

    /**
     * 根据患者ID查询文件列表
     *
     * @param patientId 患者ID
     * @return 文件集合
     */
    @Override
    public List<TcmPatientFile> selectFilesByPatientId(String patientId)
    {
        return tcmPatientFileMapper.selectFilesByPatientId(patientId);
    }

    /**
     * 根据问诊ID查询文件列表
     *
     * @param consultationId 问诊ID
     * @return 文件集合
     */
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

    /**
     * 新增患者文件
     *
     * @param file 患者文件信息
     * @return 影响行数
     */
    @Override
    public int insertTcmPatientFile(TcmPatientFile file)
    {
        return tcmPatientFileMapper.insertTcmPatientFile(file);
    }

    /**
     * 删除患者文件
     *
     * @param id 文件ID
     * @return 影响行数
     */
    @Override
    public int deleteTcmPatientFileById(Long id)
    {
        return tcmPatientFileMapper.deleteTcmPatientFileById(id);
    }
}
