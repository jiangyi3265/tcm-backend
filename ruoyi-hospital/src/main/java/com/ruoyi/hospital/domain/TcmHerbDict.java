package com.ruoyi.hospital.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 中药材字典对象 tcm_herb_dict
 */
public class TcmHerbDict extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private String id;

    @Excel(name = "药材名称")
    private String name;

    @Excel(name = "别名")
    private String alias;

    @Excel(name = "拼音")
    private String pinyin;

    @Excel(name = "分类")
    private String category;

    @Excel(name = "药性")
    private String nature;

    @Excel(name = "药味")
    private String taste;

    @Excel(name = "归经")
    private String meridianTropism;

    @Excel(name = "功效")
    private String efficacy;

    @Excel(name = "主治")
    private String indication;

    @Excel(name = "用量范围")
    private String dosageRange;

    @Excel(name = "禁忌")
    private String contraindication;

    @Excel(name = "备注")
    private String notes;

    private Integer isActive;
    private String deletedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public String getPinyin() { return pinyin; }
    public void setPinyin(String pinyin) { this.pinyin = pinyin; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getNature() { return nature; }
    public void setNature(String nature) { this.nature = nature; }
    public String getTaste() { return taste; }
    public void setTaste(String taste) { this.taste = taste; }
    public String getMeridianTropism() { return meridianTropism; }
    public void setMeridianTropism(String meridianTropism) { this.meridianTropism = meridianTropism; }
    public String getEfficacy() { return efficacy; }
    public void setEfficacy(String efficacy) { this.efficacy = efficacy; }
    public String getIndication() { return indication; }
    public void setIndication(String indication) { this.indication = indication; }
    public String getDosageRange() { return dosageRange; }
    public void setDosageRange(String dosageRange) { this.dosageRange = dosageRange; }
    public String getContraindication() { return contraindication; }
    public void setContraindication(String contraindication) { this.contraindication = contraindication; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }
    public String getDeletedAt() { return deletedAt; }
    public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }

    @Override
    public String toString() {
        return "TcmHerbDict{id=" + id + ", name=" + name + ", category=" + category + "}";
    }
}
