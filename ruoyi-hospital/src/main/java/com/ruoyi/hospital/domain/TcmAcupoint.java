package com.ruoyi.hospital.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 针灸穴位对象 tcm_acupoint
 *
 * @author ruoyi
 */
public class TcmAcupoint extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private String id;

    @Excel(name = "穴位名称")
    private String name;

    @Excel(name = "拼音")
    private String pinyin;

    @Excel(name = "英文名")
    private String englishName;

    @Excel(name = "所属经络")
    private String meridian;

    @Excel(name = "定位")
    private String location;

    @Excel(name = "主治")
    private String indication;

    @Excel(name = "刺法")
    private String method;

    @Excel(name = "备注")
    private String notes;

    private Integer isActive;

    private String deletedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPinyin() { return pinyin; }
    public void setPinyin(String pinyin) { this.pinyin = pinyin; }

    public String getEnglishName() { return englishName; }
    public void setEnglishName(String englishName) { this.englishName = englishName; }

    public String getMeridian() { return meridian; }
    public void setMeridian(String meridian) { this.meridian = meridian; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getIndication() { return indication; }
    public void setIndication(String indication) { this.indication = indication; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }

    public String getDeletedAt() { return deletedAt; }
    public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }

    @Override
    public String toString() {
        return "TcmAcupoint{id=" + id + ", name=" + name + ", meridian=" + meridian + "}";
    }
}
