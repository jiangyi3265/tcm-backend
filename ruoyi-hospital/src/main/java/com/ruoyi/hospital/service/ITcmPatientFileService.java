package com.ruoyi.hospital.service;

import java.util.List;
import com.ruoyi.hospital.domain.TcmPatientFile;

/**
 * 患者文件 Service接口
 *
 * @author ruoyi
 */
public interface ITcmPatientFileService
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
     * 根据ID查询文件
     *
     * @param id 文件ID
     * @return 文件信息
     */
    TcmPatientFile selectTcmPatientFileById(Long id);

    /**
     * 新增患者文件
     *
     * @param file 患者文件信息
     * @return 影响行数
     */
    int insertTcmPatientFile(TcmPatientFile file);

    /**
     * 删除患者文件
     *
     * @param id 文件ID
     * @return 影响行数
     */
    int deleteTcmPatientFileById(Long id);
}
