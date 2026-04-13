#!/bin/bash
# 服务器部署脚本 - 加载所有Docker镜像并启动服务
# 作者：刘鑫
# 时间：2025年1月
# 更新时间：2025年12月

set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
    if command -v sudo >/dev/null 2>&1; then
        exec sudo -E bash "$0" "$@"
    fi
    echo "错误：需要root权限执行（或安装sudo后重试）"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if ! command -v docker >/dev/null 2>&1; then
    echo "错误：未找到docker，请先安装Docker"
    exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
    echo "错误：未找到docker compose（Compose V2），请确认已安装"
    exit 1
fi
if ! docker info >/dev/null 2>&1; then
    echo "错误：Docker未运行或当前用户无权限访问Docker"
    exit 1
fi

INSTALL_DIR=/opt/liutech

echo "=========================================="
echo "LiuTech 博客系统服务器部署脚本"
echo "=========================================="

# 建立总目录和镜像目录
mkdir -p "$INSTALL_DIR"
mkdir -p "$INSTALL_DIR/images"
mkdir -p "$INSTALL_DIR/sql"
mkdir -p "$INSTALL_DIR/nginx/certs"
# 文件上传目录使用绝对路径 /liuxin/uploads
mkdir -p /liuxin/uploads

# 检查并复制SQL文件
echo "检查SQL初始化文件..."
if [ -f ./sql/sql.sql ]; then
    cp ./sql/sql.sql "$INSTALL_DIR/sql/"
    echo "已复制 sql.sql"
else
    echo "警告: 未找到 sql/sql.sql 文件"
fi

if [ -f ./sql/ai_chat_tables.sql ]; then
    cp ./sql/ai_chat_tables.sql "$INSTALL_DIR/sql/"
    echo "已复制 ai_chat_tables.sql"
else
    echo "警告: 未找到 sql/ai_chat_tables.sql 文件"
fi

# Removed 00_create_databases.sql to match local docker-compose.yml config

# 复制Nginx配置
if [ -d ./nginx ]; then
    mkdir -p "$INSTALL_DIR/nginx"
    cp -r ./nginx/. "$INSTALL_DIR/nginx/"
    echo "已复制 Nginx 配置"
fi

# 创建docker-compose.yml文件
echo "创建Docker Compose配置文件..."
cat > "$INSTALL_DIR/docker-compose.yml" << 'EOF'
services:
  # MySQL 数据库服务
  mysql:
    image: mysql:8.0
    container_name: liutech-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD:-@liuxin2020}
      MYSQL_DATABASE: liutech
    ports:
      - "${MYSQL_PORT:-3306}:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./sql/sql.sql:/docker-entrypoint-initdb.d/init.sql:ro
      - ./sql/ai_chat_tables.sql:/docker-entrypoint-initdb.d/ai_chat_tables.sql:ro
    command: --default-authentication-plugin=mysql_native_password
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-p${DB_PASSWORD:-@liuxin2020}"]
      timeout: 20s
      retries: 10
      interval: 10s
      start_period: 40s

  # 后端应用服务
  backend:
    image: liutech-backend:latest
    container_name: liutech-backend
    restart: unless-stopped
    ports:
      - "${BACKEND_PORT:-8080}:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/liutech?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8
      - SPRING_DATASOURCE_USERNAME=root
      - SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD:-@liuxin2020}
      # JWT签名密钥 - 生产环境必须配置强密钥
      - JWT_SECRET=${JWT_SECRET}
      # 文件上传路径：使用 /app/uploads，外部通过 Bind Mount 挂载 $INSTALL_DIR/uploads 到此路径
      - FILE_UPLOAD_BASE_PATH=/app/uploads
      - SERVER_BASE_URL=${SERVER_BASE_URL:-http://liuxin.chat}
      # 静态资源路径（可选，用于直接返回文件）
      - SPRING_WEB_RESOURCES_STATIC_LOCATIONS=file:/app/uploads
    volumes:
      # 将宿主机 /liuxin/uploads 目录挂载到容器 /app/uploads
      # 这样文件会持久化到宿主机 /liuxin/uploads 目录
      - /liuxin/uploads:/app/uploads
    depends_on:
      mysql:
        condition: service_healthy

  # AI服务
  ai:
    image: liutech-ai:latest
    container_name: liutech-ai
    restart: unless-stopped
    ports:
      - "${AI_PORT:-8081}:8081"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/liutech_ai?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8
      - SPRING_DATASOURCE_USERNAME=root
      - SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD:-@liuxin2020}
      - SPRING_AI_OPENAI_API_KEY=${SPRING_AI_OPENAI_API_KEY}
      - BLOG_API_URL=http://backend:8080
      # JWT密钥 - 必须与后端一致，否则无法验证token
      - JWT_SECRET=${JWT_SECRET}
    depends_on:
      mysql:
        condition: service_healthy

  # Web前端服务
  web:
    image: liutech-web:latest
    container_name: liutech-web
    restart: unless-stopped
    ports:
      - "${WEB_PORT:-3000}:80"
    depends_on:
      - backend

  # Admin前端服务
  admin:
    image: liutech-admin:latest
    container_name: liutech-admin
    restart: unless-stopped
    ports:
      - "${ADMIN_PORT:-3001}:80"
    depends_on:
      - backend

  # Nginx反向代理服务
  nginx:
    image: liutech-nginx:latest
    container_name: liutech-nginx
    restart: unless-stopped
    ports:
      - "${NGINX_HTTP:-80}:80"
      - "${NGINX_HTTPS:-443}:443"
      - "81:81"
    volumes:
      - ./nginx/certs:/etc/nginx/certs:ro
    depends_on:
      - backend
      - web
      - admin
      - ai

