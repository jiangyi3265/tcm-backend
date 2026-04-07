# Java 17 Build Baseline Design

**日期：** 2026-04-07

## 背景

当前后端工程运行环境已经切到 JDK 17，但父级 [pom.xml](C:\Users\jiangyi\Desktop\项目\未完成\医院\RuoYi-Vue\pom.xml) 仍声明 `<java.version>1.8</java.version>`，并使用较老的 `maven-compiler-plugin:3.1` 通过 `source/target` 方式编译。这个组合会带来两个直接问题：

1. IDE、命令行 Maven、潜在 CI 对 Java API 可用性的判断不一致。
2. 项目里已经出现 `List.of(...)` 这类 Java 9+ API，用旧基线时会产生“找不到符号”类误报或真实编译失败。

本次目标不是升级业务框架，而是把构建基线与实际 JDK 版本统一到 Java 17，消除版本漂移。

## 目标

- 将 Maven 编译基线统一升级到 Java 17。
- 让命令行 Maven、IDE 与后续 CI 对 Java 语言级别和标准库 API 的判断保持一致。
- 保持现有模块结构、依赖版本和业务代码行为不变。

## 非目标

- 不升级 Spring Boot 主版本。
- 不重构业务代码。
- 不处理与 Java 17 无关的历史依赖治理问题。

## 方案对比

### 方案 A：仅升级 Maven 编译基线到 Java 17

做法：

- 把父 `pom` 的 `<java.version>` 从 `1.8` 改成 `17`。
- 将 `maven-compiler-plugin` 升级到支持 Java 17 的版本。
- 用 `<release>${java.version}</release>` 替代单独的 `source/target`。

优点：

- 改动最小。
- 与本机 JDK 17 一致。
- 可以从构建层面明确声明 Java 17 API 可用。

缺点：

- 若个别依赖或插件隐含依赖旧 JDK 行为，会在验证阶段暴露出来，需要单独处理。

### 方案 B：保持 Java 8 基线，回退所有 Java 9+ API

做法：

- 保留 `<java.version>1.8</java.version>`。
- 将所有 `List.of(...)` 等写法改回 Java 8 等价实现。

优点：

- 兼容旧环境。

缺点：

- 与当前 JDK 17 环境不一致。
- 后续仍可能重复出现版本歧义。

### 方案 C：连 Spring Boot 与插件链一起整体升级

做法：

- 同步升级 Java、Boot、测试与插件版本。

优点：

- 长期结构更干净。

缺点：

- 范围明显扩大，不适合本次只解决构建基线统一的问题。

## 决策

采用方案 A。

原因：

- 它直接解决当前“JDK 17 运行，Java 8 构建声明”的根因。
- 变更面控制在父 `pom`，风险最小。
- 不把任务扩散到框架升级，符合 KISS 原则。

## 详细设计

### 1. 编译基线

在父 [pom.xml](C:\Users\jiangyi\Desktop\项目\未完成\医院\RuoYi-Vue\pom.xml) 中：

- 将 `<java.version>` 调整为 `17`。
- 新增或复用统一的编译插件版本属性，避免在插件声明里写死旧版本。

### 2. 编译插件配置

升级 `maven-compiler-plugin` 至支持 Java 17 的稳定版本，并将配置调整为：

- 使用 `<release>${java.version}</release>`。
- 保留 `<encoding>${project.build.sourceEncoding}</encoding>`。

这样做的目的：

- `release` 会同时约束语言级别和目标平台 API。
- 避免 `source/target` 在高版本 JDK 下出现“语法限制了但 API 仍可能漏检”的不一致行为。

### 3. 影响范围

预期受影响文件只有父 [pom.xml](C:\Users\jiangyi\Desktop\项目\未完成\医院\RuoYi-Vue\pom.xml)。

各子模块通过父工程继承统一编译设置，不需要分别改动模块 `pom`，除非验证时发现个别模块覆盖了编译插件配置。

### 4. 兼容性预期

- `List.of(...)`、`Map.of(...)` 等 Java 9+ API 在 Java 17 基线下将被正式支持。
- 现有 Spring Boot 2.5.15 在 Java 17 上通常可运行，但本次只对构建通过负责，不额外承诺运行期所有第三方依赖都已最佳化。

## 验证计划

最小验证分两层：

1. 模块验证  
在后端根目录执行：

```bash
mvn -pl ruoyi-hospital -DskipTests package
```

目的：

- 确认医院模块在 Java 17 编译基线下可正常编译打包。

2. 全量验证  
如模块验证通过，再执行：

```bash
mvn -DskipTests package
```

目的：

- 确认所有子模块都能继承并接受新的 Java 17 构建基线。

## 风险与应对

### 风险 1：旧插件或模块对新版编译插件不兼容

应对：

- 先做 `ruoyi-hospital` 定点验证。
- 若全量构建失败，再根据失败模块做最小兼容修正，而不是提前扩大改动面。

### 风险 2：个别模块源码隐含 Java 8 假设

应对：

- 通过全量 Maven 构建暴露。
- 仅针对报错点做必要修复，不进行无关重构。

## 成功标准

- 父 `pom` 明确声明 Java 17 为统一构建基线。
- `maven-compiler-plugin` 使用 `release=17`。
- `mvn -pl ruoyi-hospital -DskipTests package` 成功。
- 若执行全量验证，则 `mvn -DskipTests package` 成功。
