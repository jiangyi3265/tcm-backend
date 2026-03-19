# 库存-处方联动 完整流程说明

## 以"生脉散 粉剂 7剂"为例，走完全流程

---

## 一、开处方阶段（医生操作）

### 第1步：新建处方
医生在问诊页面点"新建处方" → 打开处方抽屉（960px宽）

设置：
- 处方类型：**粉剂 Powder**
- 剂数：**7**

### 第2步：选方剂
医生在搜索框输入"生脉" → 下拉列表出现"生脉散" → 点击

**系统自动执行：**
```
applyFormulaToDialog(formula)
  → calculatePrescription(formula.items, 7, 'powder', inventory)
    → 对每味药调用 convertSingleHerb()
```

### 第3步：自动匹配库存和供应商

以"人参 10g"为例：

```
1. 查找库存：findInventoryMatches('人参', 'powder', inventory)
   → 找到两个候选：
     • inv-31: 同仁堂, 2g/包, 库存200包, 5元/包
     • inv-32: 康仁堂, 5g/包, 库存150包, 3.5元/包

2. 智能选型：sortBySupplierPreference(candidates, null, 70)
   计算浪费量：
     • 同仁堂: ⌈70÷2⌉×2 - 70 = 70 - 70 = 0（精确匹配！）
     • 康仁堂: ⌈70÷5⌉×5 - 70 = 70 - 70 = 0（也精确匹配！）
   → 两个都0浪费，按库存量选 → 同仁堂(200包) 胜出

3. 换算：convertedQty = ⌈70g ÷ 2g/包⌉ = 35包
   → subtotal = 35包 × 5元/包 = 175元
```

三味药的完整计算结果：

| 药材 | 处方剂量 | 总克数 | 选中供应商 | 每包克数 | 需要包数 | 单价 | 小计 |
|------|---------|-------|-----------|---------|---------|------|------|
| 人参 | 10g×7 | 70g | 同仁堂 | 2g | 35包 | 5元 | 175元 |
| 麦冬 | 30g×7 | 210g | 同仁堂 | 3g | 70包 | 2元 | 140元 |
| 五味子 | 6g×7 | 42g | 同仁堂 | 2g | 21包 | 2.5元 | 52.5元 |
| **合计** | | | | | **126包** | | **367.5元** |

### 第4步：医生可选操作
- **修改剂量**：改dosage后自动重算（`recalcSingleItem`）
- **切换供应商**：从下拉选另一个供应商（`switchSupplier`）
- **切换处方类型**：改为草药/成药，自动全部重算（`recalcRxItems`）

### 第5步：保存处方
医生点"确认" → `saveRx()` → 处方保存到问诊记录中，包含所有换算信息

---

## 二、问诊完成 → 收款（前台操作）

```
问诊状态变化：draft → completed → paid
```

- 医生完成问诊 → 状态变为 `completed`
- 前台收款 → 状态变为 `paid`，记录锁定

**⚠️ 此时库存还没有扣减！** 只有发药时才扣减。

---

## 三、发药阶段（药房操作）

### 第6步：药房看到待发处方
药房页面（PharmacyView）显示所有 `status=paid` 且 `dispensingCompleted=false` 的处方

### 第7步：确认发药
药房人员点"确认发药" → `markDispensed(consult)`

**前端调用链：**
```
markDispensed(consult)
  → consultationsStore.markDispensingComplete(consult.id)
    → API: PATCH /api/consultations/{id}/dispense
```

**后端处理链：**
```
TcmConsultationController.dispense(id)
  → TcmConsultationServiceImpl.markDispensingComplete(id, actorId)
    
    1. 从 payload 提取处方数据
       buildPrescriptionGroups(payload)
         → 解析 prescriptions 数组
         → mergePrescriptionItems() 合并同名药材
    
    2. 扣减库存（对每个处方组）
       inventoryService.deductFromPrescription(herbals, 'powder')
         
         对每味药：
         a. 查找库存：selectTcmInventoryItemsByName('人参', 'powder')
         b. 智能选型（优先指定供应商 → 最小浪费 → 最大库存）
         c. 粉剂转换：deductQty = ⌈70g ÷ 2g/包⌉ = 35包
         d. 库存检查：200包 - 35包 = 165包 ≥ 0 ✓
         e. 更新库存：UPDATE quantity = 165
    
    3. 标记完成
       payload.dispensingCompleted = true
       payload.dispensingCompletedAt = 当前时间
```

