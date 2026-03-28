# 菠萝医院系统扩展 - 实施进展 (2026-03-13 11:46 更新)

## ✅ 编译验证 (2026-03-13 11:46)
- **前端 (hospital)**: `vite build` ✅ 构建成功 (6.84s)
- **后端 (RuoYi-Vue)**: `mvn compile` ✅ 全部8个模块编译成功 (7.19s)
- **Bug修复**: `TcmBootstrapController.java` 缺少 `import java.util.LinkedHashMap` → 已添加

## 🔍 商用化审查 (2026-03-13 新增)
- 详细审查报告：`doc/commercial_review.md`
- **商用准备度**: 约 60-70%
- **🔴 必须修复**: H2→MySQL迁移、JWT密钥安全、CORS限制、移除Demo数据、HTTPS
- **🟡 建议改进**: ~~分页查询~~、~~操作日志~~、~~表单验证~~、~~密码管理~~
- **🟢 增强功能**: ~~统计报表~~、~~处方打印~~、预约提醒

## 🆕 核心功能实现 (2026-03-13 第二阶段)

### ✅ 1. 用户密码管理
- **自助修改密码**: Header 头像下拉菜单 → "修改密码"对话框
  - 验证旧密码 → 设置新密码(≥6位) → BCrypt 加密存储
  - 后端: `AuthController.changePassword()` 
  - 前端: `AppHeader.vue` 密码修改对话框
- **管理员重置密码**: 系统管理 → 用户管理 → "重置密码"按钮
  - 仅 admin 角色可操作
  - 后端: `AuthController.resetPassword()`
  - 前端: `AdminView.vue` 重置密码对话框
- **API**: `POST /api/auth/change-password`, `POST /api/auth/reset-password`

### ✅ 2. 数据统计报表
- **新页面**: `/statistics` 数据统计仪表盘
- **导航入口**: 侧边栏 "数据统计" (admin + practitioner 可见)
- **功能**:
  - 今日速览: 今日诊疗数、今日预约、今日/本周/本月收入
  - 累计总览: 总病人数、总诊疗数、各状态分布(草稿/完诊/已收款)
  - 诊疗状态分布条形图(CSS绘制，无需外部图表库)
  - 近7日收入趋势(条形图)
  - 近6月收入趋势(条形图)
  - 病种排行 TOP10
  - 医师诊疗量排行
- **后端**: `StatisticsController` `/api/statistics/overview`
- **前端**: `StatisticsView.vue`(纯CSS图表，无外部依赖)

### ✅ 3. 处方笺打印
- **功能**: 诊疗记录 → 治疗方案 Tab → 每个处方行 "打印" 按钮
- **打印格式**: 传统中医处方笺样式
  - 诊所名(红色)、"处方笺"标题
  - 患者信息: 姓名、性别、年龄、日期、编号、主诉
  - 辨证结果
  - 药材列表(序号/药名/剂量/备注)
  - 剂数、处方类型、用法、取药处
  - 医师签名区、药师签名区、调剂签名区
- **实现**: `pdfExport.js` → `printPrescription()` 函数
- **触发**: 处方表格操作列增加 "打印" 按钮(已保存和只读状态均可打印)

### ✅ 4. 操作审计日志
- **新页面**: `/audit-logs` 操作日志(仅 admin 可见)
- **数据库**: `audit_logs` 表(JPA自动创建)
  - 记录: 用户ID/姓名/角色、操作类型、目标类型/ID/名称、详情、时间
- **已集成审计点**:
  - 用户登录(LOGIN)
  - 修改密码(PASSWORD_CHANGE)
  - 管理员重置密码(PASSWORD_RESET)
- **前端**: `AuditLogView.vue` 时间线视图
  - 模块筛选(用户/病人/诊疗/库存等)
  - 时间范围筛选(7天/30天/90天/1年)
  - 关键字搜索
  - 颜色编码操作类型
- **后端**: `AuditLogController` + `AuditLogRepository`
- **API**: `GET /api/audit-logs`, `GET /api/audit-logs/recent`

### ✅ 5. 路由与导航更新
- 新增路由: `/statistics`, `/audit-logs`
- 侧边栏新增: "数据统计"(TrendCharts图标), "操作日志"(Document图标)
- 权限控制: statistics → admin+practitioner, audit-logs → admin
- Header 面包屑: 支持新页面标题显示
- i18n: 中英文导航翻译已同步

