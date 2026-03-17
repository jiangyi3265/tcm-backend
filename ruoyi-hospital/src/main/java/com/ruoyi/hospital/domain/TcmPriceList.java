package com.ruoyi.hospital.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 价格表对象 tcm_price_list
 *
 * @author ruoyi
 */
public class TcmPriceList extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 价格表ID */
    private String id;

    /** 名称 */
    @Excel(name = "名称")
    private String name;

    /** 生效日期 */
    @Excel(name = "生效日期")
    private String effectiveDate;

    /** 是否活跃（1=活跃, 0=停用） */
    @Excel(name = "是否活跃", readConverterExp = "1=活跃,0=停用")
    private Integer isActive;

    /** 价格项列表（JSON） */
    private String itemsJson;

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

    public void setEffectiveDate(String effectiveDate)
    {
        this.effectiveDate = effectiveDate;
    }

    public String getEffectiveDate()
    {
        return effectiveDate;
    }

    public void setIsActive(Integer isActive)
    {
        this.isActive = isActive;
    }

    public Integer getIsActive()
    {
        return isActive;
    }

    public void setItemsJson(String itemsJson)
    {
        this.itemsJson = itemsJson;
    }

    public String getItemsJson()
    {
        return itemsJson;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("name", getName())
            .append("effectiveDate", getEffectiveDate())
            .append("isActive", getIsActive())
            .append("itemsJson", getItemsJson())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
