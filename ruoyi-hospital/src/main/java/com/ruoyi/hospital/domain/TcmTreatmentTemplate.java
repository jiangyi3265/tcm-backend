package com.ruoyi.hospital.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 诊疗模板对象 tcm_treatment_template
 */
public class TcmTreatmentTemplate extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private String id;
    @Excel(name = "模板名称") private String name;
    @Excel(name = "病症名称") private String disease;
    @Excel(name = "分类") private String category;
    @Excel(name = "模板说明") private String description;
    private String acupointsJson;
    private String formulaIds;
    @Excel(name = "常用医嘱") private String advice;
    @Excel(name = "备注") private String notes;
    private Integer isActive;
    private String deletedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDisease() { return disease; }
    public void setDisease(String disease) { this.disease = disease; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAcupointsJson() { return acupointsJson; }
    public void setAcupointsJson(String acupointsJson) { this.acupointsJson = acupointsJson; }
    public String getFormulaIds() { return formulaIds; }
    public void setFormulaIds(String formulaIds) { this.formulaIds = formulaIds; }
    public String getAdvice() { return advice; }
    public void setAdvice(String advice) { this.advice = advice; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }
    public String getDeletedAt() { return deletedAt; }
    public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }
}