**库存变动：**

| 药材 | 扣减前 | 扣减量 | 扣减后 |
|------|-------|-------|--------|
| 人参(同仁堂粉) | 200包 | 35包 | 165包 |
| 麦冬(同仁堂粉) | 300包 | 70包 | 230包 |
| 五味子(同仁堂粉) | 250包 | 21包 | 229包 |

---

## 四、特殊情况

### 情况A：库存不足
后端检测到某味药库存不足 → 返回错误 → 整个事务回滚 → 前端弹出错误提示

### 情况B：删除已发药的处方
```
deleteRx(idx)
  → 检测到 rx.dispensingCompleted = true
  → inventoryStore.restoreFromPrescription(herbals, 'powder')
    → API: POST /api/inventory/restore-prescription
      → 后端：70g ÷ 2g/包 = 35包 → 库存加回35包
```

### 情况C：草药模式（不需要转包）
- 库存单位：克
- 人参10g × 7剂 = 70g
- 直接扣减70g从草药库存
- 不涉及包/供应商规格换算

### 情况D：仅开方不拿药（none模式）
- 不做任何库存匹配和换算
- 不扣减库存
- 仅记录处方信息

---

## 五、整体架构图

```
┌─────────────────────────────────────────────────────────┐
│                    前端 (Vue3)                            │
│                                                          │
│  ConsultationView                PharmacyView            │
│  ┌──────────────┐               ┌──────────────┐        │
│  │ 新建处方      │               │ 待发药列表    │        │
│  │ 选方剂        │               │ 确认发药      │        │
│  │ 智能选型      │               │ 库存状态显示  │        │
│  │ 保存处方      │               └──────┬───────┘        │
│  └──────┬───────┘                       │               │
│         │                               │               │
│  prescriptionCalc.js              consultationsStore     │
│  (g→包换算, 智能选型)              .markDispensingComplete│
│         │                               │               │
│  inventoryStore                         │               │
│  (deduct/restore)                       │               │
└─────────┼───────────────────────────────┼───────────────┘
          │                               │
          ▼                               ▼
┌─────────────────────────────────────────────────────────┐
│                    后端 (Spring Boot)                     │
│                                                          │
│  TcmInventoryController          TcmConsultationController│
│  POST /deduct-prescription       PATCH /{id}/dispense    │
│  POST /restore-prescription                              │
│         │                               │               │
│         ▼                               ▼               │
│  TcmInventoryServiceImpl    TcmConsultationServiceImpl   │
│  ┌──────────────────┐       ┌──────────────────┐        │
│  │ 查找匹配库存      │       │ 提取处方数据      │        │
│  │ 智能供应商选型    │  ←──  │ 合并同名药材      │        │
│  │ 粉剂 g→包 转换   │       │ 调用库存扣减      │        │
│  │ 检查库存充足     │       │ 标记发药完成      │        │
│  │ 执行扣减/回滚    │       │ 记录审计日志      │        │
│  └──────────────────┘       └──────────────────┘        │
│                                                          │
│                    MySQL 数据库                           │
│  tcm_inventory_item  tcm_supplier  tcm_consultation      │
│  tcm_formula         tcm_formula_item                    │
└─────────────────────────────────────────────────────────┘
```

---

## 六、关键代码文件

| 文件 | 职责 |
|------|------|
| `prescriptionCalc.js` | 前端换算引擎：g→包、智能选型、计价 |
| `ConsultationView.vue` | 处方UI：方剂搜索、剂量编辑、供应商切换 |
| `PharmacyView.vue` | 药房：待发药列表、确认发药 |
| `inventory.js` (store) | 前端库存状态管理 |
| `TcmInventoryServiceImpl.java` | 后端：库存扣减/回滚 + g→包转换 |
| `TcmConsultationServiceImpl.java` | 后端：发药流程、处方解析 |
