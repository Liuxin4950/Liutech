# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目说明

本项目是一个**全栈博客平台**，文档和注释主要使用中文编写。

## 🏗️ 系统架构

这是一个**全栈博客平台**，采用微服务架构：

- **前端**: Vue 3 + TypeScript 应用
  - `Web/` - 用户前台博客 (端口 3000)
  - `Admin/` - 管理后台 (端口 3001)

- **后端**: Spring Boot 微服务
  - `LiuTech/` - 主后端 API 服务 (端口 8080)
  - `LiuTech-AI/` - AI 聊天助手服务 (端口 8081)

- **数据库与缓存**: MySQL 8.0 + 可选 Redis
- **反向代理**: Nginx 负载均衡和路由
- **容器化**: 完整的 Docker Compose 配置

### Service Dependencies
```
Nginx (80/443)
  ├── Web Frontend (3000)
  ├── Admin Frontend (3001)
  ├── LiuTech Backend (8080)
  └── LiuTech-AI Service (8081)
        └── MySQL (3306)
```

## 🛠️ 常用开发命令

### 后端 (Spring Boot)

**主后端服务 (LiuTech):**
```bash
cd LiuTech
mvn clean compile                    # 编译 Java 代码
mvn spring-boot:run                  # 开发模式运行
mvn test                             # 运行单元测试
mvn test -Dtest=UserControllerTest   # 运行指定测试
mvn clean package -DskipTests        # 构建生产 JAR
java -jar target/liutech-backend-*.jar  # 运行编译后的 JAR
```

**AI 服务 (LiuTech-AI):**
```bash
cd LiuTech-AI
mvn clean compile
mvn spring-boot:run
mvn test
mvn clean package -DskipTests
java -jar target/liutech-ai-*.jar
```

**父模块 (所有后端服务):**
```bash
# 从项目根目录构建所有模块
mvn clean install -DskipTests        # 构建所有模块
mvn test                             # 运行所有测试
mvn clean install                    # 构建并运行测试
```

### 前端 (Vue 3)

**用户前台 (Web):**
```bash
cd Web
npm install                          # 安装依赖
npm run dev                          # 启动开发服务器 (端口 3000)
npm run build                        # 生产构建
npm run preview                      # 预览生产构建
```

**管理后台 (Admin):**
```bash
cd Admin
npm install
npm run dev                          # 启动开发服务器 (端口 3001)
npm run build
npm run preview
```

**安装所有前端依赖:**
```bash
cd Web && npm install && cd ../Admin && npm install
```

### Docker 开发

**构建所有镜像:**
```bash
./快速打包文件.bat        # Windows 构建脚本
```

**启动全栈:**
```bash
docker-compose up -d        # 启动所有服务
docker-compose up -d mysql  # 仅启动 MySQL
docker-compose ps           # 检查服务状态
docker-compose logs -f      # 查看日志
docker-compose down         # 停止所有服务
```

**查看日志:**
```bash
docker-compose logs -f backend
docker-compose logs -f ai
docker-compose logs -f web
```

### 数据库

**初始化数据库:**
```sql
CREATE DATABASE liutech CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE liutech_ai CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
mysql -u root -p liutech < sql/sql.sql
mysql -u root -p liutech_ai < sql/ai_chat_tables.sql
```

## 📁 关键目录与文件

### 后端结构 (LiuTech/)
```
src/main/java/chat/liuxin/liutech/
├── controller/          # REST 接口层
│   ├── admin/          # 管理后台 API
│   └── web/            # 公开 API
├── service/            # 业务逻辑层
├── mapper/             # MyBatis 数据访问层
├── model/              # 数据模型 (User, Post 等)
├── config/             # Spring 配置类
├── common/             # 公共工具类
└── aspect/             # AOP 切面
```

### 前端结构 (Web/ & Admin/)
```
src/
├── views/              # 页面组件
├── components/         # 可复用组件
├── stores/             # Pinia 状态管理
├── services/           # API 服务层
├── router/             # Vue Router 配置
├── composables/        # Vue 组合式函数
├── utils/              # 工具函数
└── assets/             # 静态资源
```

### 配置文件
- `pom.xml` - Maven 父模块 (依赖管理)
- `LiuTech/pom.xml` - 主后端依赖
- `LiuTech-AI/pom.xml` - AI 服务依赖
- `Web/package.json` - 前端依赖 (Web)
- `Admin/package.json` - 前端依赖 (Admin)
- `docker-compose.yml` - 服务编排
- `.env` - 环境变量

## 🔧 配置

### 环境变量 (.env)
```bash
# 数据库
DB_ROOT_PASSWORD=123456          # MySQL root 密码
MYSQL_PORT=3306

# 服务端口
BACKEND_PORT=8080
AI_PORT=8081
WEB_PORT=3000
ADMIN_PORT=3001
NGINX_HTTP=80
NGINX_HTTPS=443

# AI 服务
SPRING_AI_OPENAI_API_KEY=your_api_key    # AI 服务必需

# JWT (重要：后端和 AI 服务必须使用相同的密钥)
JWT_SECRET=your_strong_jwt_secret_key_min_32_chars    # 生产环境必需

# 文件上传 (Docker)
FILE_UPLOAD_BASE_PATH=/app/uploads           # 容器内路径
# 文件实际存储在宿主机 /liuxin/uploads 目录 (挂载到容器 /app/uploads)

# 服务器
SERVER_BASE_URL=http://liuxin.chat           # 应用基础 URL
```

