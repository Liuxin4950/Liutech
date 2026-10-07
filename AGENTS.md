# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## 项目说明

本项目是全栈博客平台（LiuTech），文档和注释主要使用中文编写。前后端分离 + Spring Boot 微服务，部署在 Docker Compose 上。

## 工作约定

工作流靠判断力 + `.claude/rules/style.md` 沟通风格，不设强制流程文档。详细约定、命令、跨服务约束、高风险提醒见 `CLAUDE.md`。

## ⚠️ 改代码前必读（防重复造轮子 / 防方法不统一）

**任何后端代码（主后端 `LiuTech/` 或 AI 服务 `LiuTech-AI/`）任务开始前，必须完成两步：**

1. 先读 **`Docs/架构/跨服务规范.md`** —— 两服务统一编码规范的权威来源（响应 `Result`/`ErrorCode`、异常、构造器注入、Caffeine 声明式缓存、JWT、分层、日志）。
2. 搜索仓库，确认要实现的横切能力（响应封装、错误码、缓存、JWT、分页、日志）**是否已有统一实现**。**禁止**新建第二套同名/同职责的实现；跨服务要遵循同一风格，不因"微服务隔离"各搞一套。

对照 `Docs/架构/跨服务规范.md` 第 8 节「改代码前置检查清单」逐项确认后再动手。依赖注入约定见该文档 §2：普通业务层一律构造器注入；仅安全/过滤器配置与需打破循环依赖处允许 `@Autowired`（配 `@Lazy`），两服务一致。

## 关键约束

- **JWT_SECRET** 只属于主后端；AI 服务禁止读取或自行解析用户 JWT
- **LIUTECH_INTERNAL_TOKEN** 在 backend 和 ai 服务必须一致，用于容器内身份内省、社区内部接口与用户数据清理
- **JDBC URL** 必须含 `allowPublicKeyRetrieval=true`
- **AI 服务 → 主后端** Docker 内用 `http://backend:8080`
- **TTS 完全归 AI 服务**；主后端不得新增 TTS 配置、推理、音色或音频缓存逻辑
- **文件上传** 容器内 `/app/uploads` 绑定到宿主机 `/liuxin/uploads`；**不要** `docker compose down -v`（会清空 `mysql_data` 卷）
- **SSE** Nginx 必须 `proxy_buffering off` 并提高 `proxy_read_timeout`；非 SSE 路径不要加 `Accept "text/event-stream"`（会破坏 JSON 响应 406）

## 其它资源

- `CLAUDE.md` — 项目结构、命令、跨服务约束、工作约定
- `README.md` — 产品向介绍（含 API 概览）
- `Docs/架构/后端/AI服务/接口参考.md` — AI 服务接口参考
- `Docs/README.md` — 文档统一入口；维护规则见 `Docs/AGENTS.md`
- `Docs/归档/README.md` — 历史评估、需求与未实施方案
- `Docs/架构/总览.md` — 当前生效的总体架构
- `Docs/维护清单.md` — 已核实的维护事项与待验证边界
- `Docs/SQL/sql.sql` — 两个数据库的唯一完整初始化脚本
- `Docs/架构/README.md` — 模块化架构文档索引

功能、接口、配置或数据库行为改变后同步相应现行文档，完成后运行 `node scripts/check-docs.mjs`。历史材料不作为当前实现依据。

## 数据库升级入口

`Docs/SQL/sql.sql` 仍是唯一完整初始化快照；增量发布使用 `Docs/SQL/migrations/README.md` 与 `scripts/migrate.ps1`。已发布迁移长期保留且不可修改；应用不执行 DDL。修改数据库结构时同步增量、快照、启动结构检查和真实 MySQL 回归，发布前先迁移再启动新版。
