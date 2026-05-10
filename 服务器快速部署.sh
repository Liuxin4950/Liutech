#!/bin/bash
# 服务器快速部署脚本（从 Docker Hub 拉取镜像）
# 用法：./服务器快速部署.sh

set -euo pipefail

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

DOCKER_HUB="liuxin"  # Docker Hub 用户名，改成你的
INSTALL_DIR=/opt/liutech

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}LiuTech 博客系统 - 快速部署${NC}"
echo -e "${GREEN}========================================${NC}"

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "错误：未安装 Docker"
    exit 1
fi

# 创建必要目录
mkdir -p "$INSTALL_DIR"
mkdir -p /liuxin/uploads

# 进入安装目录
cd "$INSTALL_DIR"

# 检查 docker-compose.yml 是否存在
if [ ! -f "docker-compose.yml" ]; then
    echo -e "${YELLOW}错误：未找到 $INSTALL_DIR/docker-compose.yml${NC}"
    echo "请先将项目文件上传到服务器"
    exit 1
fi

# 检查 .env 是否存在
if [ ! -f ".env" ]; then
    echo -e "${YELLOW}错误：未找到 $INSTALL_DIR/.env${NC}"
    echo "请先创建 .env 文件并配置密码和 API Key"
    exit 1
fi

# 检查 .env 是否已配置
if grep -q '^DB_PASSWORD=your_' .env 2>/dev/null; then
    echo -e "${YELLOW}请先编辑 .env 文件配置密码${NC}"
    exit 1
fi

# 从 Docker Hub 拉取最新镜像
echo -e "${GREEN}拉取最新镜像...${NC}"
docker pull "$DOCKER_HUB/liutech-backend:latest"
docker pull "$DOCKER_HUB/liutech-ai:latest"
docker pull "$DOCKER_HUB/liutech-web:latest"
docker pull "$DOCKER_HUB/liutech-admin:latest"
docker pull "$DOCKER_HUB/liutech-nginx:latest"

# 重新打标签（因为服务器的 docker-compose.yml 使用本地镜像名）
echo -e "${GREEN}标记镜像...${NC}"
docker tag "$DOCKER_HUB/liutech-backend:latest" liutech-backend:latest
docker tag "$DOCKER_HUB/liutech-ai:latest" liutech-ai:latest
docker tag "$DOCKER_HUB/liutech-web:latest" liutech-web:latest
docker tag "$DOCKER_HUB/liutech-admin:latest" liutech-admin:latest
docker tag "$DOCKER_HUB/liutech-nginx:latest" liutech-nginx:latest

# 启动服务
echo -e "${GREEN}启动服务...${NC}"
docker compose up -d --force-recreate

# 清理旧镜像
docker image prune -f

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}部署完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "访问地址："
echo "- 用户前端: http://$(hostname -I | awk '{print $1}'):80"
echo "- 管理后台: http://$(hostname -I | awk '{print $1}'):81"
echo ""
echo "常用命令："
echo "- 查看状态: docker compose ps"
echo "- 查看日志: docker compose logs -f"
echo "- 重启服务: docker compose restart"
