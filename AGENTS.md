# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## 项目说明

本项目是全栈博客平台（LiuTech），文档和注释主要使用中文编写。前后端分离 + Spring Boot 微服务，部署在 Docker Compose 上。

## 工作约定

工作流靠判断力 + `.claude/rules/style.md` 沟通风格，不设强制流程文档。详细约定、命令、跨服务约束、高风险提醒见 `CLAUDE.md`。

## 关键约束

- **JWT_SECRET** 在 backend 和 ai 服务必须完全一致
- **TTS_PROXY_INTERNAL_TOKEN** 在 backend 和 ai 服务必须一致
- **JDBC URL** 必须含 `allowPublicKeyRetrieval=true`
- **AI 服务 → 主后端** Docker 内用 `http://backend:8080`
- **文件上传** 容器内 `/app/uploads` 绑定到宿主机 `/liuxin/uploads`；**不要** `docker compose down -v`（会清空 `mysql_data` 卷）
- **SSE** Nginx 必须 `proxy_buffering off` 并提高 `proxy_read_timeout`；非 SSE 路径不要加 `Accept "text/event-stream"`（会破坏 JSON 响应 406）

## 其它资源

- `CLAUDE.md` — 项目结构、命令、跨服务约束、工作约定
- `README.md` — 产品向介绍
- `LiuTech/API文档.md` — API 参考
- `doc/PRD/` — 历史 PRD 归档
- `doc/记录/` — 当前架构与历史归档（`archive/`）
- `doc/记录/当前架构.md` — 当前生效的总体架构
