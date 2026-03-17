package com.ruoyi.hospital.domain;

import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 方剂对象 tcm_formula
 *
 * @author ruoyi
 */
public class TcmFormula extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 方剂ID */
    private String id;

    /** 方剂名称 */
    @Excel(name = "方剂名称")
    private String name;

    /** 方剂分类 */
    @Excel(name = "方剂分类")
    private String category;

    /** 方剂说明/功效 */
    @Excel(name = "功效")
    private String description;

    /** 出处/来源 */
    @Excel(name = "出处")
    private String source;

    /** 是否启用 */
    @Excel(name = "是否启用", readConverterExp = "1=启用,0=停用")
    private Integer isActive;

    /** 软删除时间 */
    private String deletedAt;

    /** 方剂药材明细（非DB字段，用于联查） */
    private List<TcmFormulaItem> items;

    public void setId(String id) { this.id = id; }
    public String getId() { return id; }

    public void setName(String name) { this.name = name; }
    public String getName() { return name; }

    public void setCategory(String category) { this.category = category; }
    public String getCategory() { return category; }

    public void setDescription(String description) { this.description = description; }
    public String getDescription() { return description; }

    public void setSource(String source) { this.source = source; }
    public String getSource() { return source; }

    public void setIsActive(Integer isActive) { this.isActive = isActive; }
    public Integer getIsActive() { return isActive; }

    public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }
    public String getDeletedAt() { return deletedAt; }

    public void setItems(List<TcmFormulaItem> items) { this.items = items; }
    public List<TcmFormulaItem> getItems() { return items; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("name", getName())
            .append("category", getCategory())
            .append("description", getDescription())
            .append("source", getSource())
            .append("isActive", getIsActive())
            .append("deletedAt", getDeletedAt())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .append("items", getItems())
            .toString();
    }
}
