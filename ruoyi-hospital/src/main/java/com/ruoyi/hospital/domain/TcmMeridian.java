package com.ruoyi.hospital.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 经络字典对象 tcm_meridian
 */
public class TcmMeridian extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private String id;
    @Excel(name = "经络名称") private String name;
    @Excel(name = "英文名") private String englishName;
    @Excel(name = "缩写") private String abbr;
    @Excel(name = "分类") private String category;
    @Excel(name = "所属脏腑") private String organ;
    @Excel(name = "循行路线") private String pathway;
    @Excel(name = "穴位数量") private Integer acupointCount;
    @Excel(name = "主治概述") private String indication;
    @Excel(name = "备注") private String notes;
    private Integer isActive;
    private String deletedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEnglishName() { return englishName; }
    public void setEnglishName(String englishName) { this.englishName = englishName; }
    public String getAbbr() { return abbr; }
    public void setAbbr(String abbr) { this.abbr = abbr; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getOrgan() { return organ; }
    public void setOrgan(String organ) { this.organ = organ; }
    public String getPathway() { return pathway; }
    public void setPathway(String pathway) { this.pathway = pathway; }
    public Integer getAcupointCount() { return acupointCount; }
    public void setAcupointCount(Integer acupointCount) { this.acupointCount = acupointCount; }
    public String getIndication() { return indication; }
    public void setIndication(String indication) { this.indication = indication; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }
    public String getDeletedAt() { return deletedAt; }
    public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }
}
