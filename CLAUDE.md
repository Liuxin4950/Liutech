# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目说明

本项目是一个**全栈博客平台**（LiuTech），文档和注释主要使用中文编写。前后端分离 + Spring Boot 微服务，部署在 Docker Compose 上。

技术栈关键事实：后端 **Java 21** + Spring Boot 3.5 + MyBatis-Plus；缓存用 **Caffeine 本地多级 TTL**（文章 5min / 标签 10min / 分类 15min），**未引入 Redis**，别建议加 Redis 或写 Redis 代码；前端 Vue 3 + TS + Vite；数据库 MySQL 8（两个库：`liutech` 主库、`liutech_ai` AI 库）。

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

后端分层（`LiuTech/src/main/java/chat/liuxin/liutech/`）：`controller/{admin,web}` -> `service` -> `mapper` -> `model`，横切关注点放在 `common/`、`config/`、`aspect/`、`filter/`；AI 服务结构类似但包名为 `chat.liuxin.ai`。

前端分层（`Web/src/` 与 `Admin/src/`）：`views/`（页面）-> `components/`（复用）-> `services/`（API）-> `stores/`（Pinia）-> `router/`、`composables/`、`utils/`。Web 含 Live2D、TinyMCE、看板娘聊天；Admin 以表格/表单 CRUD 为主。

## 🛠️ 如何运行

**本地开发**：两个 Spring Boot 服务需 JDK 21，环境变量从根目录 `.env` 加载（真实密钥在 `.env`，已 gitignore 不入库）。

```bash
# bash：每次新 shell 先从 .env 加载环境变量
set -a && source .env && set +a
cd LiuTech && mvn spring-boot:run          # 主后端 :8080
cd LiuTech-AI && mvn spring-boot:run       # AI 服务 :8081
```

`JWT_SECRET` 与 `TTS_PROXY_INTERNAL_TOKEN` 在两个服务必须一致，否则 token 验证 / TTS 代理失败。

AI 要自己启动项目验证：开发完接口后本地启动 + 实时读日志 + 调接口测试，小问题自行解决，避免麻烦用户。token 通过登录接口获取（请求参数：用户名 + 密码，返回 token）。

**生产部署**：见 `.claude/skills/deploy.md`（连服务器、构建上传镜像、重启容器）。

⚠️ 真实密钥只在本地 `.env`，**不要**写入 CLAUDE.md 或任何入库文件。

## 📦 构建与测试

```bash
# 后端（本机 mvn 默认 Java 8，需设 JAVA_HOME 指向 JDK 21）
cd LiuTech && mvn test                       # 单元测试
cd LiuTech && mvn test -Dtest=类名#方法      # 跑单个测试类/方法
cd LiuTech && mvn clean package -DskipTests # 构建 JAR
mvn clean install -DskipTests                # 从根构建所有后端模块

# 前端
cd Web && npm run dev                        # 起 Web 开发服务器
cd Web && npm run build                      # 生产构建
cd Admin && npm run build                    # Admin 构建

# 数据库
mysql -u root -p < sql/sql.sql               # 初始化两个库
docker exec -it liutech-mysql mysql -u root -p

# 全栈部署（Windows 一键）
./快速打包文件.bat && docker-compose up -d
docker-compose logs -f backend               # 跟踪后端日志
```

## ⚠️ 跨服务集成约束（最容易出错的点）

- **`JWT_SECRET`** 在 `backend` 和 `ai` 服务中**必须完全一致**，否则 token 验证失败。
- **`TTS_PROXY_INTERNAL_TOKEN`** 在 `backend` 和 `ai` 服务中**必须一致**，AI 服务通过 `/tts/speech` 代理调用主后端 TTS。
- **AI 服务 -> 主后端** URL：Docker 内 `http://backend:8080`（`BLOG_API_URL`），本地 `http://localhost:8080`。
- **JDBC URL** 必须含 `allowPublicKeyRetrieval=true`，兼容 MySQL 8 认证。
- **文件上传**：容器内 `/app/uploads` 绑定宿主机 `/liuxin/uploads`；**不要** `docker compose down -v`（清空 `mysql_data` 卷）。
- **图片 URL 策略**：`FileUtil.generateFileUrl` 返回**相对路径** `/uploads/...`，不拼 `serverBaseUrl`；数据库存相对路径，环境无关。**不要**为"开发环境图片显示不了"改 `.env` 的 `SERVER_BASE_URL`。详见 [当前架构.md](Docs/记录/当前架构.md)。
- **SSE（AI 流式响应）** Nginx 必须 `proxy_buffering off;` 并提高 `proxy_read_timeout`；**不要**给非 SSE 路径加 `proxy_set_header Accept "text/event-stream";`（破坏 JSON 响应 406）。
- **域名拓扑**：主站 `liuxin.chat` 走腾讯云 CDN 回源 443；后台 `admin.liuxin.chat` A 记录直连源站绕开 CDN（443）。证书 SAN 含 `liuxin.chat`/`www.liuxin.chat` 但**不含 admin 子域名**，浏览器报名称不匹配需手动继续，故 admin 站**不发 HSTS**；81 端口为其备用入口。详见 [部署运维总览](Docs/架构/运维/部署运维/总览.md)。
- **HTTPS 证书**位置（生产）：`/opt/liutech/nginx/liuxin.chat_bundle.crt` 与 `liuxin.chat.key`。
- **nginx 配置烤进镜像**：`conf.d/*.conf` 是 `COPY` 进 nginx 镜像的，改 nginx 配置后**必须重新 build nginx 镜像并部署**，改宿主机文件不生效。
- **CORS allowedOrigins 双服务同步**：新增前端访问域名时，`LiuTech` 与 `LiuTech-AI` 两个 `SecurityConfig.java` 的 `allowedOrigins` 必须同步添加，否则该域名跨域请求被拦。
- **环境变量**从根目录 `.env` 注入；`.env.example` 是模板，生产替换为强密钥。
- **开发/生产 compose 区分**：`docker-compose.override.yml` 仅本地开发，暴露 mysql/backend/ai/web/admin 宿主机端口便于调试，`docker compose up` 自动合并；生产**不上传此文件**，只 `docker-compose.yml`，仅 nginx 暴露 80/443/81。