volumes:
  mysql_data:
    driver: local
EOF

# 进入镜像目录加载所有镜像
echo ""
echo "=========================================="
echo "加载Docker镜像..."
echo "=========================================="
cd "$INSTALL_DIR/images"

# 检查并加载镜像
load_image() {
    local img_file=$1
    local img_name=$2
    if [ -f "$img_file" ]; then
        echo "加载 $img_name..."
        docker load -i "$img_file"
    else
        echo "警告: 未找到 $img_file，跳过加载"
    fi
}

load_image "mysql-8.0.tar" "MySQL镜像"
load_image "liutech-nginx.tar" "Nginx镜像"
load_image "liutech-backend.tar" "后端镜像"
load_image "liutech-ai.tar" "AI服务镜像"
load_image "liutech-web.tar" "Web前端镜像"
load_image "liutech-admin.tar" "Admin前端镜像"

echo ""
echo "=========================================="
echo "创建环境配置文件..."
echo "=========================================="

# 返回项目根目录
cd "$INSTALL_DIR"

# 创建.env文件
if [ ! -f .env ]; then
cat > .env << 'EOF'
# 服务端口配置
WEB_PORT=3000
ADMIN_PORT=3001
BACKEND_PORT=8080
MYSQL_PORT=3306
AI_PORT=8081
NGINX_HTTP=80
NGINX_HTTPS=443

# 服务器配置
SERVER_BASE_URL=http://liuxin.chat

# MySQL root 密码（必需配置）
DB_PASSWORD=@liuxin2020

# JWT密钥 - 后端和AI服务共用此密钥（必需配置，请修改为随机字符串）
JWT_SECRET=liutech_blog_jwt_secret_min_256_bits

# AI服务API Key（必需配置）
# 请替换为你的SiliconFlow API Key
SPRING_AI_OPENAI_API_KEY="sk-gkfhwsydhwfkfcaxiijxatigydwghbyajjkpsrypktojdcmg"
EOF
    echo "环境配置文件 .env 已创建"
else
    echo "环境配置文件 .env 已存在，跳过创建"
fi

echo "请检查 .env 文件中的 DB_PASSWORD / JWT_SECRET / SPRING_AI_OPENAI_API_KEY"

if grep -q '^DB_PASSWORD=your_' .env || grep -q '^JWT_SECRET=your_' .env || grep -q '^SPRING_AI_OPENAI_API_KEY=your_' .env; then
    echo "错误：检测到 .env 仍是示例配置，请先修改后再执行部署"
    exit 1
fi

echo ""
echo "=========================================="
echo "启动Docker Compose服务..."
echo "=========================================="

docker compose config -q

# 启动服务
docker compose up -d

echo ""
echo "=========================================="
echo "部署完成！"
echo "=========================================="
echo ""
echo "访问地址："
echo "- 用户前端: http://你的服务器IP:80"
echo "- 管理后台: http://你的服务器IP:81"
echo "- 后端API: http://你的服务器IP:80/api"
echo "- AI服务: http://你的服务器IP:80/ai"
echo ""
echo "常用命令："
echo "- 查看状态: docker compose ps"
echo "- 查看日志: docker compose logs -f"
echo "- 重启服务: docker compose restart"
echo "- 查看AI日志: docker compose logs -f ai"
