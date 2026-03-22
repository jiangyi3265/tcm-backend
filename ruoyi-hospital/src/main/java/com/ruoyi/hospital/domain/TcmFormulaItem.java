package com.ruoyi.hospital.domain;

import java.math.BigDecimal;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 方剂药材明细对象 tcm_formula_item
 *
 * @author ruoyi
 */
public class TcmFormulaItem
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 方剂ID */
    private String formulaId;

    /** 药材名称 */
    private String herbName;

    /** 默认剂量 */
    private BigDecimal dosage;

    /** 单位 */
    private String unit;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    private String notes;

    /** 关联中药字典ID */
    private String herbDictId;

    public void setId(Long id) { this.id = id; }
    public Long getId() { return id; }

    public void setFormulaId(String formulaId) { this.formulaId = formulaId; }
    public String getFormulaId() { return formulaId; }

    public void setHerbName(String herbName) { this.herbName = herbName; }
    public String getHerbName() { return herbName; }

    public void setDosage(BigDecimal dosage) { this.dosage = dosage; }
    public BigDecimal getDosage() { return dosage; }

    public void setUnit(String unit) { this.unit = unit; }
    public String getUnit() { return unit; }

    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getSortOrder() { return sortOrder; }

    public void setNotes(String notes) { this.notes = notes; }
    public String getNotes() { return notes; }

    public void setHerbDictId(String herbDictId) { this.herbDictId = herbDictId; }
    public String getHerbDictId() { return herbDictId; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("formulaId", getFormulaId())
            .append("herbName", getHerbName())
            .append("dosage", getDosage())
            .append("unit", getUnit())
            .append("sortOrder", getSortOrder())
            .append("notes", getNotes())
            .append("herbDictId", getHerbDictId())
            .toString();
    }
}
