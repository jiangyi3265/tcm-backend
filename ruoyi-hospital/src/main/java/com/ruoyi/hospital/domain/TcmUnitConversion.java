package com.ruoyi.hospital.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.math.BigDecimal;

/**
 * 单位换算对象 tcm_unit_conversion
 *
 * @author ruoyi
 */
public class TcmUnitConversion extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private String fromUnit;
    private String toUnit;
    private BigDecimal factor;
    private String notes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFromUnit() { return fromUnit; }
    public void setFromUnit(String fromUnit) { this.fromUnit = fromUnit; }

    public String getToUnit() { return toUnit; }
    public void setToUnit(String toUnit) { this.toUnit = toUnit; }

    public BigDecimal getFactor() { return factor; }
    public void setFactor(BigDecimal factor) { this.factor = factor; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    @Override
    public String toString() {
        return "TcmUnitConversion{" + fromUnit + " → " + toUnit + " × " + factor + "}";
    }
}
