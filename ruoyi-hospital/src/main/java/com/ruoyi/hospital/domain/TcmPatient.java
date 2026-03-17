package com.ruoyi.hospital.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 患者信息对象 tcm_patient
 *
 * @author ruoyi
 */
public class TcmPatient extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 患者ID */
    private String id;

    /** 姓名 */
    @Excel(name = "姓名")
    private String name;

    /** 名 */
    @Excel(name = "名")
    private String firstName;

    /** 姓 */
    @Excel(name = "姓")
    private String lastName;

    /** 邮箱 */
    @Excel(name = "邮箱")
    private String email;

    /** 电话 */
    @Excel(name = "电话")
    private String phone;

    /** 医师ID */
    private String practitionerId;

    /** 是否活跃（1=活跃, 0=停用） */
    @Excel(name = "是否活跃", readConverterExp = "1=活跃,0=停用")
    private Integer isActive;

    /** 是否签署同意书（0或1） */
    private Integer consentSigned;

    /** 同意书签署时间 */
    private String consentSignedAt;

    /** 同意书签署令牌 */
    private String consentToken;

    /** 令牌过期时间 */
    private String consentTokenExpires;

    /** 合并到 */
    private String mergedInto;

    /** 删除时间 */
    private String deletedAt;

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

    public void setName(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

    public void setFirstName(String firstName)
    {
        this.firstName = firstName;
    }

    public String getFirstName()
    {
        return firstName;
    }

    public void setLastName(String lastName)
    {
        this.lastName = lastName;
    }

    public String getLastName()
    {
        return lastName;
    }

    public void setEmail(String email)
    {
        this.email = email;
    }

    public String getEmail()
    {
        return email;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public String getPhone()
    {
        return phone;
    }

    public void setPractitionerId(String practitionerId)
    {
        this.practitionerId = practitionerId;
    }

    public String getPractitionerId()
    {
        return practitionerId;
    }

    public void setIsActive(Integer isActive)
    {
        this.isActive = isActive;
    }

    public Integer getIsActive()
    {
        return isActive;
    }

    public void setConsentSigned(Integer consentSigned)
    {
        this.consentSigned = consentSigned;
    }

    public Integer getConsentSigned()
    {
        return consentSigned;
    }

    public void setConsentSignedAt(String consentSignedAt)
    {
        this.consentSignedAt = consentSignedAt;
    }

    public String getConsentSignedAt()
    {
        return consentSignedAt;
    }

    public void setConsentToken(String consentToken)
    {
        this.consentToken = consentToken;
    }

    public String getConsentToken()
    {
        return consentToken;
    }

    public void setConsentTokenExpires(String consentTokenExpires)
    {
        this.consentTokenExpires = consentTokenExpires;
    }

    public String getConsentTokenExpires()
    {
        return consentTokenExpires;
    }

    public void setMergedInto(String mergedInto)
    {
        this.mergedInto = mergedInto;
    }

    public String getMergedInto()
    {
        return mergedInto;
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
            .append("name", getName())
            .append("firstName", getFirstName())
            .append("lastName", getLastName())
            .append("email", getEmail())
            .append("phone", getPhone())
            .append("practitionerId", getPractitionerId())
            .append("isActive", getIsActive())
            .append("consentSigned", getConsentSigned())
            .append("consentSignedAt", getConsentSignedAt())
            .append("consentToken", getConsentToken())
            .append("consentTokenExpires", getConsentTokenExpires())
            .append("mergedInto", getMergedInto())
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
