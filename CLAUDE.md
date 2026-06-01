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

按影响范围选择，不要机械全跑。完整验证集见 `.claude/project-adapter.md`。

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

## 🤖 AI 工作流契约

本项目使用**轻量规则 + 专用 skill**，核心文件：

```text
.claude/rules/deep-research.md              # 深度研究准则（核心行为）
.claude/rules/ai-development-workflow.md    # 入口规则，定义变更分级
.claude/rules/style.md                      # 沟通与代码风格
.claude/project-adapter.md                  # 项目适配器：模块/目录/验证命令/高风险定义
.claude/skills/prd-workflow                 # 需求共创 → 实现 PRD
.claude/skills/delivery-workflow            # 编码交付 + 验证 + 架构更新
.claude/skills/delivery-workflow/references/excellent-code-index.md  # 项目内优秀范式索引
```

**触发任一 workflow skill 后，先读 `.claude/project-adapter.md`。**

- 日常交流、概念解释、头脑风暴 → **不**主动加载 PRD/架构/规则。
- 用户说"生成/审查 PRD / 先设计方案 / 实现前规划" → `prd-workflow`。
- 用户说"帮我实现 / 开始开发 / 修复 bug / 重构 / 安全整改 / 性能优化 / 写开发记录 / 更新项目架构" → `delivery-workflow`。

**变更分级（来自 `ai-development-workflow.md`）：**

- **极小修**（仅文案/注释/格式，无行为变化）：可不写开发记录，但回复需说明"无行为影响"。
- **小修 / Bug 修复**：可跳过 PRD，但必须写开发记录。
- **普通功能**（满足下方豁免条件时）：可直接进入 `delivery-workflow`，只写开发记录。
- **普通功能**（不满足豁免条件时）：PRD（业务需求 + 实现方案）→ 开发 → 验证 → 写记录。
- **重大 / 高风险**：完整走完 PRD → 开发 → 验证 → 写记录 → 更新 `doc/记录/`。高风险领域包括：认证授权、积分/支付/下载权限、数据库结构、上传下载、AI/SSE/TTS、Nginx、Docker、CI/CD、跨服务调用。**无论大小一律按重大处理。**

**普通功能豁免 PRD 的条件**（全部满足才可豁免）：改动 < 约 200 行；不跨服务；不触及高风险领域；不引入/修改接口协议；不新增库表或迁移脚本。

**过程文件命名**：同一功能在 4 类目录下用**完全一致**的 `功能名_YYYY-MM-DD.md`。

## 📁 文档目录

```text
doc/PRD/功能名_YYYY-MM-DD.md          # 业务需求 + 实现方案合并文档
doc/记录/开发-功能名_YYYY-MM-DD.md    # 实际改了什么、为什么、怎么验证
doc/记录/架构-功能名_YYYY-MM-DD.md    # 单次架构变化
doc/记录/当前架构.md                   # 长期有效的总体架构，定期汇总
```

写实现 PRD / 开发记录前先读 `references/development-record-template.md` 和 `references/implementation-prd-template.md`（在对应 skill 目录下）。

## 🔗 重要资源指针

- `README.md` — 完整功能介绍、特性列表、部署流程（产品向）
- `LiuTech/API文档.md` — 后端 API 完整参考
- `快速部署指南.md` — 生产环境部署步骤
- `doc/记录/当前架构.md` — 当前生效的总体架构
- `.claude/project-adapter.md` — 验证命令、高风险定义、模块表
- `.claude/skills/delivery-workflow/references/excellent-code-index.md` — 优秀代码范式索引
