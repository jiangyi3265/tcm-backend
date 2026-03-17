package com.ruoyi.hospital.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 供应商对象 tcm_supplier
 *
 * @author ruoyi
 */
public class TcmSupplier extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 供应商ID */
    private String id;

    /** 供应商名称 */
    @Excel(name = "供应商名称")
    private String name;

    /** 联系人 */
    @Excel(name = "联系人")
    private String contactPerson;

    /** 电话 */
    @Excel(name = "电话")
    private String phone;

    /** 邮箱 */
    @Excel(name = "邮箱")
    private String email;

    /** 地址 */
    @Excel(name = "地址")
    private String address;

    /** 备注 */
    private String notes;

    /** 是否启用 */
    @Excel(name = "是否启用", readConverterExp = "1=启用,0=停用")
    private Integer isActive;

    /** 软删除时间 */
    private String deletedAt;

    public void setId(String id) { this.id = id; }
    public String getId() { return id; }

    public void setName(String name) { this.name = name; }
    public String getName() { return name; }

    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    public String getContactPerson() { return contactPerson; }

    public void setPhone(String phone) { this.phone = phone; }
    public String getPhone() { return phone; }

    public void setEmail(String email) { this.email = email; }
    public String getEmail() { return email; }

    public void setAddress(String address) { this.address = address; }
    public String getAddress() { return address; }

    public void setNotes(String notes) { this.notes = notes; }
    public String getNotes() { return notes; }

    public void setIsActive(Integer isActive) { this.isActive = isActive; }
    public Integer getIsActive() { return isActive; }

    public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }
    public String getDeletedAt() { return deletedAt; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("name", getName())
            .append("contactPerson", getContactPerson())
            .append("phone", getPhone())
            .append("email", getEmail())
            .append("address", getAddress())
            .append("notes", getNotes())
            .append("isActive", getIsActive())
            .append("deletedAt", getDeletedAt())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
