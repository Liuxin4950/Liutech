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
mkdir -p "$INSTALL_DIR/mysql"
mkdir -p "$INSTALL_DIR/nginx"
# 文件上传目录使用绝对路径 /liuxin/uploads
mkdir -p /liuxin/uploads

# 检查并复制SQL文件
echo "检查SQL初始化文件..."
if [ -f ./Docs/SQL/sql.sql ]; then
    cp ./Docs/SQL/sql.sql "$INSTALL_DIR/sql/sql.sql"
    echo "已复制 sql.sql"
else
    echo "错误: 未找到 Docs/SQL/sql.sql 文件"
    exit 1
fi

if [ -f ./mysql/init-users.sh ]; then
    cp ./mysql/init-users.sh "$INSTALL_DIR/mysql/init-users.sh"
    chmod +x "$INSTALL_DIR/mysql/init-users.sh"
else
    echo "错误: 未找到 mysql/init-users.sh 文件"
    exit 1
fi

# Docs/SQL/sql.sql 已包含主后端 liutech 库和 AI 服务 liutech_ai 库的完整初始化结构。

# 复制Nginx配置
if [ -d ./nginx ]; then
    mkdir -p "$INSTALL_DIR/nginx"
    cp -r ./nginx/. "$INSTALL_DIR/nginx/"
    echo "已复制 Nginx 配置"
fi

# 创建docker-compose.yml文件
echo "创建Docker Compose配置文件..."
# 复制仓库根目录的 docker-compose.yml（唯一事实源）
# 说明：此前本脚本内嵌了一份 compose 副本，与根目录版本已漂移
#       （缺 backend/ai 的 healthcheck、deploy.resources、COS、MAIL、挂载路径变量），
#       执行会把生产配置覆盖坏。现改为直接复制根文件，杜绝多事实源。
if [ -f ./docker-compose.yml ]; then
    cp ./docker-compose.yml "$INSTALL_DIR/docker-compose.yml"
    echo "已复制 docker-compose.yml（根目录版本，唯一事实源）"
else
    echo "错误: 未找到项目根目录的 docker-compose.yml，无法部署"
    exit 1
fi

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

echo "跳过 MySQL 镜像加载：生产数据库使用现有 mysql:8.0 镜像和 mysql_data 数据卷"
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

# 创建 .env 文件（模板取自仓库根 .env.example —— 唯一事实源）
# 说明：此前本脚本内嵌了第二份 .env 模板（17 变量），缺全部 6 个 MAIL_* 变量，
#       导致用本脚本部署时后端邮件功能（忘记密码/验证码登录）静默失效。现改为复制 .env.example。
# ⚠️ 此处 CWD 已是 $INSTALL_DIR，而 .env.example 只存在于仓库根、不会被拷进 $INSTALL_DIR，
#    因此必须用 $SCRIPT_DIR 绝对路径读取，否则永远命中「未找到」分支、.env 根本不会生成。
if [ ! -f .env ]; then
    if [ -f "$SCRIPT_DIR/.env.example" ]; then
        cp "$SCRIPT_DIR/.env.example" .env
    else
        echo "错误: 未找到 $SCRIPT_DIR/.env.example，无法生成 .env 模板"
        exit 1
    fi
    echo ""
    echo "=========================================="
    echo "⚠️  请立即编辑 .env 并填写以下必填项："
    echo "=========================================="
    echo "  JWT_SECRET                    - 强随机密钥 (openssl rand -hex 64)"
    echo "  LIUTECH_INTERNAL_TOKEN        - 内部服务令牌"
    echo "  DB_PASSWORD                   - MySQL root 密码"
    echo "  DB_APP_PASSWORD               - 应用数据库密码"
    echo "  DB_AI_APP_PASSWORD            - AI 服务数据库密码"
    echo "  SPRING_AI_OPENAI_API_KEY      - AI 模型密钥"
    echo "  MAIL_HOST/MAIL_USERNAME/MAIL_PASSWORD/MAIL_FROM  - 邮件配置"
    echo "  UPLOADS_PATH / LOGS_PATH / NGINX_SSL_PATH        - 生产绝对路径"
    echo "  COS_ENABLED / COS_SECRET_ID / COS_SECRET_KEY ... - 如启用对象存储"
    echo ""
    echo "编辑命令： nano $INSTALL_DIR/.env"
    echo "=========================================="
    echo ""
    echo "环境配置文件 .env 已创建（模板复制自 .env.example）"
else
    echo "环境配置文件 .env 已存在，跳过创建"
fi

echo "请检查 .env 文件中的数据库密码、JWT_SECRET、LIUTECH_INTERNAL_TOKEN 和 AI API Key"

if grep -q '^DB_PASSWORD=your_' .env || grep -q '^DB_APP_PASSWORD=your_' .env || grep -q '^DB_AI_APP_PASSWORD=your_' .env || grep -q '^JWT_SECRET=your_' .env || grep -q '^LIUTECH_INTERNAL_TOKEN=your_' .env || grep -q '^SPRING_AI_OPENAI_API_KEY=your_' .env || grep -q '^SILICONFLOW_API_KEY=your_' .env; then
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
