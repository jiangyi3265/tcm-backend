# 草药修改立即联动库存设计

## 1. 背景

当前系统已经具备处方级库存联动主链路：

- 诊疗页处方编辑通常通过 `PATCH /api/consultations/{id}/prescriptions`
- 后端 `syncPrescription()` 会先恢复旧库存，再按新处方重新占库

但仍然存在两个漏口：

1. 前端在处方删到没有有效药味时，不再触发自动同步，导致旧库存不会立即回退。
2. 后端普通问诊更新 `PUT /api/consultations/{id}` 可以直接写入 `prescriptions/herbals`，但不会联动库存。

本次改造目标不是重写整套库存机制，而是在现有结构上补齐“所有有草药修改且已记录的地方，库存必须立即联动”的缺口。

---

## 2. 本次确认口径

### 2.1 联动总原则

- 任何已经写入问诊 payload 的处方草药变化，都必须立即联动库存。
- 联动策略统一为：旧占用先恢复，再按当前处方内容重新占用。

### 2.2 空处方口径

- 当医生把一张已存在处方删到最后一味药都没有时：
  - 保留这张处方
  - 处方状态保持 `editing`
  - `items = []`
  - `inventoryReservation = []`
  - 立即回掉旧库存
  - 不再重新占库

### 2.3 状态职责边界

- `syncPrescription()`：负责草药内容同步与库存联动
- `completePrescription()`：只负责 `editing -> pending`
- `dispensePrescription()`：只负责 `pending -> dispensed`
- `reopenPrescription()`：只负责 `dispensed -> pending`
- `recordPayment()`：只负责付款记录

付款、发药、回退不承担草药库存重算职责。

---

## 3. 方案选型

本次采用“前端修正 + 后端兜底”的组合方案。

### 3.1 选择原因

- 只改前端可以修掉删空不回库，但挡不住其他入口绕过处方同步接口。
- 只改后端虽然能兜底，但会把普通问诊保存和处方编辑主链路混在一起，改动风险偏大。
- 前端修正负责保证诊疗页实时体验，后端兜底负责保证数据一致性，改动最小且覆盖完整。

---

## 4. 详细设计

### 4.1 前端改造

目标文件：

- `hospital/src/views/consultations/ConsultationView.vue`

#### 4.1.1 自动同步判定调整

当前 `hasPersistableRxDraft()` 只在处方里仍有有效药味时才允许同步。这个条件需要改成“当前处方是否需要同步”。

新规则：

1. 当前处方有有效药味时，继续自动同步。
2. 当前正在编辑一张已经存在于问诊中的处方时，即使当前 `items = []`，也必须允许同步一次。
3. 新建但从未落库过的空处方，不自动同步。

这样可确保：

- 加药、删药、改剂量、改剂数、改供应商仍按原逻辑自动同步。
- 删到最后一味药时，前端仍会发送一次 `syncPrescription`。
- 空白新处方不会制造无意义请求。

#### 4.1.2 空处方交互约束

- 空处方允许存在于编辑态。
- 空处方不允许进入 `pending`。
- 现有“完成处方”前的校验继续保留或补强，确保 `items = []` 时不能完成。

### 4.2 后端主链路改造

目标文件：

- `ruoyi-hospital/src/main/java/com/ruoyi/hospital/service/impl/TcmConsultationServiceImpl.java`

#### 4.2.1 `syncPrescription()` 的空处方语义

保留现有主流程：

1. 读取当前问诊
2. 找到旧处方
3. 恢复旧 `inventoryReservation`
4. 构建新处方
5. 重新占库
6. 写回 payload 和修改记录

其中第 5 步补充新规则：

- 如果新处方 `items = []`，则跳过重新占库
- `inventoryReservation` 写为空数组
- `inventorySyncedAt` 仍更新
- 处方本身仍保存，不自动删除

这样可以直接满足“保留空处方，并立即回库”。

#### 4.2.2 `updateTcmConsultation()` 后端兜底

当前普通问诊更新可以直接覆盖 payload 中的 `prescriptions/herbals/items`，但不会联动库存。这里需要加兜底逻辑。

