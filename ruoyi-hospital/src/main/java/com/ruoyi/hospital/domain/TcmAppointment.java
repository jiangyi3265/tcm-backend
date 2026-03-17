package com.ruoyi.hospital.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 预约信息对象 tcm_appointment
 *
 * @author ruoyi
 */
public class TcmAppointment extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 预约ID */
    private String id;

    /** 患者ID */
    @Excel(name = "患者ID")
    private String patientId;

    /** 医师ID */
    @Excel(name = "医师ID")
    private String practitionerId;

    /** 诊室ID */
    private String roomId;

    /** 服务类型 */
    @Excel(name = "服务类型")
    private String serviceType;

    /** 开始时间 */
    @Excel(name = "开始时间")
    private String startTime;

    /** 结束时间 */
    @Excel(name = "结束时间")
    private String endTime;

    /** 状态（booked, confirmed, completed, cancelled） */
    @Excel(name = "状态")
    private String status;

    /** 分院ID */
    private String branchId;

    /** 问诊表单令牌 */
    private String intakeToken;

    /** 表单是否已提交 */
    private Integer intakeSubmitted;

    /** 扩展信息（JSON） */
    private String payload;

    public void setId(String id)
    {
        this.id = id;
    }

    public String getId()
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

    public void setPractitionerId(String practitionerId)
    {
        this.practitionerId = practitionerId;
    }

    public String getPractitionerId()
    {
        return practitionerId;
    }

    public void setRoomId(String roomId)
    {
        this.roomId = roomId;
    }

    public String getRoomId()
    {
        return roomId;
    }

    public void setServiceType(String serviceType)
    {
        this.serviceType = serviceType;
    }

    public String getServiceType()
    {
        return serviceType;
    }

    public void setStartTime(String startTime)
    {
        this.startTime = startTime;
    }

    public String getStartTime()
    {
        return startTime;
    }

    public void setEndTime(String endTime)
    {
        this.endTime = endTime;
    }

    public String getEndTime()
    {
        return endTime;
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

    public void setIntakeToken(String intakeToken)
    {
        this.intakeToken = intakeToken;
    }

    public String getIntakeToken()
    {
        return intakeToken;
    }

    public void setIntakeSubmitted(Integer intakeSubmitted)
    {
        this.intakeSubmitted = intakeSubmitted;
    }

    public Integer getIntakeSubmitted()
    {
        return intakeSubmitted;
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
            .append("patientId", getPatientId())
            .append("practitionerId", getPractitionerId())
            .append("roomId", getRoomId())
            .append("serviceType", getServiceType())
            .append("startTime", getStartTime())
            .append("endTime", getEndTime())
            .append("status", getStatus())
            .append("branchId", getBranchId())
            .append("intakeToken", getIntakeToken())
            .append("intakeSubmitted", getIntakeSubmitted())
            .append("payload", getPayload())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
