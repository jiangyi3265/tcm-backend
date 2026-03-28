package com.ruoyi.hospital.domain;

import java.math.BigDecimal;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 库存物品对象 tcm_inventory_item
 *
 * @author ruoyi
 */
public class TcmInventoryItem extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 物品ID */
    private String id;

    /** 名称 */
    @Excel(name = "名称")
    private String name;

    /** 分类（powder, raw_herbs, pills） */
    @Excel(name = "分类")
    private String category;

    /** 单位 */
    @Excel(name = "单位")
    private String unit;

    /** 数量 */
    @Excel(name = "数量")
    private BigDecimal quantity;

    /** 单价 */
    @Excel(name = "单价")
    private BigDecimal pricePerUnit;

    /** 最低库存量 */
    @Excel(name = "最低库存量")
    private BigDecimal minStockLevel;

    /** 供应商 */
    @Excel(name = "供应商")
    private String supplier;

    /** 供应商ID */
    private String supplierId;

    /** 每包克数 */
    private BigDecimal gramsPerPacket;

    /** 分院ID */
    private String branchId;

    /** 是否活跃（1=活跃, 0=停用） */
    @Excel(name = "是否活跃", readConverterExp = "1=活跃,0=停用")
    private Integer isActive;

    /** 删除时间 */
    private String deletedAt;

    /** 关联中药字典ID */
    private String herbDictId;

    /** 扩展JSON（存储别名、性味归经、功效禁忌、进价等附加属性） */
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

    public void setCategory(String category)
    {
        this.category = category;
    }

    public String getCategory()
    {
        return category;
    }

    public void setUnit(String unit)
    {
        this.unit = unit;
    }

    public String getUnit()
    {
        return unit;
    }

    public void setQuantity(BigDecimal quantity)
    {
        this.quantity = quantity;
    }

    public BigDecimal getQuantity()
    {
        return quantity;
    }

    public void setPricePerUnit(BigDecimal pricePerUnit)
    {
        this.pricePerUnit = pricePerUnit;
    }

    public BigDecimal getPricePerUnit()
    {
        return pricePerUnit;
    }

    public void setMinStockLevel(BigDecimal minStockLevel)
    {
        this.minStockLevel = minStockLevel;
    }

    public BigDecimal getMinStockLevel()
    {
        return minStockLevel;
    }

    public void setSupplier(String supplier)
    {
        this.supplier = supplier;
    }

    public String getSupplier()
    {
        return supplier;
    }

    public void setSupplierId(String supplierId)
    {
        this.supplierId = supplierId;
    }

    public String getSupplierId()
    {
        return supplierId;
    }

    public void setGramsPerPacket(BigDecimal gramsPerPacket)
    {
        this.gramsPerPacket = gramsPerPacket;
    }

    public BigDecimal getGramsPerPacket()
    {
        return gramsPerPacket;
    }

    public void setBranchId(String branchId)
    {
        this.branchId = branchId;
    }

    public String getBranchId()
    {
        return branchId;
    }

    public void setIsActive(Integer isActive)
    {
        this.isActive = isActive;
    }

    public Integer getIsActive()
    {
        return isActive;
    }

    public void setDeletedAt(String deletedAt)
    {
        this.deletedAt = deletedAt;
    }

    public String getDeletedAt()
    {
        return deletedAt;
    }

    public void setHerbDictId(String herbDictId)
    {
        this.herbDictId = herbDictId;
    }

    public String getHerbDictId()
    {
        return herbDictId;
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
            .append("category", getCategory())
            .append("unit", getUnit())
            .append("quantity", getQuantity())
            .append("pricePerUnit", getPricePerUnit())
            .append("minStockLevel", getMinStockLevel())
            .append("supplier", getSupplier())
            .append("gramsPerPacket", getGramsPerPacket())
            .append("branchId", getBranchId())
            .append("isActive", getIsActive())
            .append("deletedAt", getDeletedAt())
            .append("herbDictId", getHerbDictId())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
