package com.ruoyi.hospital.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 患者文件对象 tcm_patient_file
 *
 * @author ruoyi
 */
public class TcmPatientFile extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 患者ID */
    @Excel(name = "患者ID")
    private String patientId;

    /** 问诊编号 */
    @Excel(name = "问诊编号")
    private String consultationId;

    /** 文件类型 */
    @Excel(name = "文件类型")
    private String fileType;

    /** 文件名 */
    @Excel(name = "文件名")
    private String fileName;

    /** 文件路径 */
    @Excel(name = "文件路径")
    private String filePath;

    /** 上传时间 */
    @Excel(name = "上传时间")
    private String uploadTime;

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }

    public void setPatientId(String patientId)
    {
        this.patientId = patientId;
    }

    public String getPatientId()
    {
        return patientId;
    }

    public void setConsultationId(String consultationId)
    {
        this.consultationId = consultationId;
    }

    public String getConsultationId()
    {
        return consultationId;
    }

    public void setFileType(String fileType)
    {
        this.fileType = fileType;
    }

    public String getFileType()
    {
        return fileType;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFilePath(String filePath)
    {
        this.filePath = filePath;
    }

    public String getFilePath()
    {
        return filePath;
    }

    public void setUploadTime(String uploadTime)
    {
        this.uploadTime = uploadTime;
    }

    public String getUploadTime()
    {
        return uploadTime;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("patientId", getPatientId())
            .append("consultationId", getConsultationId())
            .append("fileType", getFileType())
            .append("fileName", getFileName())
            .append("filePath", getFilePath())
            .append("uploadTime", getUploadTime())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
