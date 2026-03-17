package com.ruoyi.hospital.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 诊室信息对象 tcm_room
 *
 * @author ruoyi
 */
public class TcmRoom extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 诊室ID */
    private String id;

    /** 名称 */
    @Excel(name = "名称")
    private String name;

    /** 分院ID */
    @Excel(name = "分院ID")
    private String branchId;

    /** 是否活跃（1=活跃, 0=停用） */
    @Excel(name = "是否活跃", readConverterExp = "1=活跃,0=停用")
    private Integer isActive;

    public void setId(String id)
    {
        this.id = id;
    }

    public String getId()
    {
        return id;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

    public void setBranchId(String branchId)
    {
        this.branchId = branchId;
    }

    public String getBranchId()
    {
        return branchId;
    }

    public void setIsActive(Integer isActive)
    {
        this.isActive = isActive;
    }

    public Integer getIsActive()
    {
        return isActive;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("name", getName())
            .append("branchId", getBranchId())
            .append("isActive", getIsActive())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
