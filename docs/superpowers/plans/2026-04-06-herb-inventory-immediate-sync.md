# 草药修改立即联动库存 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复草药编辑后的库存漏联动问题，确保删到空处方时立即回库且保留空处方，并为普通问诊更新增加库存联动兜底。

**Architecture:** 保持现有 `syncPrescription -> reserve/restore inventory` 主链路不变，只补前端自动同步门槛与后端空处方语义；同时在 `updateTcmConsultation()` 增加对 payload 草药变化的库存兜底，避免绕过处方接口时出现“有记录无联动”。

**Tech Stack:** Vue 3、Node `node:test`、Spring Boot、JUnit 5、Mockito、Fastjson2

---

### Task 1: 前端为已存在空处方继续触发同步

**Files:**
- Modify: `hospital/src/views/consultations/ConsultationView.vue`
- Test: `hospital/tests/consultationInventorySync.test.js`

- [ ] **Step 1: 写前端失败测试**

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  shouldSyncPrescriptionDraft,
  hasEditablePrescriptionItems,
} from '../src/utils/consultationInventorySync.js'

test('已存在处方即使删空也仍需同步一次用于立即回库', () => {
  assert.equal(hasEditablePrescriptionItems({ items: [] }), false)
  assert.equal(
    shouldSyncPrescriptionDraft({
      source: { id: 'rx-1', items: [] },
      existingPrescriptionIds: ['rx-1'],
    }),
    true,
  )
})

test('从未落库的新空处方不自动同步', () => {
  assert.equal(
    shouldSyncPrescriptionDraft({
      source: { id: 'rx-new', items: [] },
      existingPrescriptionIds: ['rx-old'],
    }),
    false,
  )
})
```

- [ ] **Step 2: 运行前端测试确认失败**

Run: `npm test -- consultationInventorySync.test.js`
Expected: FAIL with module not found or exported function not defined

- [ ] **Step 3: 写最小前端实现**

```js
export function hasEditablePrescriptionItems(source = {}) {
  const items = Array.isArray(source?.items) ? source.items : []
  return items.some((item) => String(item?.name || '').trim())
}

export function shouldSyncPrescriptionDraft({ source = {}, existingPrescriptionIds = [] } = {}) {
  if (hasEditablePrescriptionItems(source)) return true
  const id = String(source?.id || '').trim()
  return !!id && existingPrescriptionIds.includes(id)
}
```

- [ ] **Step 4: 在诊疗页接入新判定**

```js
import {
  shouldSyncPrescriptionDraft,
} from '../../utils/consultationInventorySync.js'

function hasPersistableRxDraft(source = rxForm.value) {
  const existingIds = form.value.prescriptions.map((item) => item.id)
  return shouldSyncPrescriptionDraft({
    source,
    existingPrescriptionIds: existingIds,
  })
}
```

- [ ] **Step 5: 运行前端测试确认通过**

Run: `npm test -- consultationInventorySync.test.js consultationCopy.test.js`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add hospital/src/views/consultations/ConsultationView.vue hospital/src/utils/consultationInventorySync.js hospital/tests/consultationInventorySync.test.js
git commit -m "fix(frontend): sync empty prescriptions for inventory rollback"
```

### Task 2: 后端同步空处方时只回库不重占

**Files:**
- Modify: `ruoyi-hospital/src/main/java/com/ruoyi/hospital/service/impl/TcmConsultationServiceImpl.java`
- Test: `ruoyi-hospital/src/test/java/com/ruoyi/hospital/service/impl/TcmConsultationServiceImplTest.java`

- [ ] **Step 1: 写后端失败测试**

```java
@Test
void syncPrescription_shouldKeepEmptyPrescriptionAndClearReservation()
{
    TcmConsultation existing = consultationWithPrescription("consult-1", "rx-1", reservationItem("inv-1", "黄芪", "70"));
    when(consultationMapper.selectTcmConsultationById("consult-1")).thenReturn(existing, existing, existing);
    when(consultationMapper.updateTcmConsultation(any(TcmConsultation.class))).thenReturn(1);
    when(inventoryService.restoreFromPrescription(anyList(), eq("raw_herbs"))).thenReturn(successResult());

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("prescription", emptyPrescription("rx-1"));

    TcmConsultation result = service.syncPrescription("consult-1", body, "u-1");

    assertNotNull(result);
    verify(inventoryService).restoreFromPrescription(anyList(), eq("raw_herbs"));
    verify(inventoryService, never()).deductFromPrescription(anyList(), eq("raw_herbs"));
}
```

- [ ] **Step 2: 运行后端测试确认失败**

Run: `mvn -pl ruoyi-hospital -Dtest=TcmConsultationServiceImplTest#syncPrescription_shouldKeepEmptyPrescriptionAndClearReservation test`
Expected: FAIL because test class/helper missing or code still attempts re-deduct path

- [ ] **Step 3: 写最小后端实现**

```java
private List<Map<String, Object>> reservePrescription(Map<String, Object> prescription)
{
    String prescriptionType = getString(prescription, "prescriptionType", "raw_herbs");
    List<Map<String, Object>> reservationItems = buildReservationSnapshot(prescription);
    if (reservationItems.isEmpty() || "none".equals(prescriptionType))
    {
        return new ArrayList<>();
    }
    // existing deduct logic
}
```

