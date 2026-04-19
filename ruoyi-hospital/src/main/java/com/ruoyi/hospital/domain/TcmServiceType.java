package com.ruoyi.hospital.domain;

import java.math.BigDecimal;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 服务类型对象 tcm_service_type
 *
 * @author ruoyi
 */
public class TcmServiceType extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 服务标识 */
    @Excel(name = "服务标识")
    private String serviceKey;

    /** 标签 */
    @Excel(name = "标签")
    private String label;

    /** 时长（分钟） */
    @Excel(name = "时长(分钟)")
    private Integer duration;

    /** 医师时间（分钟或overlap标识） */
    @Excel(name = "医师时间")
    private String practitionerTime;

    /** 是否需要诊室（1=是, 0=否） */
    @Excel(name = "是否需要诊室", readConverterExp = "1=是,0=否")
    private Integer roomRequired;

    /** 默认价格 */
    @Excel(name = "默认价格")
    private BigDecimal defaultPrice;

    /** 所需标签（如 acupuncture, tuina, herbs） */
    @Excel(name = "所需标签")
    private String requiredTag;

    /** 是否在公共预订页面显示（1=是, 0=否） */
    @Excel(name = "公共页面显示", readConverterExp = "1=是,0=否")
    private Integer publicVisible;

    /** 是否计税（1=是, 0=否） */
    @Excel(name = "计税", readConverterExp = "1=是,0=否")
    private Integer taxable;

    /** 是否在Pricing下拉显示（1=是, 0=否） */
    @Excel(name = "Pricing显示", readConverterExp = "1=是,0=否")
    private Integer pricingVisible;

    public void setServiceKey(String serviceKey)
    {
        this.serviceKey = serviceKey;
    }

    public String getServiceKey()
    {
        return serviceKey;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public String getLabel()
    {
        return label;
    }

    public void setDuration(Integer duration)
    {
        this.duration = duration;
    }

    public Integer getDuration()
    {
        return duration;
    }

    public void setPractitionerTime(String practitionerTime)
    {
        this.practitionerTime = practitionerTime;
    }

    public String getPractitionerTime()
    {
        return practitionerTime;
    }

    public void setRoomRequired(Integer roomRequired)
    {
        this.roomRequired = roomRequired;
    }

    public Integer getRoomRequired()
    {
        return roomRequired;
    }

    public void setDefaultPrice(BigDecimal defaultPrice)
    {
        this.defaultPrice = defaultPrice;
    }

    public BigDecimal getDefaultPrice()
    {
        return defaultPrice;
    }

    public void setRequiredTag(String requiredTag)
    {
        this.requiredTag = requiredTag;
    }

    public String getRequiredTag()
    {
        return requiredTag;
    }

    public void setPublicVisible(Integer publicVisible)
    {
        this.publicVisible = publicVisible;
    }

    public Integer getPublicVisible()
    {
        return publicVisible;
    }

    public void setTaxable(Integer taxable)
    {
        this.taxable = taxable;
    }

    public Integer getTaxable()
    {
        return taxable;
    }

    public void setPricingVisible(Integer pricingVisible)
    {
        this.pricingVisible = pricingVisible;
    }

    public Integer getPricingVisible()
    {
        return pricingVisible;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("serviceKey", getServiceKey())
            .append("label", getLabel())
            .append("duration", getDuration())
            .append("practitionerTime", getPractitionerTime())
            .append("roomRequired", getRoomRequired())
            .append("defaultPrice", getDefaultPrice())
            .append("requiredTag", getRequiredTag())
            .append("publicVisible", getPublicVisible())
            .append("taxable", getTaxable())
            .append("pricingVisible", getPricingVisible())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
