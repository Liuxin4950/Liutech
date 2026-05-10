#!/bin/bash
# 服务器快速部署脚本（从 Docker Hub 拉取镜像）
# 用法：./服务器快速部署.sh

set -euo pipefail

# 颜色输出
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}LiuTech 博客系统 - 快速部署${NC}"
echo -e "${GREEN}========================================${NC}"

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "错误：未安装 Docker"
    exit 1
fi

if ! docker compose version &> /dev/null; then
    echo "错误：未安装 Docker Compose V2"
    exit 1
fi

# 安装目录
INSTALL_DIR=/opt/liutech

# 创建必要目录
mkdir -p "$INSTALL_DIR"
mkdir -p /liuxin/uploads

# 检查 docker-compose.yml
if [ ! -f "$INSTALL_DIR/docker-compose.yml" ]; then
    echo -e "${YELLOW}首次部署，创建配置文件...${NC}"

    # 复制 docker-compose.yml
    if [ -f "./docker-compose.yml" ]; then
        cp ./docker-compose.yml "$INSTALL_DIR/"
    else
        echo "错误：未找到 docker-compose.yml"
        exit 1
    fi

    # 复制 SQL 文件
    mkdir -p "$INSTALL_DIR/sql"
    if [ -f "./sql/sql.sql" ]; then
        cp ./sql/sql.sql "$INSTALL_DIR/sql/"
    fi

    # 复制 Nginx 配置
    if [ -d "./nginx" ]; then
        cp -r ./nginx "$INSTALL_DIR/"
    fi

    # 创建 .env 文件
    if [ ! -f "$INSTALL_DIR/.env" ]; then
        cat > "$INSTALL_DIR/.env" << 'EOF'
# 服务端口
WEB_PORT=3000
ADMIN_PORT=3001
BACKEND_PORT=8080
MYSQL_PORT=3306
AI_PORT=8081
NGINX_HTTP=80
NGINX_HTTPS=443

# 必需配置（请修改）
DB_PASSWORD=your_mysql_root_password
JWT_SECRET=your_strong_jwt_secret_min_32_chars
SPRING_AI_OPENAI_API_KEY=your_siliconflow_api_key
SILICONFLOW_API_KEY=your_siliconflow_api_key
SILICONFLOW_TTS_API_KEY=your_siliconflow_tts_api_key
TTS_PROXY_INTERNAL_TOKEN=your_tts_internal_token

# 服务器地址
SERVER_BASE_URL=https://www.liuxin.chat
EOF
        echo -e "${YELLOW}请编辑 $INSTALL_DIR/.env 配置密码和 API Key${NC}"
        echo -e "${YELLOW}然后重新运行此脚本${NC}"
        exit 0
    fi
fi

cd "$INSTALL_DIR"

# 检查 .env 是否已配置
if grep -q '^DB_PASSWORD=your_' .env 2>/dev/null; then
    echo -e "${YELLOW}请先编辑 .env 文件配置密码${NC}"
    exit 1
fi

# 拉取最新镜像
echo -e "${GREEN}拉取最新镜像...${NC}"
docker compose pull

# 启动服务
echo -e "${GREEN}启动服务...${NC}"
docker compose up -d

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
echo "- 查看状态: cd $INSTALL_DIR && docker compose ps"
echo "- 查看日志: cd $INSTALL_DIR && docker compose logs -f"
echo "- 重启服务: cd $INSTALL_DIR && docker compose restart"
