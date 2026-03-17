package com.ruoyi.hospital.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 问诊修改记录对象 tcm_consultation_mod
 *
 * @author ruoyi
 */
public class TcmConsultationMod extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 问诊编号 */
    @Excel(name = "问诊编号")
    private String consultationId;

    /** 修改日期 */
    @Excel(name = "修改日期")
    private String modDate;

    /** 修改类型 */
    @Excel(name = "修改类型")
    private String modType;

    /** 操作 */
    @Excel(name = "操作")
    private String action;

    /** 用户ID */
    private String userId;

    /** 版本号 */
    private Integer version;

    /** 变更内容（JSON） */
    private String changes;

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
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

    public void setModDate(String modDate)
    {
        this.modDate = modDate;
    }

    public String getModDate()
    {
        return modDate;
    }

    public void setModType(String modType)
    {
        this.modType = modType;
    }

    public String getModType()
    {
        return modType;
    }

    public void setAction(String action)
    {
        this.action = action;
    }

    public String getAction()
    {
        return action;
    }

    public void setUserId(String userId)
    {
        this.userId = userId;
    }

    public String getUserId()
    {
        return userId;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
    }

    public Integer getVersion()
    {
        return version;
    }

    public void setChanges(String changes)
    {
        this.changes = changes;
    }

    public String getChanges()
    {
        return changes;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("consultationId", getConsultationId())
            .append("modDate", getModDate())
            .append("modType", getModType())
            .append("action", getAction())
            .append("userId", getUserId())
            .append("version", getVersion())
            .append("changes", getChanges())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
