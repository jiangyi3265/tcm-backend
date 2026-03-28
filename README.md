# TCM Clinic Management System - Backend

中医诊所综合管理系统 — 后端服务

## 项目简介

基于 **若依（RuoYi-Vue v3.9.1）** 框架二次开发的中医诊所综合管理系统后端，提供预约挂号、问诊病历、中药方剂、针灸穴位、药材字典、分院管理、文件管理、审计日志等完整业务 API。

## 技术栈

| 层级 | 技术 |
|------|------|
| 框架 | Spring Boot 2.5 |
| ORM | MyBatis + PageHelper |
| 数据库 | MySQL + Druid 连接池 |
| 权限 | Spring Security + JWT |
| 文档 | Swagger / Knife4j |
| 构建 | Maven 多模块 |
| Java | JDK 8+ |

**核心业务模块** `ruoyi-hospital`：预约（Appointment）、问诊（Consultation）、方剂（Formula）、药材（HerbDict）、穴位（Acupoint）、分院（Branch）、知情同意（Consent）、审计日志（AuditLog）等。

## 关联仓库

| 仓库 | 说明 | 链接 |
|------|------|------|
| **tcm-backend** | 后端服务（当前仓库） | — |
| **tcm-app** | 用户端前端（Vue 3 + Element Plus） | [tcm-app](https://github.com/jiangyi3265/tcm-app) |

## 快速启动

```bash
# 1. 克隆项目
git clone https://github.com/jiangyi3265/tcm-backend.git
cd tcm-backend

# 2. 初始化数据库（MySQL）
#    直接执行整合单文件
#    tcm_all_in_one.sql

# 3. 修改数据库连接配置
#    编辑 ruoyi-admin/src/main/resources/application-druid.yml

# 4. 启动
mvn clean install -DskipTests
cd ruoyi-admin
mvn spring-boot:run
# 或使用 ry.sh / ry.bat 脚本启动
```

默认端口 `8080`，Swagger 文档地址：`http://localhost:8080/swagger-ui/index.html`

## 简历描述

> **中医诊所综合管理系统（后端）** — 基于 Spring Boot + MyBatis + MySQL 的中医诊所管理平台后端，在若依框架上扩展 `ruoyi-hospital` 业务模块，实现预约挂号、问诊病历、中药方剂管理、针灸穴位查询、多分院管理、知情同意电子签署、文件管理与审计日志等功能，提供 RESTful API 供前端调用。