**重要提示:**
- `JWT_SECRET` 在 `backend` 和 `ai` 服务中必须完全相同，否则 token 验证会失败
- AI 服务使用 SiliconFlow API 密钥: https://www.siliconflow.com/
- 文件上传持久化在宿主机 `/liuxin/uploads` 目录 (bind mount 到容器)
- **HTTPS (生产环境)**: SSL 证书应放置在服务器的 `/opt/liutech/nginx/` 目录:
  - `/opt/liutech/nginx/liuxin.chat_bundle.crt` - SSL 证书
  - `/opt/liutech/nginx/liuxin.chat.key` - SSL 私钥

### 后端配置 (application.yml)
关键配置位于 `LiuTech/src/main/resources/`:
- `application.yml` - 基础配置
- `application-dev.yml` - 开发环境 (本地 MySQL)
- `application-prod.yml` - 生产环境 (Docker 网络)
- 文件上传设置 (最大 100MB)
- JWT 配置 (7 天过期)
- MyBatis-Plus 分页配置

### 前端配置 (.env.development)
```bash
VITE_API_BASE_URL=http://127.0.0.1:8080
```

## 🎯 核心功能

### 用户系统
- JWT 认证
- 基于角色的访问控制 (user/admin)
- 用户注册/登录
- 个人资料管理与头像上传

### 内容管理
- 富文本编辑器 (TinyMCE 7.9.1)
- 文章 CRUD 操作
- 分类和标签
- 文件/图片上传
- 草稿和发布状态

### AI 助手
- 聊天式 AI 集成 (LiuTech-AI 服务)
- 上下文感知对话
- 内容写作辅助

### 管理功能
- 用户管理
- 内容审核
- 系统统计
- 分类/标签管理

## 🔌 API 架构

### 基础 URL
- 主 API: `http://localhost:8080` (backend)
- AI API: `http://localhost:8081` (ai)
- Docker 内部: `http://backend:8080`, `http://ai:8081`

### 认证
所有受保护的路由需要在请求头中携带 JWT token:
```
Authorization: Bearer {token}
```
Token 有效期为 7 天。后端和 AI 服务使用相同的 JWT_SECRET 进行验证。

### 主要接口
- `POST /user/login` - 用户登录 (返回 JWT token)
- `POST /user/register` - 用户注册
- `GET /posts` - 获取文章列表 (带分页)
- `POST /posts` - 创建文章 (需要认证)
- `GET /posts/{id}` - 获取文章详情
- `GET /admin/users` - 用户管理 (仅管理员)
- `POST /ai/chat` - AI 聊天 (SSE 流式响应)

完整 API 文档: `LiuTech/API文档.md`

## 🚀 部署

### 快速启动 (推荐)
```bash
./快速打包文件.bat    # 构建所有组件
docker-compose up -d  # 启动全栈
```

访问地址:
- 用户前台: http://localhost:3000
- 管理后台: http://localhost:3001
- API: http://localhost:8080
- AI 服务: http://localhost:8081

### 生产环境部署
1. 本地构建: `.\快速打包文件.bat`
2. 导出镜像: `.\镜像导出脚本.bat` (可选)
3. 上传到服务器: `/opt/liutech/`
4. 运行: `chmod +x 服务器部署脚本.sh && ./服务器部署脚本.sh`
5. 配置 `.env` 文件中的 JWT_SECRET 和 SPRING_AI_OPENAI_API_KEY
6. 重启服务: `docker compose restart backend ai`

详细生产部署说明请参考: 快速部署指南.md

### 生产构建
```bash
# 后端
cd LiuTech && mvn clean package -DskipTests
cd LiuTech-AI && mvn clean package -DskipTests

# 前端
cd Web && npm run build
cd Admin && npm run build

# 使用 Docker 部署
docker-compose up -d
```

注意: 构建脚本 `快速打包文件.bat` 会自动处理以上所有步骤。

## 🧪 测试

### 后端测试
```bash
# 运行所有测试
mvn test

# 运行指定测试类
mvn test -Dtest=UserServiceTest

# 运行测试并生成覆盖率报告
mvn test jacoco:report
```

### 前端测试
前端使用 Vite - 目前未配置测试框架。

## 🐛 调试

### 后端日志
```bash
# 开发模式
mvn spring-boot:run  # 日志输出到控制台

# Docker
docker-compose logs -f backend
docker-compose logs -f ai
docker-compose logs -f mysql
```

### 前端开发工具
```bash
npm run dev  # Vite 开发服务器，支持 HMR
```

