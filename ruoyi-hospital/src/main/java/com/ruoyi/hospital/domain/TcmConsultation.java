package com.ruoyi.hospital.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 问诊记录对象 tcm_consultation
 *
 * @author ruoyi
 */
public class TcmConsultation extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private String id;

    /** 问诊编号 */
    @Excel(name = "问诊编号")
    private String consultationId;

    /** 患者ID */
    @Excel(name = "患者ID")
    private String patientId;

    /** 医师ID */
    @Excel(name = "医师ID")
    private String practitionerId;

    /** 问诊日期 */
    @Excel(name = "问诊日期")
    private String consultDate;

    /** 状态（draft, completed, paid） */
    @Excel(name = "状态")
    private String status;

    /** 分院ID */
    private String branchId;

    /** 锁定时间 */
    private String lockedAt;

    /** 版本号 */
    private Integer version;

    /** 删除时间 */
    private String deletedAt;

    /** 问诊详情（JSON） */
    private String payload;

    public void setId(String id)
    {
        this.id = id;
    }

    public String getId()
    {
        return id;
    }

    public void setConsultationId(String consultationId)
    {
        this.consultationId = consultationId;
    }

    public String getConsultationId()
    {
        return consultationId;
    }

    public void setPatientId(String patientId)
    {
        this.patientId = patientId;
    }

    public String getPatientId()
    {
        return patientId;
    }

    public void setPractitionerId(String practitionerId)
    {
        this.practitionerId = practitionerId;
    }

    public String getPractitionerId()
    {
        return practitionerId;
    }

    public void setConsultDate(String consultDate)
    {
        this.consultDate = consultDate;
    }

    public String getConsultDate()
    {
        return consultDate;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setBranchId(String branchId)
    {
        this.branchId = branchId;
    }

    public String getBranchId()
    {
        return branchId;
    }

    public void setLockedAt(String lockedAt)
    {
        this.lockedAt = lockedAt;
    }

    public String getLockedAt()
    {
        return lockedAt;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
    }

    public Integer getVersion()
    {
        return version;
    }

    public void setDeletedAt(String deletedAt)
    {
        this.deletedAt = deletedAt;
    }

    public String getDeletedAt()
    {
        return deletedAt;
    }

    public void setPayload(String payload)
    {
        this.payload = payload;
    }

    public String getPayload()
    {
        return payload;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("consultationId", getConsultationId())
            .append("patientId", getPatientId())
            .append("practitionerId", getPractitionerId())
            .append("consultDate", getConsultDate())
            .append("status", getStatus())
            .append("branchId", getBranchId())
            .append("lockedAt", getLockedAt())
            .append("version", getVersion())
            .append("deletedAt", getDeletedAt())
            .append("payload", getPayload())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
