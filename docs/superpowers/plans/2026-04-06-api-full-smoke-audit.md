# API Full Smoke Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 对 `ruoyi-hospital` 当前暴露的 API 做一轮真实环境 smoke，确认哪些接口已成功联调、哪些接口存在明确错误、哪些接口因前置条件未覆盖。

**Architecture:** 先做 controller 静态审阅收敛高风险点，再按“只读/公开接口”“核心写接口”“主数据与配置写接口”“文件与公开访问接口”分批调用真实后端。所有写操作只使用一次性 smoke 数据，并在接口支持的情况下立即回滚或清理。

**Tech Stack:** Spring Boot, RuoYi, PowerShell, Invoke-RestMethod, 真实后端 `http://127.0.0.1:8006`

---

### Task 1: 收敛接口清单与静态高风险点

**Files:**
- Modify: `C:/Users/jiangyi/Desktop/项目/未完成/医院/RuoYi-Vue/docs/superpowers/plans/2026-04-06-api-full-smoke-audit.md`

- [ ] **Step 1: 汇总 controller 与路由**

Run: `rg -n "@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|RequestMapping)" C:/Users/jiangyi/Desktop/项目/未完成/医院/RuoYi-Vue/ruoyi-hospital/src/main/java/com/ruoyi/hospital/controller`
Expected: 输出全部 API 路由，作为 smoke 基准清单。

- [ ] **Step 2: 回收并汇总子代理静态审阅**

Run: `wait_agent` 回收分域 controller 审阅结论。
Expected: 形成高风险接口列表与已知空指针/鉴权/响应语义问题列表。

### Task 2: 执行只读与公开接口 smoke

**Files:**
- Modify: `C:/Users/jiangyi/Desktop/项目/未完成/医院/RuoYi-Vue/docs/superpowers/plans/2026-04-06-api-full-smoke-audit.md`

- [ ] **Step 1: 登录并获取 admin / practitioner token**

Run: `POST /api/auth/login`
Expected: 成功拿到 token，用于 admin 和 practitioner 两类权限校验。

- [ ] **Step 2: 先测只读与公开接口**

Run: `GET /api/bootstrap`, `GET /api/statistics/overview`, `GET /api/settings`, `GET /api/formulas`, `GET /api/branches`, `GET /api/herb-dict`, `GET /api/acupoints`, `GET /api/meridians`, `GET /api/public/files/access`（基于真实签名链接）, `GET /api/consent/{token}`, `GET /api/intake/{token}`
Expected: 成功路径返回 2xx；非法 token 不应出现 500。

### Task 3: 执行核心写接口 smoke

**Files:**
- Modify: `C:/Users/jiangyi/Desktop/项目/未完成/医院/RuoYi-Vue/docs/superpowers/plans/2026-04-06-api-full-smoke-audit.md`

- [ ] **Step 1: 验证患者 / 预约 / 问诊 / 库存主链路**

Run: 针对 `patients`, `appointments`, `consultations`, `inventory` 的新增、更新、状态变更、库存联动接口调用真实环境。
Expected: 成功返回 2xx，且库存扣减/回库与问诊处方同步。

- [ ] **Step 2: 验证公开 token 链路**

Run: 通过 `consent send` / `intake send` 生成或获取真实 token，再验证 `GET/POST /api/consent/*` 与 `GET/POST /api/intake/*`。
Expected: token 可读、可提交、无 500。

### Task 4: 执行主数据与配置写接口 smoke

**Files:**
- Modify: `C:/Users/jiangyi/Desktop/项目/未完成/医院/RuoYi-Vue/docs/superpowers/plans/2026-04-06-api-full-smoke-audit.md`

- [ ] **Step 1: 一次性 smoke 数据写入**

Run: 创建 `settings rooms/price-lists`, `formulas`, `suppliers`, `branches`, `herb-dict`, `acupoints`, `meridians`, `templates`, `unit-conversions`, `users`
Expected: 全部创建成功，并能立刻回查。

- [ ] **Step 2: 逐个更新、软删、恢复、物理删**

Run: 对支持 `PUT/PATCH/DELETE` 的接口逐个执行。
Expected: 返回状态正确，软删可恢复，物理删仅针对一次性 smoke 数据。

### Task 5: 失败处理与结论汇总

**Files:**
- Modify: `C:/Users/jiangyi/Desktop/项目/未完成/医院/RuoYi-Vue/docs/superpowers/plans/2026-04-06-api-full-smoke-audit.md`

- [ ] **Step 1: 若出现失败，按根因最小修复**

Run: 打开对应 controller/service/mapper，定位异常分支，修复后重跑相关 smoke 与测试。
Expected: 失败接口要么被修复并回归通过，要么保留为明确未解决问题。

- [ ] **Step 2: 输出最终覆盖结论**

Run: 汇总“已实测通过 / 静态判定有风险 / 因条件未覆盖”。
Expected: 不模糊宣称“全部没问题”，而是给出有证据的接口级结论。