## ✅ 全部五大模块已完成编码 + i18n国际化 + CRUD完善

### 🥇 Phase 1: 中药材字典 + 大量草药数据 ✅
- **数据库**: `tcm_herb_dict` 表, 80种常用中药材种子数据
- **后端**: TcmHerbDict.java / Mapper / Service / Controller
- **前端**: herbDictApi / herbDict store / AdminView "草药字典" Tab
- **功能**: 搜索(名称/拼音)、分类过滤、增删改查
- **i18n**: ✅ 已完成中英文翻译

### 🥈 Phase 2: 扩充穴位数据到 100+ ✅
- 补充80个穴位(覆盖十二经脉+任督+经外奇穴)
- 总计100个穴位

### 🥉 Phase 3: 扩充方剂数据到 50+ ✅
- 补充42个经典方剂
- 补充核心方剂药材明细(15个重点方剂)
- 总计50个方剂

### 🏅 Phase 4: 经络字典 ✅
- **数据库**: `tcm_meridian` 表, 14条经络种子数据
- **后端**: TcmMeridian.java / Mapper / Service / Controller
- **前端**: meridiansApi / meridians store / AdminView "经络字典" Tab
- **i18n**: ✅ 已完成中英文翻译

### 🏅 Phase 5: 诊疗模板 ✅
- **数据库**: `tcm_treatment_template` 表, 10个常见病症模板
- **后端**: TcmTreatmentTemplate.java / Mapper / Service / Controller
- **前端**: templatesApi / templates store / AdminView "诊疗模板" Tab
- **i18n**: ✅ 已完成中英文翻译

## 🌐 国际化 (i18n) ✅ 全部完成

### 已国际化的模块：
1. ✅ 草药字典 (herbDict) — Phase 1 完成
2. ✅ 经络字典 (meridians) — Phase 4 完成
3. ✅ 诊疗模板 (templates) — Phase 5 完成
4. ✅ **方剂管理 (formulas)** — 2026-03-13 新增 (30个翻译键)
5. ✅ **供应商管理 (suppliers)** — 2026-03-13 新增 (20个翻译键)
6. ✅ **穴位管理 (acupoints)** — 2026-03-13 新增 (28个翻译键)
7. ✅ **导航菜单 (nav)** — 新增 statistics/auditLogs 翻译

## 🔧 功能完善 (2026-03-13)

### 穴位编辑改进 ✅
- 对话框编辑模式，展示全部 8 个字段

## 创建/修改的文件清单

### 本次新增文件 (第二阶段)
**后端 Java:**
- `AuditLog.java` — 审计日志实体
- `AuditLogRepository.java` — 审计日志数据访问
- `AuditLogController.java` — 审计日志API + 通用日志记录工具
- `StatisticsController.java` — 数据统计API

**前端 Vue:**
- `StatisticsView.vue` — 数据统计报表页面
- `AuditLogView.vue` — 操作日志页面

### 本次修改文件 (第二阶段)
**后端:**
- `AuthController.java` — 新增 change-password, reset-password 端点 + 审计日志集成

**前端:**
- `api.js` — 新增 authApi.changePassword/resetPassword, auditLogsApi, statisticsApi
- `router/index.js` — 新增 /statistics, /audit-logs 路由
- `permissions.js` — MENU_ACCESS 增加 statistics, audit-logs
- `AppSidebar.vue` — 新增两个导航菜单项
- `AppHeader.vue` — 新增密码修改对话框、面包屑支持
- `AdminView.vue` — 新增管理员重置密码功能
- `ConsultationView.vue` — 处方打印按钮
- `pdfExport.js` — 新增 printPrescription() 处方笺打印
- `zh-CN.js` — 新增导航翻译键
- `en.js` — 新增导航翻译键

### SQL脚本
- `sql/tcm_all_in_one.sql` — 新库唯一初始化脚本

## 部署步骤
1. 执行 SQL 脚本
2. 重新编译后端 `mvn clean package`
3. 前端重新构建 `npm run build`
4. ✅ 前端构建通过，后端编译通过
