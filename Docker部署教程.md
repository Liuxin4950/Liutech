# LiuTech 全栈 Docker 部署教程（最简同域代理版）

> 适配当前仓库的实际结构：`mysql + backend + web + admin + nginx`。
> 核心策略：前端生产统一使用同域 `'/api'`，由根 Nginx 反向代理到后端容器 `backend:8080`。

## 教程概述

本教程提供一套“可直接复现”的最简部署方案，强调：
- 前端（Web/Admin）生产环境统一写 `VITE_API_BASE_URL=/api`；
- 根 Nginx 在 `nginx/conf.d/default.conf` 中统一将 `/api` 转发到后端容器；
- 不在浏览器端使用容器名（如 `backend:8080`），以避免网络解析问题与跨域复杂度。

部署完成后：
- 访问 Web 前端：`http://localhost`
- 访问 Admin 前端：`http://admin.localhost`
- 所有前端的接口请求均走 `http://(同域)/api/...`，由 Nginx 反向代理到后端。

## 前置条件

- Docker Desktop（Windows/Mac）或 Docker（Linux）
- Docker Compose v2
- 可用的 80 端口（如被占用，可在 `.env` 中调整 `NGINX_HTTP`）

## 一、环境与配置

### 1. 项目根 `.env`（端口统一管理）

位于 `./.env`（已存在），示例：

```env
WEB_PORT=3000
ADMIN_PORT=3001
BACKEND_PORT=8080
MYSQL_PORT=3306
NGINX_HTTP=80
NGINX_HTTPS=443
```

- 这些变量会被 `docker-compose.yml` 读取，用于主机端口映射。
- 如需更改对外端口，仅修改 `.env`，无需改 Compose 文件。

### 2. 前端生产环境变量（同域 `/api`）

- `Web/.env.production`
  ```env
  VITE_API_BASE_URL=/api
  ```
- `Admin/.env.production`
  ```env
  VITE_API_BASE_URL=/api
  ```

说明：统一写 `'/api'`，由根 Nginx 反代到 `backend:8080`。这样前端与后端同域，避免跨域与容器名解析问题。

### 3. 根 Nginx 反向代理（关键）

配置文件：`nginx/conf.d/default.conf`

- Web 站点：`server_name localhost`，根路径代理到 `web` 容器；
- Admin 站点：`server_name admin.localhost`，根路径代理到 `admin` 容器；
- 统一接口：`location /api/` 代理到 `backend:8080`；文件上传也在此统一代理。

> 注意：`admin.localhost` 无需额外 hosts 配置，`*.localhost` 系列域名系统默认解析到 `127.0.0.1`。

## 二、启动与访问

### 1. 构建并启动

在仓库根目录执行：

```bash
docker-compose up --build -d
```

查看状态：

```bash
docker-compose ps
```

正常情况下，你会看到 `mysql、backend、web、admin、nginx` 都是 `Up` 状态。

### 2. 访问入口

- Web 前端：`http://localhost`
- Admin 前端：`http://admin.localhost`

两者的接口请求均走同域 `/api`，由根 Nginx 转发到后端容器。

## 三、日志与排错

常用日志：

```bash
# 查看后端日志
docker logs -f backend

# 查看 Nginx 访问与错误日志（容器内路径）
docker logs -f nginx

# 查看 MySQL 容器健康状态
docker ps
```

常见问题：
- 401 未授权：后端启用了 JWT，未登录的接口会返回 401。请先登录或在请求头携带 `Authorization: Bearer <token>`。
- 端口被占用：修改根 `.env` 中的端口变量后，重新执行 `docker-compose up -d`。
- 前端访问接口失败：确认 `.env.production` 的 `VITE_API_BASE_URL` 是否为 `'/api'`；确认 Nginx `/api` 反代是否生效。

## 四、更新与重启

代码更新后重新构建与启动：

```bash
docker-compose up -d --build
```

仅重启后端：

```bash
docker-compose restart backend
```

## 五、关于“直连后端”的说明（不推荐）

如果你坚持让前端直连后端而不走同域代理，请在前端生产 `.env` 中写外网可达地址：

```env
VITE_API_BASE_URL=http://<服务器IP或域名>:8080
```

但请注意：
- 需要在后端或前端配置 CORS；
- 不要写 `backend:8080`，该主机名只在 Docker 内部网络可解析，浏览器无法访问；
- 不同环境的 URL 管理成本更高，不如同域 `/api` 简洁稳定。

## 六、当前 Compose 与端口映射（参照）

`docker-compose.yml` 的关键片段如下（已与根 `.env` 绑定）：

```yaml
services:
  mysql:
    image: mysql:8.0.39
    ports:
      - "${MYSQL_PORT:-3306}:3306"

  backend:
    build: ./LiuTech
    ports:
      - "${BACKEND_PORT:-8080}:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/liutech?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8
      - SPRING_DATASOURCE_USERNAME=root
      - SPRING_DATASOURCE_PASSWORD=123456
      - FILE_UPLOAD_BASE_PATH=/app/uploads

  web:
    build: ./Web
    ports:
      - "${WEB_PORT:-3000}:80"

  admin:
    build: ./Admin
    ports:
      - "${ADMIN_PORT:-3001}:80"

  nginx:
    image: nginx:alpine
    ports:
      - "${NGINX_HTTP:-80}:80"
      - "${NGINX_HTTPS:-443}:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./nginx/conf.d:/etc/nginx/conf.d:ro
```

## 七、目录参考

- `docker-compose.yml`：一键编排各服务
- `nginx/conf.d/default.conf`：同域反向代理入口，统一维护后端地址
- `Web/.env.production`、`Admin/.env.production`：统一写 `VITE_API_BASE_URL=/api`
- `LiuTech/src/main/resources/application.yml`：后端端口 `8080` 与上传目录配置

---

以上就是“最简同域代理版”的全栈 Docker 部署教程。按此流程即可在本地或服务器快速上线，且配置集中、易于学习与维护。出现问题时，优先检查 Nginx `/api` 代理与前端 `VITE_API_BASE_URL`，通常即可定位并解决。