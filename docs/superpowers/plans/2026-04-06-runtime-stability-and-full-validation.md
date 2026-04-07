# Runtime Stability And Full Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复后端在真实冒烟过程中出现的掉线问题，并补齐后端写接口与前端浏览器关键页面的完整验收。

**Architecture:** 先收紧运行时线程预算并降低登录链路的 `User-Agent` 解析成本，再对当前 `master` 做真实 HTTP smoke、关键写接口回归，以及最小浏览器级页面冒烟。修复与验收都围绕“主链路稳定可运行”展开，不做无关重构。

**Tech Stack:** Spring Boot, RuoYi, Java 17, Vite, Vue 3, Playwright, PowerShell

---

### Task 1: 修复后端运行时稳定性

**Files:**
- Modify: `C:/Users/jiangyi/Desktop/项目/未完成/医院/RuoYi-Vue/ruoyi-admin/src/main/resources/application.yml`
- Modify: `C:/Users/jiangyi/Desktop/项目/未完成/医院/RuoYi-Vue/ruoyi-common/src/main/java/com/ruoyi/common/utils/http/UserAgentUtils.java`
- Modify: `C:/Users/jiangyi/Desktop/项目/未完成/医院/RuoYi-Vue/ruoyi-framework/src/main/java/com/ruoyi/framework/config/ThreadPoolConfig.java`
- Modify: `C:/Users/jiangyi/Desktop/项目/未完成/医院/RuoYi-Vue/ruoyi-framework/src/main/java/com/ruoyi/framework/manager/factory/AsyncFactory.java`
- Modify: `C:/Users/jiangyi/Desktop/项目/未完成/医院/RuoYi-Vue/ruoyi-framework/src/main/java/com/ruoyi/framework/web/service/TokenService.java`

- [ ] **Step 1: 收紧 Tomcat 与异步线程池配置**

将 `server.tomcat.threads.max`、`server.tomcat.threads.min-spare`、`accept-count` 以及 `ThreadPoolConfig` 里的 `corePoolSize/maxPoolSize/queueCapacity` 改成适合当前诊所场景的保守值，避免本地 Windows 环境被大线程预算拖垮。

- [ ] **Step 2: 降低登录热路径的 `User-Agent` 解析成本**

在 `UserAgentUtils` 中改为“正则快速识别优先，必要时才退回重解析”，并在 `TokenService` / `AsyncFactory` 中只走轻量解析路径，避免登录与登录日志各自触发高开销解析。

- [ ] **Step 3: 补上稳定性回归测试**

为 `UserAgentUtils` 增加主流 UA 快路径测试，为线程配置增加合理阈值断言或最小验证，确保后续不会再回到激进配置。

### Task 2: 回归后端自动化与真实 HTTP smoke

**Files:**
- Modify: `C:/Users/jiangyi/Desktop/项目/未完成/医院/RuoYi-Vue/docs/superpowers/plans/2026-04-06-runtime-stability-and-full-validation.md`

- [ ] **Step 1: 跑后端自动化回归**

执行 `mvn -pl ruoyi-hospital -am test`，必要时补跑 `ruoyi-admin` 打包，确认修复没有破坏现有测试与构建。

- [ ] **Step 2: 重新启动当前 `master` 的后端 jar**

使用 `ruoyi-admin/target/ruoyi-admin.jar` 做真实运行态验证，记录端口监听、线程数、日志与进程存活情况。

- [ ] **Step 3: 执行读写接口 smoke**

覆盖 `auth/bootstrap/users/settings/appointments/check-slot/public-booking` 以及患者、预约、问诊、公开 token 等关键读写链路，确认修复后服务不再中途掉线。

### Task 3: 补齐前端最小浏览器级冒烟

**Files:**
- Modify: `C:/Users/jiangyi/Desktop/项目/未完成/医院/hospital/package.json`
- Create: `C:/Users/jiangyi/Desktop/项目/未完成/医院/hospital/playwright.config.js`
- Create: `C:/Users/jiangyi/Desktop/项目/未完成/医院/hospital/tests/e2e/public-booking.spec.js`
- Create: `C:/Users/jiangyi/Desktop/项目/未完成/医院/hospital/tests/e2e/appointment-admin.spec.js`
- Create: `C:/Users/jiangyi/Desktop/项目/未完成/医院/hospital/tests/e2e/prescription-completion.spec.js`

- [ ] **Step 1: 加入最小 Playwright 冒烟基础设施**

只增加运行这次验收需要的最小依赖和配置，不引入复杂测试框架层。

- [ ] **Step 2: 覆盖 3 条关键页面路径**

分别验证公开预约、前台预约/排班联动、处方完成竞态这三条路径的关键 UI 行为是否正常。

- [ ] **Step 3: 跑浏览器级 smoke**

在当前主项目上启动前端，串联真实后端环境执行浏览器级冒烟，保留失败截图/日志或通过证据。

### Task 4: 汇总结论与剩余风险

**Files:**
- Modify: `C:/Users/jiangyi/Desktop/项目/未完成/医院/RuoYi-Vue/docs/superpowers/plans/2026-04-06-runtime-stability-and-full-validation.md`

- [ ] **Step 1: 汇总“已修复并实测通过”的范围**

按后端稳定性、后端接口、前端页面三类分别整理证据。

- [ ] **Step 2: 列出仍未覆盖或仍存在风险的点**

如果有未跑到的边界路径或环境依赖，明确标出，不用“全部完成”之类模糊表达掩盖。
