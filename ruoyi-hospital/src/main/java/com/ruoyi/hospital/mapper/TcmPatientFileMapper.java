package com.ruoyi.hospital.mapper;

import java.util.List;
import com.ruoyi.hospital.domain.TcmPatientFile;

/**
 * 患者文件Mapper接口
 *
 * @author ruoyi
 */
public interface TcmPatientFileMapper
{
    /**
     * 根据患者ID查询文件列表
     *
     * @param patientId 患者ID
     * @return 文件集合
     */
    List<TcmPatientFile> selectFilesByPatientId(String patientId);

    /**
     * 根据问诊ID查询文件列表
     *
     * @param consultationId 问诊ID
     * @return 文件集合
     */
    List<TcmPatientFile> selectFilesByConsultationId(String consultationId);

    /**
     * 查询患者文件
     *
     * @param id 文件主键
     * @return 患者文件
     */
    TcmPatientFile selectTcmPatientFileById(Long id);

    /**
     * 新增患者文件
     *
     * @param file 患者文件
     * @return 结果
     */
    int insertTcmPatientFile(TcmPatientFile file);

    /**
     * 删除患者文件
     *
     * @param id 文件主键
     * @return 结果
     */
    int deleteTcmPatientFileById(Long id);
}