### 常见问题
- **AI 服务返回 406 Not Acceptable**: Nginx 配置问题 - 确保没有设置 `proxy_set_header Accept "text/event-stream";` (这会破坏非 SSE 的 AI 请求)
- **AI 服务无法连接后端**: 检查 AI 服务配置中的 `BLOG_API_URL=http://backend:8080`
- **JWT token 验证失败**: 确保 `JWT_SECRET` 在后端和 AI 服务中完全相同
- **文件上传不持久**: 检查宿主机上是否存在 bind mount `/liuxin/uploads:/app/uploads`
- **HTTPS 不工作**: 确保 SSL 证书位于 `/opt/liutech/nginx/liuxin.chat_bundle.crt` 和 `liuxin.chat.key`
- **SSE 流式传输不工作**: Nginx 必须设置 `proxy_buffering off` 和足够高的 `proxy_read_timeout`

### 数据库访问
```bash
# 连接 Docker 中的 MySQL
docker exec -it liutech-mysql mysql -u root -p123456
```

## 📚 关键技术

**后端:**
- Spring Boot (3.5.9 父模块, 3.5.6 子模块)
- Spring Security + JWT
- MyBatis-Plus (3.5.12)
- MySQL 8.0
- Java 21

**前端:**
- Vue 3.5.17 + TypeScript
- Vite 7.1.3
- Pinia (状态管理)
- Vue Router 4.5.1
- Ant Design Vue (管理后台)
- TinyMCE (富文本编辑器)
- Live2D 动画

**基础设施:**
- Docker + Docker Compose
- Nginx (反向代理)
- MySQL 8.0

## 📝 开发说明

### AI 开发工程流程

本项目的 AI 流程采用“轻量规则 + 专用 skill”：

```text
.codex/project-adapter.md
.claude/rules/ai-development-workflow.md
.codex/rules/ai-development-workflow.md
.codex/skills/prd-workflow
.codex/skills/delivery-workflow
```

日常交流、解释概念、头脑风暴、非落地讨论时，不主动加载 PRD、实现 PRD、开发记录或项目架构。用户明确要求生成 PRD、审查 PRD、先设计方案、实现前规划时，使用 `.codex/skills/prd-workflow`。用户明确要求开始开发、修复、重构、安全整改或性能优化时，使用 `.codex/skills/delivery-workflow`。触发任一 workflow skill 后，先读取 `.codex/project-adapter.md`。

### 添加新功能
1. 后端: 创建 controller → service → mapper 层
2. 前端: 添加路由 → 视图组件 → API 服务
3. 数据库: 在 `sql/` 目录添加迁移文件
4. 测试: 为新功能添加单元测试

### Maven 多模块结构
- 父 `pom.xml` 定义 `spring-boot-starter-parent` 3.5.9 并管理依赖
- 子模块 (`LiuTech`, `LiuTech-AI`) 继承父模块
- 从根目录构建: `mvn clean install -DskipTests` 构建所有模块

### Docker 服务通信
服务使用容器名称进行内部通信:
- AI 服务 → 后端: `http://backend:8080`
- 所有服务 → MySQL: `mysql:3306`
- 外部访问: 使用暴露端口 (8080, 8081, 3000, 3001)

### 数据库迁移
- 主数据库: `sql/sql.sql`
- AI 数据库: `sql/ai_chat_tables.sql`

### 代码风格
- 后端: 遵循 Java 规范 (Spring Boot 标准)
- 前端: Vue 3 Composition API + TypeScript
- 前端已配置 ESLint/Prettier

## 🔗 重要资源

- README.md - 完整项目文档
- LiuTech/API文档.md - 完整 API 参考
- 快速部署指南.md - 部署指南
- docker-compose.yml - 服务配置
- 服务器部署脚本.sh - 服务器部署脚本

## 🚦 服务端口

| 服务 | 端口 | 描述 |
|---------|------|-------------|
| 用户前台 | 3000 | 用户博客前端 |
| 管理后台 | 3001 | 管理面板 |
| 后端 API | 8080 | 主 REST API |
| AI 服务 | 8081 | AI 聊天助手 |
| MySQL | 3306 | 主数据库 |
| Nginx | 80/443 | 反向代理 |

## 🌐 Nginx 路由配置

Nginx 作为统一入口，根据路径路由到不同服务:

- `/` → Web 前端 (端口 3000)
- `/api/` → 后端 API (端口 8080)
- `/ai/` → AI 服务 (端口 8081)
- `/uploads/` → 后端文件服务 (端口 8080)
- 端口 81 → Admin 管理后台 (端口 3001)

### CORS 配置
- 后端 API: 允许所有来源 (`Access-Control-Allow-Origin *`)
- AI 服务: 使用动态来源 (`$http_origin`) 以支持 SSE 流式响应
- 所有请求支持 GET, POST, PUT, DELETE, OPTIONS 方法

### SSE 流式响应配置
AI 服务的 SSE (Server-Sent Events) 需要特殊 Nginx 配置:
```nginx
proxy_http_version 1.1;
proxy_set_header Connection "";
proxy_buffering off;
proxy_read_timeout 3600s;
```
