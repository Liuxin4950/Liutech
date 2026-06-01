# 项目适配文件

> 本文件把通用 AI 开发流程适配到当前项目。通用 skill 只负责流程方法；项目名、目录、技术栈、验证命令和优秀代码索引在这里维护。复制 skill 到其他项目时，优先替换本文件。

## 1. 项目基础信息

| 字段 | 内容 |
| --- | --- |
| 项目名 | Liutech |
| 项目类型 | 全栈博客平台 |
| 主要语言 | Java 21、TypeScript |
| 后端技术栈 | Spring Boot、Spring Security、MyBatis-Plus、MySQL |
| 前端技术栈 | Vue 3、Vite、Pinia、Vue Router |
| 部署技术栈 | Docker Compose、Nginx、MySQL |

## 2. 过程文档目录

```text
doc/PRD/功能名_YYYY-MM-DD.md          # 业务需求 + 实现方案合并文档
doc/记录/开发-功能名_YYYY-MM-DD.md    # 实际改了什么、为什么、怎么验证
doc/记录/架构-功能名_YYYY-MM-DD.md    # 单次架构变化
doc/记录/当前架构.md                   # 长期有效的总体架构
```

同一功能的过程文件统一使用：

```text
功能名_YYYY-MM-DD.md
```

`doc/记录/当前架构.md` 用于定期汇总长期有效的系统架构；单个功能架构文档记录本次变化。

## 3. 项目模块

| 模块 | 路径 | 职责 |
| --- | --- | --- |
| Web 前台 | `Web/` | 用户博客前台 |
| Admin 后台 | `Admin/` | 管理后台 |
| 主后端服务 | `LiuTech/` | 主要 REST API、用户、文章、资源等业务 |
| AI 服务 | `LiuTech-AI/` | AI 聊天、推荐、模型配置、SSE/TTS |
| 数据库脚本 | `sql/` | MySQL 初始化和表结构 |
| 网关配置 | `nginx/` | Nginx 反向代理、SSE、CORS |
| 容器编排 | `docker-compose.yml` | 服务编排 |

## 4. 验证命令

按影响范围选择，不要机械全跑。

```bash
# 主后端
cd LiuTech && mvn test

# AI 服务
cd LiuTech-AI && mvn test

# 后端多模块
mvn test

# Web 前台
cd Web && npm run build

# Admin 后台
cd Admin && npm run build

# Docker 编排
docker-compose config
```

## 5. 高风险变更

涉及以下内容时，必须按重大功能 / 高风险变更处理：

- 认证、授权、安全策略、JWT、密码、密钥。
- 积分、支付、资源购买、下载权限。
- 数据库结构、迁移脚本、数据修复。
- 上传、下载、富文本、静态资源暴露。
- AI 调用、模型配置、SSE 流式响应、TTS。
- Nginx、Docker、CI/CD、部署脚本、生产配置。
- 跨服务调用、公共组件、核心业务流程。
- 明显性能影响或兼容性风险。

## 6. 项目优秀代码索引

交付阶段优先读取：

```text
.codex/skills/delivery-workflow/references/excellent-code-index.md
```