## 🤖 工作约定

工作流靠判断力 + [`.claude/rules/style.md`](.claude/rules/style.md) 沟通风格，不设强制流程文档。复杂功能先口头确认方案再动手，做完按规范提交。

**高风险领域**（认证授权、积分支付、数据库结构、上传下载、AI/SSE/TTS、Nginx/Docker/部署、跨服务调用）改动要特别谨慎：先读相关代码和 [当前架构.md](Docs/记录/当前架构.md)，确认影响范围再动手，不猜测。

**架构文档**：新增或大改功能模块时，在 `Docs/架构/<模块>/` 下补充文档并更新 `Docs/架构/README.md` 索引；每个模块以「总览.md」为入口，单一领域文档不超过 500 行。

**Commit message 规范**（过程产物的「为什么」「怎么验证」由 commit 承担，不单独写开发记录文件）：

```
<type>(<scope>): <subject>

为什么: <动机或根因>
验证: <命令或方式>
```

## 📝 日志规范

- **安全审计**（登录/注册/改密/验证码/邮箱登录/重置密码）：`log.info`，不可降级或删除。web 端用户操作无 @OperationLog，log 是唯一审计手段。
- **资产变动**（积分扣减/增加、资源购买消费）：`log.info`。
- **数据删除/不可逆操作**（彻底删除、定时清理、清空记忆）：`log.info` 记执行结果。
- **AI 端管理操作**（AiModel 增删改）：`log.info`（AI 端无 AOP，日志是唯一审计）。
- **管理端 CRUD**（admin controller 有 @OperationLog 的写操作）：Service 层可 `log.debug`，AOP 兜底审计。
- **高频读**（首页/列表/查询/sitemap）：`log.debug` 或不打。
- **请求链路/认证**（每请求触发）：`log.debug`；慢请求（>1s）`log.warn`。
- **异常**：业务异常 `log.warn` / 系统异常 `log.error`，不可删除。
- **级别配置**：生产 `root: INFO`；不要把业务包整体开 `DEBUG`（Spring AI 的 DEBUG 会打 prompt 明文，IO 开销大）。

## 🔗 重要资源指针

- `README.md` - 完整功能介绍、特性列表、部署流程（产品向）
- `LiuTech/src/main/java/chat/liuxin/liutech/controller/` - 后端 API 完整参考
- `快速部署指南.md` - 生产环境部署步骤
- `Docs/记录/当前架构.md` - 当前生效的总体架构
- `Docs/架构/README.md` - 模块化架构文档索引，接手某模块先读对应目录的「总览.md」
- `AGENTS.md` - 给 Codex 的精简指引，指向本文件；改动约定时两处保持同步
- `.claude/skills/` - `deploy.md`（部署步骤）、`docs-architecture/`（架构文档维护规范）
- `Docs/` 子目录：`架构/`（模块文档）、`记录/`（当前架构 + 历史归档）、`PRD/`、`SQL/`、`团队反馈/`

## 🧠 GBrain 持久知识库

本机已配置 gbrain（本地 PGLite + Ollama bge-m3 embedding），Liutech 代码已索引，支持语义搜索。

**配置：**
- Mode: local-stdio（`gbrain serve` 作为 MCP，user scope 注册）
- Engine: pglite（单进程嵌入式，单写者）
- Embedding: `ollama:bge-m3`（1024 维，本地 Ollama，无 API 成本）
- Code source: `gstack-code-liutech-1ab86efa`（542 pages，7116 chunks，100% embedded，2026-08-02 首次同步）
- 排除目录：`Web/public/tinymce/**`、`Web/public/live2d/**`、`Admin/public/tinymce/**`（第三方 vendored，不索引）
- Repo policy: read-write
- 配置文件：`~/.gbrain/config.json`（mode 0600，含 database_path/embedding_model）

**搜索指导：**
- 语义/符号不明确时优先 gbrain：`gbrain search "<词>"`、`gbrain query "<问题>"`
- 已知精确串/正则/多行/文件 glob：仍用 Grep
- 当前目录已 attach 到 `gstack-code-liutech-1ab86efa`（`.gbrain-source` 已 gitignore），CLI 命令默认用此 source
- 增量同步在 gstack skill 启动时自动跑；强制刷新 `/sync-gbrain`，全量重建 `/sync-gbrain --full`

**⚠️ PGLite 单写者约束（重要）：** CLI 命令（`gbrain sync`/`import`/`doctor`/`sources`）与 `gbrain serve`（MCP）不能同时打开数据库。若 CLI 报 "already open through gbrain serve" 或 doctor 报 broken-config，先停 serve（`taskkill //F //PID <bun.exe PID>`），跑完 CLI 再重连 MCP（重启 Claude Code 或 `/mcp`）。serve 进程退出后 PGLite 锁会自动回收。

**已知限制：** 当前 schema pack 是 `gbrain-base-v2`，不抽取代码符号，`gbrain code-def`/`code-refs`/调用图无结果（语义搜索 `search`/`query` 正常）。需要符号查询要迁移到 code-aware pack（大迁移，未做）。
