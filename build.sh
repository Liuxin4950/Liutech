#!/bin/bash

# LiuTech 项目统一构建脚本
# 作者：刘鑫
# 说明：确保每次修改代码后都重新打包jar并构建镜像

set -e  # 遇到错误立即退出

echo "=========================================="
echo "LiuTech 项目构建开始"
echo "时间: $(date)"
echo "=========================================="

# 1. 构建后端
echo "🔨 构建后端服务..."
echo "清理并编译后端项目..."
mvn -q -f LiuTech/pom.xml clean package -DskipTests

echo "构建后端Docker镜像..."
docker build -t liutech-backend:latest -f LiuTech/Dockerfile LiuTech

# 2. 构建Web前端
echo "🔨 构建Web前端..."
cd Web
npm ci --silent
npm run build
docker build -t liutech-web:latest .
cd ..

# 3. 构建Admin前端
echo "🔨 构建Admin前端..."
cd Admin
npm ci --silent
npm run build
docker build -t liutech-admin:latest .
cd ..

# 4. 构建Nginx
echo "🔨 构建Nginx服务..."
docker build -t liutech-nginx:latest nginx/

# 5. 显示构建结果
echo "=========================================="
echo "✅ 构建完成！镜像列表："
docker images | grep liutech
echo "=========================================="

echo "🚀 使用以下命令启动服务："
echo "  本地开发: docker-compose up -d"
echo "  云端部署: docker-compose -f docker-compose.hub.yml up -d"
echo "  生产环境: docker-compose -f docker-compose.prod.yml up -d"
echo "=========================================="