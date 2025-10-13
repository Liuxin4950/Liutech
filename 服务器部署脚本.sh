#!/bin/bash
# 服务器部署脚本 - 加载所有Docker镜像并启动服务
# 作者：刘鑫
# 时间：2025年1月

echo "开始加载Docker镜像..."

# 建立总目录和镜像目录
mkdir -p /opt/liutech
mkdir -p /opt/liutech/images

# 在总目录下编写容器编排文件和.env文件和sql文件
# 容器编排文件：docker-compose.prod.yml
# .env文件：包含环境变量配置(容器编排会读取这个文件来确定每个服务映射的端口)
# sql文件：初始化数据库脚本

# 创建docker-compose.prod.yml文件
echo "创建Docker Compose配置文件..."
cat > /opt/liutech/docker-compose.yml << 'EOF'
services:
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
      - ./sql.sql:/docker-entrypoint-initdb.d/init.sql:ro
    command: --default-authentication-plugin=mysql_native_password
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-p123456"]
      timeout: 20s
      retries: 10
      interval: 10s
      start_period: 40s

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
    volumes:
      - upload_files:/app/uploads
    depends_on:
      mysql:
        condition: service_healthy

  web:
    image: liutech-web:latest
    container_name: liutech-web
    restart: unless-stopped
    ports:
      - "${WEB_PORT:-3000}:80"
    depends_on:
      - backend

  admin:
    image: liutech-admin:latest
    container_name: liutech-admin
    restart: unless-stopped
    ports:
      - "${ADMIN_PORT:-3001}:80"
    depends_on:
      - backend

  nginx:
    image: liutech-nginx:latest
    container_name: liutech-nginx
    restart: unless-stopped
    ports:
      - "${NGINX_HTTP:-8888}:80"
      - "${NGINX_HTTPS:-8443}:443"
    depends_on:
      - web
      - admin
      - backend

volumes:
  mysql_data:
  upload_files:
EOF

# 复制SQL初始化文件（需要手动上传sql.sql到/opt/liutech目录）
echo "请确保sql.sql文件已上传到/opt/liutech目录"

# 进入镜像目录 #将项目打包的所以镜像都放在这个目录下***.tar文件
cd /opt/liutech/images

# 加载所有镜像
echo "正在加载MySQL镜像..."
docker load -i mysql-8.0.39.tar

echo "正在加载Nginx镜像..."
docker load -i liutech-nginx.tar

echo "正在加载后端应用镜像..."
docker load -i liutech-backend.tar

echo "正在加载Web前端镜像..."
docker load -i liutech-web.tar

echo "正在加载Admin前端镜像..."
docker load -i liutech-admin.tar

echo "镜像加载完成！"

# 返回项目根目录
cd /opt/liutech

# 创建.env文件
echo "创建环境配置文件..."
cat > .env << 'EOF'
WEB_PORT=3000
ADMIN_PORT=3001
BACKEND_PORT=8080
MYSQL_PORT=3306
MYSQL_ROOT_PASSWORD=123456
NGINX_HTTP=8888
NGINX_HTTPS=8443
EOF

echo "环境配置文件创建完成！"

# 启动服务
echo "启动Docker Compose服务..."
docker compose up -d

echo "部署完成！"
echo "访问地址："
echo "- Web前端: http://你的服务器IP:8888"
echo "- Admin后台: http://你的服务器IP:8888/admin"
echo "- 后端API: http://你的服务器IP:8888/api"