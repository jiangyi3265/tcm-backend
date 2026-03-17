package com.ruoyi.hospital.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 分院信息对象 tcm_branch
 *
 * @author ruoyi
 */
public class TcmBranch extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 分院ID */
    private String id;

    /** 名称 */
    @Excel(name = "名称")
    private String name;

    /** 编码 */
    @Excel(name = "编码")
    private String code;

    /** 地址 */
    @Excel(name = "地址")
    private String address;

    /** 电话 */
    @Excel(name = "电话")
    private String phone;

    /** 邮箱 */
    @Excel(name = "邮箱")
    private String email;

    /** 管理员ID */
    private String managerId;

    /** 是否活跃（1=活跃, 0=停用） */
    @Excel(name = "是否活跃", readConverterExp = "1=活跃,0=停用")
    private Integer isActive;

    /** 是否主院（1=是, 0=否） */
    @Excel(name = "是否主院", readConverterExp = "1=是,0=否")
    private Integer isMain;

    /** 诊室ID列表（JSON数组） */
    private String roomIds;

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

    public void setCode(String code)
    {
        this.code = code;
    }

    public String getCode()
    {
        return code;
    }

    public void setAddress(String address)
    {
        this.address = address;
    }

    public String getAddress()
    {
        return address;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public String getPhone()
    {
        return phone;
    }

    public void setEmail(String email)
    {
        this.email = email;
    }

    public String getEmail()
    {
        return email;
    }

    public void setManagerId(String managerId)
    {
        this.managerId = managerId;
    }

    public String getManagerId()
    {
        return managerId;
    }

    public void setIsActive(Integer isActive)
    {
        this.isActive = isActive;
    }

    public Integer getIsActive()
    {
        return isActive;
    }

    public void setIsMain(Integer isMain)
    {
        this.isMain = isMain;
    }

    public Integer getIsMain()
    {
        return isMain;
    }

    public void setRoomIds(String roomIds)
    {
        this.roomIds = roomIds;
    }

    public String getRoomIds()
    {
        return roomIds;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("name", getName())
            .append("code", getCode())
            .append("address", getAddress())
            .append("phone", getPhone())
            .append("email", getEmail())
            .append("managerId", getManagerId())
            .append("isActive", getIsActive())
            .append("isMain", getIsMain())
            .append("roomIds", getRoomIds())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
