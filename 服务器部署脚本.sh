#!/bin/bash
# 服务器部署脚本 - 加载所有Docker镜像并启动服务
# 作者：刘鑫
# 时间：2025年1月
# 更新时间：2025年12月

echo "=========================================="
echo "LiuTech 博客系统服务器部署脚本"
echo "=========================================="

# 建立总目录和镜像目录
mkdir -p /opt/liutech
mkdir -p /opt/liutech/images
mkdir -p /opt/liutech/sql
mkdir -p /opt/liutech/nginx/certs

# 检查并复制SQL文件
echo "检查SQL初始化文件..."
if [ -f ./sql/sql.sql ]; then
    cp ./sql/sql.sql /opt/liutech/sql/
    echo "已复制 sql.sql"
else
    echo "警告: 未找到 sql/sql.sql 文件"
fi

if [ -f ./sql/ai_chat_tables.sql ]; then
    cp ./sql/ai_chat_tables.sql /opt/liutech/sql/
    echo "已复制 ai_chat_tables.sql"
else
    echo "警告: 未找到 sql/ai_chat_tables.sql 文件"
fi

# 复制Nginx配置
if [ -d ./nginx ]; then
    cp -r ./nginx/* /opt/liutech/nginx/
    echo "已复制 Nginx 配置"
fi

# 创建docker-compose.yml文件
echo "创建Docker Compose配置文件..."
cat > /opt/liutech/docker-compose.yml << 'EOF'
services:
  # MySQL 数据库服务
  mysql:
    image: mysql:8.0.39
    container_name: liutech-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: "123456"
      MYSQL_DATABASE: liutech
    ports:
      - "${MYSQL_PORT:-3306}:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./sql/sql.sql:/docker-entrypoint-initdb.d/init.sql:ro
      - ./sql/ai_chat_tables.sql:/docker-entrypoint-initdb.d/ai_chat_tables.sql:ro
    command: --default-authentication-plugin=mysql_native_password
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-p123456"]
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
      - SPRING_DATASOURCE_PASSWORD=123456
      - FILE_UPLOAD_BASE_PATH=/app/uploads
      - SERVER_BASE_URL=${SERVER_BASE_URL:-http://liuxin.chat}
      - JWT_SECRET=${JWT_SECRET}
    volumes:
      - upload_files:/app/uploads
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
      - SPRING_DATASOURCE_PASSWORD=123456
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
  upload_files:
    driver: local
EOF

# 进入镜像目录加载所有镜像
echo ""
echo "=========================================="
echo "加载Docker镜像..."
echo "=========================================="
cd /opt/liutech/images

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

load_image "mysql-8.0.39.tar" "MySQL镜像"
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
cd /opt/liutech

# 创建.env文件
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

# JWT密钥 - 后端和AI服务共用此密钥（必需配置，请修改为随机字符串）
JWT_SECRET=your_strong_jwt_secret_key_min_32_chars

# AI服务API Key（必需配置）
# 请替换为你的SiliconFlow API Key
SPRING_AI_OPENAI_API_KEY=your_api_key_here
EOF

echo "环境配置文件 .env 已创建"
echo "请编辑 .env 文件配置 SPRING_AI_OPENAI_API_KEY"

echo ""
echo "=========================================="
echo "启动Docker Compose服务..."
echo "=========================================="

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
