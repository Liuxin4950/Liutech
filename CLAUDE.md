# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目说明

本项目是一个**全栈博客平台**（LiuTech），文档和注释主要使用中文编写。前后端分离 + Spring Boot 微服务，部署在 Docker Compose 上。

## 🏗️ 顶层模块

| 路径 | 角色 | 端口 |
| --- | --- | --- |
| `Web/` | 用户前台博客（Vue 3 + TS + Vite） | 3000 |
| `Admin/` | 管理后台（Vue 3 + Ant Design Vue） | 3001 |
| `LiuTech/` | 主后端 REST API（Spring Boot 3.5.6 + MyBatis-Plus） | 8080 |
| `LiuTech-AI/` | AI 聊天 / 推荐 / TTS 服务（Spring Boot） | 8081 |
| `nginx/` | 反向代理、CORS、SSE 配置 | 80/443 |
| `sql/sql.sql` | MySQL 初始化脚本（两个库：`liutech` 与 `liutech_ai`） | 3306 |

容器内部使用服务名通信：`backend:8080`、`ai:8081`、`mysql:3306`。完整编排见 `docker-compose.yml`。

后端分层（`LiuTech/src/main/java/chat/liuxin/liutech/`）：`controller/{admin,web}` → `service` → `mapper` → `model`，横切关注点放在 `common/`、`config/`、`aspect/`、`filter/`；AI 服务结构类似但包名为 `chat.liuxin.ai`。

前端分层（`Web/src/` 与 `Admin/src/`）：`views/`（页面）→ `components/`（复用）→ `services/`（API）→ `stores/`（Pinia）→ `router/`、`composables/`、`utils/`。Web 含 Live2D、TinyMCE、看板娘聊天；Admin 以表格/表单 CRUD 为主。

## 🛠️ 常用命令

按影响范围选择，不要机械全跑。

```bash
# 后端单服务
cd LiuTech && mvn test                       # 单元测试
cd LiuTech && mvn clean package -DskipTests # 构建 JAR
cd LiuTech-AI && mvn spring-boot:run        # 本地起 AI 服务

# 前端
cd Web && npm run dev                        # 起 Web 开发服务器
cd Web && npm run build                      # 生产构建（npm test 当前未配置）
cd Admin && npm run build                    # Admin 构建

# 全栈
mvn clean install -DskipTests                # 从根构建所有后端模块
./快速打包文件.bat && docker-compose up -d   # Windows 一键构建并启动
docker-compose logs -f backend               # 跟踪后端日志

# 数据库
mysql -u root -p < sql/sql.sql               # 一次性初始化两个库
docker exec -it liutech-mysql mysql -u root -p
```

## ⚠️ 跨服务集成约束（最容易出错的点）

- **`JWT_SECRET`** 在 `backend` 和 `ai` 服务中**必须完全一致**，否则 token 验证失败。
- **`TTS_PROXY_INTERNAL_TOKEN`** 在 `backend` 和 `ai` 服务中**必须一致**，AI 服务通过 `/tts/speech` 代理调用主后端 TTS。
- **AI 服务 → 主后端** URL：在 Docker 内是 `http://backend:8080`（`BLOG_API_URL`），本地开发用 `http://localhost:8080`。
- **JDBC URL** 必须含 `allowPublicKeyRetrieval=true`，兼容 MySQL 8 认证。
- **文件上传**：容器内 `/app/uploads` 绑定到宿主机 `/liuxin/uploads`；**不要**用 `docker compose down -v`，会清空 `mysql_data` 卷。
- **SSE（AI 流式响应）** Nginx 必须 `proxy_buffering off;` 并提高 `proxy_read_timeout`；**不要**给非 SSE 路径加 `proxy_set_header Accept "text/event-stream";`，会破坏 JSON 响应（406）。
- **HTTPS 证书**位置（生产）：`/opt/liutech/nginx/liuxin.chat_bundle.crt` 与 `liuxin.chat.key`。
- **环境变量**从根目录 `.env` 注入；`.env.example` 是模板，生产替换为强密钥。

## 🤖 工作约定

工作流靠判断力 + [`.claude/rules/style.md`](.claude/rules/style.md) 沟通风格，不设强制流程文档。复杂功能先口头确认方案再动手，做完按规范提交。

**高风险领域**（认证授权、积分支付、数据库结构、上传下载、AI/SSE/TTS、Nginx/Docker/部署、跨服务调用）改动要特别谨慎：先读相关代码和 [当前架构.md](doc/记录/当前架构.md)，确认影响范围再动手，不猜测。

**Commit message 规范**（过程产物的「为什么」「怎么验证」由 commit 承担，不单独写开发记录文件）：

```
<type>(<scope>): <subject>

为什么: <动机或根因>
验证: <命令或方式>
```

## 🔗 重要资源指针

- `README.md` — 完整功能介绍、特性列表、部署流程（产品向）
- `LiuTech/src/main/java/chat/liuxin/liutech/controller/` — 后端 API 完整参考
- `快速部署指南.md` — 生产环境部署步骤
- `doc/记录/当前架构.md` — 当前生效的总体架构
