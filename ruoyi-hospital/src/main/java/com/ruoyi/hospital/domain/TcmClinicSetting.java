package com.ruoyi.hospital.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 诊所设置对象 tcm_clinic_setting
 *
 * @author ruoyi
 */
public class TcmClinicSetting extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 设置键 */
    @Excel(name = "设置键")
    private String settingKey;

    /** 设置值（JSON） */
    @Excel(name = "设置值")
    private String settingValue;

    public void setSettingKey(String settingKey)
    {
        this.settingKey = settingKey;
    }

    public String getSettingKey()
    {
        return settingKey;
    }

    public void setSettingValue(String settingValue)
    {
        this.settingValue = settingValue;
    }

    public String getSettingValue()
    {
        return settingValue;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("settingKey", getSettingKey())
            .append("settingValue", getSettingValue())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
