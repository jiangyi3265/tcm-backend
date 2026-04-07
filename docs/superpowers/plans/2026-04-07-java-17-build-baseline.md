# Java 17 Build Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 RuoYi 后端父工程的 Maven 编译基线统一升级到 Java 17，并验证 `ruoyi-hospital` 与全量模块都能在该基线下成功打包。

**Architecture:** 只修改父级 `pom.xml`，把编译基线从 Java 8 提升到 Java 17，并将编译插件切换到 `release` 模式，让命令行 Maven、IDE 与潜在 CI 对语言级别和 API 可用性的判断一致。业务代码与模块结构保持不变，先做 `ruoyi-hospital` 定点验证，再做全量 Reactor 验证，确保影响面被控制在构建配置层。

**Tech Stack:** Maven、Java 17、Apache Maven Compiler Plugin、Spring Boot 2.5.15

---

### Task 1: 统一父工程编译基线到 Java 17

**Files:**
- Modify: `pom.xml`
- Test: `mvn -v`
- Test: `mvn -pl ruoyi-hospital -DskipTests package`

- [ ] **Step 1: 先确认 Maven 当前实际使用的是 JDK 17**

Run:

```bash
mvn -v
```

Expected:

```text
Java version: 17
```

- [ ] **Step 2: 修改属性区，把项目声明的 Java 版本切到 17，并补上编译插件版本属性**

将 `pom.xml` 中的属性片段更新为：

```xml
<properties>
    <ruoyi.version>3.9.1</ruoyi.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
    <java.version>17</java.version>
    <maven-compiler-plugin.version>3.13.0</maven-compiler-plugin.version>
    <maven-jar-plugin.version>3.1.1</maven-jar-plugin.version>
    <spring-boot.version>2.5.15</spring-boot.version>
    <druid.version>1.2.27</druid.version>
    <yauaa.version>7.32.0</yauaa.version>
    <swagger.version>3.0.0</swagger.version>
    <kaptcha.version>2.3.3</kaptcha.version>
    <pagehelper.boot.version>1.4.7</pagehelper.boot.version>
    <fastjson.version>2.0.60</fastjson.version>
    <oshi.version>6.9.1</oshi.version>
    <commons.io.version>2.21.0</commons.io.version>
    <poi.version>4.1.2</poi.version>
    <velocity.version>2.3</velocity.version>
    <jwt.version>0.9.1</jwt.version>
    <!-- override dependency version -->
    <tomcat.version>9.0.112</tomcat.version>
    <logback.version>1.2.13</logback.version>
    <spring-security.version>5.7.14</spring-security.version>
    <spring-framework.version>5.3.39</spring-framework.version>
</properties>
```

- [ ] **Step 3: 升级 `maven-compiler-plugin` 配置，使用 `release=17` 替代 `source/target`**

将 `pom.xml` 中编译插件片段更新为：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>${maven-compiler-plugin.version}</version>
    <configuration>
        <release>${java.version}</release>
        <encoding>${project.build.sourceEncoding}</encoding>
    </configuration>
</plugin>
```

- [ ] **Step 4: 运行医院模块定点构建，验证 Java 17 编译基线生效**

Run:

```bash
mvn -pl ruoyi-hospital -DskipTests package
```

Expected:

```text
[INFO] BUILD SUCCESS
```

并确认输出里不再出现基于 Java 8 API 基线的 `List.of(...)` 相关误报。

- [ ] **Step 5: 提交仅包含父 `pom.xml` 的构建基线变更**

Run:

```bash
git add pom.xml
git commit -m "build: align maven baseline to java 17"
```

Expected:

```text
[master ...] build: align maven baseline to java 17
 1 file changed, ...
```

### Task 2: 做全量 Reactor 打包验证

**Files:**
- Verify: `pom.xml`
- Test: `mvn -DskipTests package`

- [ ] **Step 1: 运行全量 Maven 打包，确认所有模块继承新的 Java 17 基线**

Run:

```bash
mvn -DskipTests package
```

Expected:

```text
[INFO] Reactor Summary:
...
[INFO] BUILD SUCCESS
```

- [ ] **Step 2: 如果全量构建失败，只记录第一个失败模块和第一条真实编译错误，不要顺手扩散修复范围**

记录格式保持为：

```text
failing module: <module-name>
first error: <compiler/plugin error line>
```

本任务的目标是确认 Java 17 基线升级是否已经足够，不在没有证据的情况下扩大到 Boot、依赖或业务代码升级。

- [ ] **Step 3: 若全量构建成功，确认无需对子模块 `pom.xml` 做额外覆盖**

Run:

```bash
rg -n "<artifactId>maven-compiler-plugin</artifactId>|<release>|<source>|<target>|<java.version>" -g pom.xml .
```

Expected:

```text
pom.xml:...
```

只要结果仍然只落在父 `pom.xml`，就说明本次“父工程统一编译基线”的设计成立。
