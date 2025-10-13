#!/bin/bash
# 镜像导出脚本 - 将所有Docker镜像导出为tar文件
# 作者：刘鑫
# 时间：2025年1月

echo "开始导出Docker镜像..."

# 创建镜像导出目录
mkdir -p ./docker-images

# 导出MySQL镜像
echo "正在导出MySQL镜像..."
docker save mysql:8.0.39 -o ./docker-images/mysql-8.0.39.tar

# 导出项目镜像
echo "正在导出后端应用镜像..."
docker save liutech-backend:latest -o ./docker-images/liutech-backend.tar

echo "正在导出Web前端镜像..."
docker save liutech-web:latest -o ./docker-images/liutech-web.tar

echo "正在导出Admin前端镜像..."
docker save liutech-admin:latest -o ./docker-images/liutech-admin.tar

echo "正在导出Nginx镜像..."
docker save liutech-nginx:latest -o ./docker-images/liutech-nginx.tar

echo "镜像导出完成！"
echo "导出的镜像文件位于 ./docker-images/ 目录下："
echo "- mysql-8.0.39.tar"
echo "- liutech-backend.tar"
echo "- liutech-web.tar"
echo "- liutech-admin.tar"
echo "- liutech-nginx.tar"
echo ""
echo "请将这些文件上传到服务器的 /opt/liutech/images/ 目录下"