兜底策略：

1. 读取旧 payload
2. 归一化新旧 payload
3. 判断 `prescriptions` / `herbals` 是否发生草药内容变化
4. 若未变化，则保持现有更新流程
5. 若发生变化，则：
   - 先恢复旧处方里真实存在的 `inventoryReservation`
   - 再按新 payload 中的处方列表逐张重建占库
   - 空处方不占库，只保留空 `inventoryReservation`
6. 再写回最新 payload

这里不要求把普通 `PUT` 完全改造成处方专用接口，只做一致性兜底。

#### 4.2.3 去掉“假库存快照”

当前 `normalizePrescriptionEntry()` 会在某些状态下自动补写 `inventoryReservation` 快照，但这不是实际扣库结果，容易造成“看起来已占库、实际上没占库”的假记录。

本次要求：

- `inventoryReservation` 只保留真实库存联动产生的结果
- 不能再由纯展示归一化逻辑伪造库存占用记录

---

## 5. 数据流

### 5.1 正常编辑处方

1. 医生修改草药内容
2. 前端自动触发 `syncPrescription`
3. 后端恢复旧库存
4. 后端按新处方重新占库
5. 返回新处方与新库存状态

### 5.2 删到空处方

1. 医生删除最后一味药
2. 前端仍触发 `syncPrescription`
3. 后端恢复旧库存
4. 后端检测到 `items = []`
5. 不再重新占库
6. 保存空处方，`inventoryReservation = []`

### 5.3 绕过主链路的普通问诊更新

1. 某入口直接调用 `PUT /api/consultations/{id}`
2. 后端检测到 payload 中草药内容变化
3. 启动兜底逻辑恢复旧库存并重算新库存
4. 保证最终库存与最新问诊内容一致

---

## 6. 异常处理

### 6.1 库存不足

- 如果新处方重占库存失败，整个事务回滚。
- 旧库存恢复与新库存占用必须处于同一事务内，避免“已回库但未重占”的半成功状态。

### 6.2 空处方

- 空处方不是异常。
- 空处方同步成功后返回最新问诊，库存应表现为已恢复。

### 6.3 无效库存快照

- 对历史脏数据中的伪 `inventoryReservation` 需要谨慎处理。
- 本次优先原则是：后续新写入数据必须是真实联动结果；旧数据兼容可继续沿用当前读取逻辑，但不能继续制造新假数据。

---

## 7. 测试与验收

### 7.1 前端验收

1. 新增一味药后，库存立即变化。
2. 修改剂量后，库存立即变化。
3. 切换供应商后，库存立即变化。
4. 删除一味药后，库存立即回掉对应部分。
5. 删除最后一味药后：
   - 处方仍存在
   - 处方为空
   - 库存立即全部恢复
6. 空处方不能完成为 `pending`。

### 7.2 后端验收

1. `syncPrescription()` 更新非空处方时，旧占用先恢复再重占。
2. `syncPrescription()` 更新为空处方时，只回库不重占。
3. `deletePrescription()` 仍保持“删除即回库”。
4. `updateTcmConsultation()` 直接改写处方内容时，也会触发库存联动。
5. `normalizePrescriptionEntry()` 不再生成伪 `inventoryReservation`。

### 7.3 回归验证

1. 复制处方仍可成功联动库存。
2. 完成处方不重复扣库。
3. 发药不重复扣库。
4. 回退已发不自动回库。
5. 付款逻辑不受影响。

---

## 8. 非目标

本次不包含以下内容：

- 重构付款逻辑
- 重构发药逻辑
- 重构处方状态机
- 自动把空处方转成删除
- 历史旧数据批量修复

---

## 9. 实施建议

按以下顺序实施：

1. 先修前端空处方仍可同步
2. 再修后端 `syncPrescription()` 对空处方的处理
3. 再补 `updateTcmConsultation()` 的库存兜底
4. 最后去掉伪 `inventoryReservation` 生成逻辑

这样可以先尽快修掉“删空不回库”的主问题，再补系统级一致性。