- [ ] **Step 4: 运行后端测试确认通过**

Run: `mvn -pl ruoyi-hospital -Dtest=TcmConsultationServiceImplTest#syncPrescription_shouldKeepEmptyPrescriptionAndClearReservation test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ruoyi-hospital/src/main/java/com/ruoyi/hospital/service/impl/TcmConsultationServiceImpl.java ruoyi-hospital/src/test/java/com/ruoyi/hospital/service/impl/TcmConsultationServiceImplTest.java
git commit -m "fix(backend): rollback inventory when prescription becomes empty"
```

### Task 3: 给普通问诊更新补库存联动兜底并清理伪库存快照

**Files:**
- Modify: `ruoyi-hospital/src/main/java/com/ruoyi/hospital/service/impl/TcmConsultationServiceImpl.java`
- Test: `ruoyi-hospital/src/test/java/com/ruoyi/hospital/service/impl/TcmConsultationServiceImplTest.java`

- [ ] **Step 1: 写后端失败测试**

```java
@Test
void updateTcmConsultation_shouldResyncInventoryWhenPrescriptionsChanged()
{
    TcmConsultation existing = consultationWithPrescription("consult-2", "rx-2", reservationItem("inv-1", "党参", "35"));
    TcmConsultation incoming = consultationForUpdate("consult-2", replacementPrescription("rx-2", "白术", "28"));
    when(consultationMapper.selectTcmConsultationById("consult-2")).thenReturn(existing);
    when(consultationMapper.updateTcmConsultation(any(TcmConsultation.class))).thenReturn(1);
    when(inventoryService.restoreFromPrescription(anyList(), anyString())).thenReturn(successResult());
    when(inventoryService.deductFromPrescription(anyList(), anyString())).thenReturn(deductSuccess("inv-2", "白术", "28"));

    int affected = service.updateTcmConsultation(incoming, "u-2");

    assertEquals(1, affected);
    verify(inventoryService).restoreFromPrescription(anyList(), eq("raw_herbs"));
    verify(inventoryService).deductFromPrescription(anyList(), eq("raw_herbs"));
}
```

- [ ] **Step 2: 运行后端测试确认失败**

Run: `mvn -pl ruoyi-hospital -Dtest=TcmConsultationServiceImplTest#updateTcmConsultation_shouldResyncInventoryWhenPrescriptionsChanged test`
Expected: FAIL because `updateTcmConsultation()` currently only writes payload

- [ ] **Step 3: 写最小后端实现**

```java
private void resyncInventoryForUpdatedConsultation(TcmConsultation existing, TcmConsultation incoming)
{
    JSONObject oldPayload = normalizeConsultationPayload(existing, parsePayload(existing.getPayload()));
    JSONObject newPayload = normalizeConsultationPayload(incoming, parsePayload(incoming.getPayload()));
    if (!hasPrescriptionInventoryChange(oldPayload, newPayload))
    {
        incoming.setPayload(newPayload.toJSONString());
        return;
    }
    restoreReservationsFromPayload(oldPayload);
    rebuildReservationsForPayload(newPayload);
    incoming.setPayload(newPayload.toJSONString());
}
```

- [ ] **Step 4: 去掉展示归一化阶段的伪 `inventoryReservation` 自动补写**

```java
if (normalized.get("inventoryReservation") == null)
{
    normalized.put("inventoryReservation", new ArrayList<>());
}
```

- [ ] **Step 5: 运行后端测试确认通过**

Run: `mvn -pl ruoyi-hospital -Dtest=TcmConsultationServiceImplTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add ruoyi-hospital/src/main/java/com/ruoyi/hospital/service/impl/TcmConsultationServiceImpl.java ruoyi-hospital/src/test/java/com/ruoyi/hospital/service/impl/TcmConsultationServiceImplTest.java
git commit -m "fix(backend): resync inventory on consultation updates"
```

### Task 4: 全量验证本次改动

**Files:**
- Test: `hospital/tests/consultationInventorySync.test.js`
- Test: `hospital/tests/consultationCopy.test.js`
- Test: `ruoyi-hospital/src/test/java/com/ruoyi/hospital/service/impl/TcmConsultationServiceImplTest.java`

- [ ] **Step 1: 跑前端相关测试**

Run: `npm test -- consultationInventorySync.test.js consultationCopy.test.js`
Expected: PASS

- [ ] **Step 2: 跑后端相关测试**

Run: `mvn -pl ruoyi-hospital -Dtest=TcmConsultationServiceImplTest test`
Expected: PASS

- [ ] **Step 3: 做一次需求对照检查**

```text
- 删到空处方：保留空处方并立即回库
- 处方编辑：继续立即联动库存
- 普通问诊更新写入草药：也会联动库存
- 发药/回退/付款：不承担库存重算
```

- [ ] **Step 4: Commit**

```bash
git add hospital/tests/consultationInventorySync.test.js hospital/src/views/consultations/ConsultationView.vue hospital/src/utils/consultationInventorySync.js ruoyi-hospital/src/main/java/com/ruoyi/hospital/service/impl/TcmConsultationServiceImpl.java ruoyi-hospital/src/test/java/com/ruoyi/hospital/service/impl/TcmConsultationServiceImplTest.java
git commit -m "test: verify herb inventory sync regression coverage"
```
