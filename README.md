# 🚀 LiuTech 博客系统

<div align="center">

![LiuTech Logo](https://img.shields.io/badge/LiuTech-博客系统-blue?style=for-the-badge&logo=vue.js)

**现代化全栈博客平台 | 前后端分离 | AI 智能助手**

[![Vue](https://img.shields.io/badge/Vue-3.5.17-4FC08D?style=flat-square&logo=vue.js)](https://vuejs.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-6DB33F?style=flat-square&logo=spring-boot)](https://spring.io/projects/spring-boot)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.8.3-3178C6?style=flat-square&logo=typescript)](https://www.typescriptlang.org/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk)](https://openjdk.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat-square&logo=mysql)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-支持-2496ED?style=flat-square&logo=docker)](https://www.docker.com/)

[在线演示](http://web.localhost) · [管理后台](http://admin.localhost) · [API 文档](./LiuTech/API文档.md) · [部署指南](./部署文档.md)

</div>

---

## 📖 项目简介

LiuTech 是一个基于现代化技术栈构建的全栈博客系统，采用前后端分离架构设计。系统包含用户前台展示、管理后台和 AI 聊天助手三大核心模块，为用户提供完整的博客创作、管理和互动体验。

### ✨ 核心特性

- 🎨 **现代化界面**：基于 Vue 3 + TypeScript，响应式设计，完美适配各种设备
- 🔐 **安全可靠**：Spring Security + JWT 认证，数据加密存储
- 🤖 **AI 智能助手**：集成 AI 聊天功能，提供智能内容创作辅助
- 📝 **富文本编辑**：TinyMCE 7.9.1 编辑器，支持多媒体内容
- 🐳 **容器化部署**：完整的 Docker 配置，一键部署
- 📊 **数据统计**：完善的用户行为分析和内容统计
- 🎭 **交互体验**：Live2D 动画效果，流畅的用户体验

---

## 🏗️ 系统架构

### 技术栈概览

```
┌─────────────────┬─────────────────┬─────────────────┐
│   前端技术栈     │    后端技术栈    │    部署运维      │
├─────────────────┼─────────────────┼─────────────────┤
│ Vue 3.5.17      │ Spring Boot 3.5.6│ Docker          │
│ TypeScript 5.8.3│ Java 21         │ Docker Compose  │
│ Vite 7.1.3      │ Spring Security │ Nginx           │
│ Pinia 3.0.3     │ MyBatis-Plus    │ MySQL 8.0       │
│ Vue Router 4.5.1│ Redis Cache     │ Git             │
│ Ant Design Vue  │ JWT Auth        │ 环境变量管理     │
│ TinyMCE 7.9.1   │ File Upload     │ 健康检查        │
│ Axios 1.10.0    │ Global Exception│ 数据持久化      │
└─────────────────┴─────────────────┴─────────────────┘
```

### 系统架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        Nginx 反向代理                        │
├─────────────────────┬─────────────────────┬─────────────────┤
│   Web 用户前台       │   Admin 管理后台     │   静态资源服务   │
│   (Vue 3 + TS)      │   (Vue 3 + Ant)     │   (文件上传)     │
│   Port: 3000        │   Port: 4000        │   /uploads      │
└─────────────────────┴─────────────────────┴─────────────────┘
                                │
                    ┌───────────┴───────────┐
                    │     后端 API 服务      │
                    │   Spring Boot 3.5.6   │
                    │     Port: 8080        │
                    └───────────┬───────────┘
                                │
                    ┌───────────┴───────────┐
                    │      数据存储层       │
                    │   MySQL 8.0 + Redis  │
                    │     Port: 3306       │
                    └───────────────────────┘
```

---

## 🎯 功能特性

### 👤 用户系统
- **用户认证**：注册、登录、JWT 身份验证
- **权限管理**：基于角色的访问控制
- **个人资料**：头像上传、信息编辑、密码修改
- **积分系统**：签到获取积分、积分统计

### 📝 内容管理
- **文章发布**：富文本编辑器、草稿保存、定时发布
- **分类管理**：多级分类、分类统计
- **标签系统**：标签管理、标签云展示
- **文件上传**：图片、文档等多媒体文件支持
- **内容搜索**：全文搜索、分类筛选

### 💬 互动功能
- **评论系统**：文章评论、评论管理
- **点赞收藏**：文章点赞、收藏功能
- **用户互动**：关注、私信（规划中）

### 🤖 AI 助手
- **智能对话**：自然语言交互
- **内容辅助**：写作建议、内容优化
- **上下文记忆**：对话历史管理

### 🎨 界面体验
- **响应式设计**：完美适配桌面端和移动端
- **主题切换**：支持明暗主题（可扩展）
- **动画效果**：Live2D 动画、页面过渡效果
- **无障碍支持**：良好的可访问性设计

---

## 🚀 快速开始

### 环境要求

- **Node.js** ≥ 18.0.0
- **Java** 21 (OpenJDK 或 Oracle JDK)
- **MySQL** ≥ 8.0.0
- **Docker** ≥ 20.10.0 (可选)
- **Git** 最新版本

### 🐳 Docker 一键部署（推荐）

```bash
# 1. 克隆项目
git clone https://github.com/Liuxin4950/Liutech.git
cd Liutech

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 文件，配置数据库密码等

# 3. 启动所有服务
docker-compose up -d --build

# 4. 访问应用
# 用户前台: http://web.localhost
# 管理后台: http://admin.localhost
# 后端API: http://localhost:8080
```

### 💻 本地开发部署

#### 1. 数据库配置
```sql
-- 创建数据库
CREATE DATABASE liutech CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 导入数据表结构
mysql -u root -p liutech < sql.sql
```

#### 2. 后端服务启动
```bash
# 进入后端目录
cd LiuTech

# 编译并启动
mvn clean compile
mvn spring-boot:run

# 或者打包运行
mvn clean package -DskipTests
java -jar target/liutech-backend-*.jar
```

#### 3. 前端服务启动

**用户前台 (Web)**
```bash
cd Web
npm install
npm run dev
# 访问: http://localhost:3000
```

**管理后台 (Admin)**
```bash
cd Admin
npm install
npm run dev
# 访问: http://localhost:4000
```

---

## 📁 项目结构

```
Liutech/
├── 📁 LiuTech/                 # Spring Boot 主后端服务
│   ├── 📁 src/main/java/       # Java 源码
│   │   └── 📁 chat/liuxin/liutech/
│   │       ├── 📁 controller/  # 控制器层
│   │       ├── 📁 service/     # 业务逻辑层
│   │       ├── 📁 mapper/      # 数据访问层
│   │       ├── 📁 model/       # 数据模型
│   │       ├── 📁 config/      # 配置类
│   │       └── 📁 common/      # 公共组件
│   ├── 📁 src/main/resources/  # 配置文件
│   ├── 📄 Dockerfile          # Docker 构建文件
│   └── 📄 pom.xml             # Maven 依赖配置
├── 📁 LiuTech-AI/             # AI 聊天服务模块
│   ├── 📁 src/main/java/       # AI 服务源码
│   ├── 📄 AI接口文档.md        # AI 接口文档
│   └── 📄 pom.xml             # Maven 依赖配置
├── 📁 Web/                    # Vue 3 用户前端
│   ├── 📁 src/
│   │   ├── 📁 views/          # 页面组件
│   │   ├── 📁 components/     # 公共组件
│   │   ├── 📁 stores/         # 状态管理
│   │   ├── 📁 services/       # API 服务
│   │   └── 📁 router/         # 路由配置
│   ├── 📁 public/             # 静态资源
│   ├── 📄 Dockerfile          # Docker 构建文件
│   └── 📄 package.json        # NPM 依赖配置
├── 📁 Admin/                  # Vue 3 管理后台
│   ├── 📁 src/                # 源码结构同 Web
│   ├── 📄 Dockerfile          # Docker 构建文件
│   └── 📄 package.json        # NPM 依赖配置
├── 📁 nginx/                  # Nginx 配置
│   ├── 📄 nginx.conf          # 主配置文件
│   └── 📁 conf.d/             # 虚拟主机配置
├── 📄 docker-compose.yml      # Docker 编排配置
├── 📄 sql.sql                 # 数据库初始化脚本
├── 📄 部署文档.md              # 详细部署指南
├── 📄 目前开发清单.md          # 开发进度清单
└── 📄 README.md               # 项目说明文档
```

---

## 🔧 配置说明

### 环境变量配置

创建 `.env` 文件：
```bash
# 数据库配置
DB_ROOT_PASSWORD=your_root_password
DB_PASSWORD=your_db_password

# 应用配置
SERVER_PORT=8080
FILE_UPLOAD_PATH=/opt/uploads

# JWT 配置
JWT_SECRET=your_jwt_secret_key
JWT_EXPIRATION=86400000
```

### 应用配置文件

**后端配置 (`application.yml`)**
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/liutech?useSSL=false&serverTimezone=UTC
    username: root
    password: ${DB_PASSWORD}
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB

server:
  port: ${SERVER_PORT:8080}

file:
  upload:
    base-path: ${FILE_UPLOAD_PATH:/opt/uploads}
```

**前端配置 (`.env.development`)**
```bash
# Web 前端
VITE_API_BASE_URL=http://127.0.0.1:8080

# Admin 后台
VITE_API_BASE_URL=http://127.0.0.1:8080
```

---

## 📊 API 接口

### 用户认证
```http
POST /api/auth/login          # 用户登录
POST /api/auth/register       # 用户注册
POST /api/auth/logout         # 用户登出
GET  /api/auth/profile        # 获取用户信息
PUT  /api/auth/profile        # 更新用户信息
```

### 文章管理
```http
GET    /api/posts             # 获取文章列表
GET    /api/posts/{id}        # 获取文章详情
POST   /api/posts             # 创建文章
PUT    /api/posts/{id}        # 更新文章
DELETE /api/posts/{id}        # 删除文章
```

### 分类标签
```http
GET    /api/categories        # 获取分类列表
GET    /api/tags              # 获取标签列表
POST   /api/categories        # 创建分类
POST   /api/tags              # 创建标签
```

> 📖 完整的 API 文档请查看：[API 文档](./LiuTech/API文档.md)

---

## 🎨 界面预览

### 用户前台
- **首页**：文章列表、分类导航、热门标签
- **文章详情**：富文本内容、评论互动、相关推荐
- **个人中心**：用户资料、我的文章、收藏管理
- **创作中心**：富文本编辑器、草稿管理

### 管理后台
- **仪表盘**：数据统计、系统概览
- **内容管理**：文章、分类、标签管理
- **用户管理**：用户列表、权限设置
- **系统设置**：公告管理、系统配置

---

## 🚀 部署指南

### 生产环境部署

1. **服务器要求**
   - Linux 系统 (Ubuntu 20.04+ / CentOS 7+)
   - 4GB+ 内存，20GB+ 存储空间
   - Docker 和 Docker Compose

2. **部署步骤**
   ```bash
   # 克隆代码
   git clone https://github.com/Liuxin4950/Liutech.git
   cd Liutech
   
   # 配置环境变量
   cp .env.example .env
   vim .env
   
   # 启动服务
   docker-compose -f docker-compose.prod.yml up -d --build
   ```

3. **Nginx 配置**
   ```nginx
   server {
       listen 80;
       server_name your-domain.com;
       
       location / {
           proxy_pass http://localhost:3000;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
       }
       
       location /api {
           proxy_pass http://localhost:8080;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
       }
   }
   ```

> 📖 详细部署指南请查看：[部署文档](./部署文档.md)

---

## 🤝 贡献指南

我们欢迎所有形式的贡献！请遵循以下步骤：

1. **Fork** 本仓库
2. **创建** 功能分支 (`git checkout -b feature/AmazingFeature`)
3. **提交** 更改 (`git commit -m 'Add some AmazingFeature'`)
4. **推送** 到分支 (`git push origin feature/AmazingFeature`)
5. **创建** Pull Request

### 开发规范

- **代码风格**：遵循 ESLint 和 Prettier 配置
- **提交信息**：使用语义化提交信息
- **测试覆盖**：新功能需要包含相应测试
- **文档更新**：重要变更需要更新文档

---

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

---

## 👨‍💻 作者信息

**刘鑫**
- 📧 Email: [your-email@example.com](mailto:your-email@example.com)
- 🐙 GitHub: [@Liuxin4950](https://github.com/Liuxin4950)
- 📝 博客: [LiuTech Blog](http://web.localhost)

---

## 🙏 致谢

感谢以下开源项目和技术社区：

- [Vue.js](https://vuejs.org/) - 渐进式 JavaScript 框架
- [Spring Boot](https://spring.io/projects/spring-boot) - Java 应用开发框架
- [Ant Design Vue](https://antdv.com/) - 企业级 UI 组件库
- [TinyMCE](https://www.tiny.cloud/) - 富文本编辑器
- [Docker](https://www.docker.com/) - 容器化平台

---

## 📈 项目统计

![GitHub stars](https://img.shields.io/github/stars/Liuxin4950/Liutech?style=social)
![GitHub forks](https://img.shields.io/github/forks/Liuxin4950/Liutech?style=social)
![GitHub issues](https://img.shields.io/github/issues/Liuxin4950/Liutech)
![GitHub license](https://img.shields.io/github/license/Liuxin4950/Liutech)

---

<div align="center">

**⭐ 如果这个项目对你有帮助，请给它一个 Star！**

**🚀 让我们一起构建更好的博客系统！**

</div>