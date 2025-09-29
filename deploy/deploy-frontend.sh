#!/bin/bash

# LiuTech 前端应用部署脚本
# 作者: 刘鑫
# 时间: 2025-09-29

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 配置变量
DEPLOY_DIR="/opt/liutech"
FRONTEND_DIR="$DEPLOY_DIR/frontend"
ADMIN_DIR="$DEPLOY_DIR/admin"
BACKUP_DIR="$DEPLOY_DIR/backup"
NGINX_ROOT="/var/www/liutech"

print_message() {
    local color=$1
    local message=$2
    echo -e "${color}[$(date '+%Y-%m-%d %H:%M:%S')] ${message}${NC}"
}

print_info() {
    print_message $BLUE "$1"
}

print_success() {
    print_message $GREEN "$1"
}

print_warning() {
    print_message $YELLOW "$1"
}

print_error() {
    print_message $RED "$1"
}

# 检查项目源码
check_source() {
    print_info "检查前端项目源码..."
    
    if [[ ! -d "../Web" ]]; then
        print_error "找不到前端项目目录 ../Web"
        exit 1
    fi
    
    if [[ ! -d "../Admin" ]]; then
        print_error "找不到管理后台项目目录 ../Admin"
        exit 1
    fi
    
    if [[ ! -f "../Web/package.json" ]]; then
        print_error "找不到前端项目package.json文件"
        exit 1
    fi
    
    if [[ ! -f "../Admin/package.json" ]]; then
        print_error "找不到管理后台package.json文件"
        exit 1
    fi
    
    print_success "前端项目源码检查通过"
}

# 备份现有前端文件
backup_frontend() {
    print_info "备份现有前端文件..."
    
    local backup_timestamp=$(date +%Y%m%d_%H%M%S)
    local backup_path="$BACKUP_DIR/frontend_backup_$backup_timestamp"
    
    mkdir -p "$backup_path"
    
    # 备份现有前端文件
    if [[ -d "$NGINX_ROOT/web" ]]; then
        cp -r "$NGINX_ROOT/web" "$backup_path/"
        print_success "前端文件已备份"
    fi
    
    if [[ -d "$NGINX_ROOT/admin" ]]; then
        cp -r "$NGINX_ROOT/admin" "$backup_path/"
        print_success "管理后台文件已备份"
    fi
    
    print_success "前端文件备份完成: $backup_path"
}

# 创建生产环境配置
create_prod_env() {
    print_info "创建生产环境配置..."
    
    # 获取服务器IP或域名
    read -p "请输入服务器域名或IP地址 (默认: localhost): " SERVER_HOST
    SERVER_HOST=${SERVER_HOST:-localhost}
    
    # 创建前端生产环境配置
    cat > ../Web/.env.production << EOF
# LiuTech 前端生产环境配置
# 作者: 刘鑫
# 时间: $(date '+%Y-%m-%d')

VITE_APP_TITLE=LiuTech 博客系统
VITE_API_BASE_URL=http://${SERVER_HOST}:8080
VITE_AI_API_BASE_URL=http://${SERVER_HOST}:8081
VITE_APP_VERSION=1.0.0
VITE_APP_ENV=production
EOF

    # 创建管理后台生产环境配置
    cat > ../Admin/.env.production << EOF
# LiuTech 管理后台生产环境配置
# 作者: 刘鑫
# 时间: $(date '+%Y-%m-%d')

VITE_APP_TITLE=LiuTech 管理后台
VITE_API_BASE_URL=http://${SERVER_HOST}:8080
VITE_AI_API_BASE_URL=http://${SERVER_HOST}:8081
VITE_APP_VERSION=1.0.0
VITE_APP_ENV=production
EOF

    print_success "生产环境配置创建完成"
}

# 构建前端项目
build_frontend() {
    print_info "构建前端项目..."
    
    # 构建主前端
    cd ../Web
    print_info "安装前端依赖..."
    npm install
    
    print_info "构建前端应用..."
    npm run build
    
    if [[ $? -ne 0 ]]; then
        print_error "前端项目构建失败"
        exit 1
    fi
    
    print_success "前端项目构建完成"
    
    # 构建管理后台
    cd ../Admin
    print_info "安装管理后台依赖..."
    npm install
    
    print_info "构建管理后台..."
    npm run build
    
    if [[ $? -ne 0 ]]; then
        print_error "管理后台构建失败"
        exit 1
    fi
    
    print_success "管理后台构建完成"
    
    cd ../deploy
}

# 部署前端文件
deploy_frontend_files() {
    print_info "部署前端文件..."
    
    # 创建Nginx根目录
    sudo mkdir -p "$NGINX_ROOT"/{web,admin}
    
    # 部署前端文件
    if [[ -d "../Web/dist" ]]; then
        sudo rm -rf "$NGINX_ROOT/web/*"
        sudo cp -r ../Web/dist/* "$NGINX_ROOT/web/"
        sudo chown -R www-data:www-data "$NGINX_ROOT/web"
        print_success "前端文件部署完成"
    else
        print_error "找不到前端构建文件"
        exit 1
    fi
    
    # 部署管理后台文件
    if [[ -d "../Admin/dist" ]]; then
        sudo rm -rf "$NGINX_ROOT/admin/*"
        sudo cp -r ../Admin/dist/* "$NGINX_ROOT/admin/"
        sudo chown -R www-data:www-data "$NGINX_ROOT/admin"
        print_success "管理后台文件部署完成"
    else
        print_error "找不到管理后台构建文件"
        exit 1
    fi
}

# 配置Nginx
configure_nginx() {
    print_info "配置Nginx..."
    
    # 创建Nginx配置文件
    sudo tee /etc/nginx/sites-available/liutech << EOF
# LiuTech 博客系统 Nginx 配置
# 作者: 刘鑫
# 时间: $(date '+%Y-%m-%d')

# 前端站点配置
server {
    listen 80;
    server_name ${SERVER_HOST} www.${SERVER_HOST};
    root /var/www/liutech/web;
    index index.html index.htm;
    
    # 启用gzip压缩
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_proxied any;
    gzip_comp_level 6;
    gzip_types
        text/plain
        text/css
        text/xml
        text/javascript
        application/json
        application/javascript
        application/xml+rss
        application/atom+xml
        image/svg+xml;
    
    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        access_log off;
    }
    
    # API代理
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_connect_timeout 30s;
        proxy_send_timeout 30s;
        proxy_read_timeout 30s;
    }
    
    # AI API代理
    location /ai/ {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_connect_timeout 30s;
        proxy_send_timeout 30s;
        proxy_read_timeout 30s;
    }
    
    # 文件上传代理
    location /uploads/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        client_max_body_size 100M;
    }
    
    # Vue Router 历史模式支持
    location / {
        try_files \$uri \$uri/ /index.html;
    }
    
    # 安全头
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "no-referrer-when-downgrade" always;
    add_header Content-Security-Policy "default-src 'self' http: https: data: blob: 'unsafe-inline'" always;
    
    # 日志配置
    access_log /var/log/nginx/liutech_access.log;
    error_log /var/log/nginx/liutech_error.log;
}

# 管理后台配置
server {
    listen 8090;
    server_name ${SERVER_HOST};
    root /var/www/liutech/admin;
    index index.html index.htm;
    
    # 启用gzip压缩
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_proxied any;
    gzip_comp_level 6;
    gzip_types
        text/plain
        text/css
        text/xml
        text/javascript
        application/json
        application/javascript
        application/xml+rss
        application/atom+xml
        image/svg+xml;
    
    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        access_log off;
    }
    
    # API代理
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
    
    # AI API代理
    location /ai/ {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
    
    # 文件上传代理
    location /uploads/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        client_max_body_size 100M;
    }
    
    # Vue Router 历史模式支持
    location / {
        try_files \$uri \$uri/ /index.html;
    }
    
    # 安全头
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header X-Content-Type-Options "nosniff" always;
    
    # 日志配置
    access_log /var/log/nginx/liutech_admin_access.log;
    error_log /var/log/nginx/liutech_admin_error.log;
}
EOF

    # 启用站点
    sudo ln -sf /etc/nginx/sites-available/liutech /etc/nginx/sites-enabled/
    
    # 删除默认站点
    sudo rm -f /etc/nginx/sites-enabled/default
    
    # 测试Nginx配置
    if sudo nginx -t; then
        print_success "Nginx配置测试通过"
    else
        print_error "Nginx配置测试失败"
        exit 1
    fi
    
    # 重新加载Nginx
    sudo systemctl reload nginx
    
    print_success "Nginx配置完成"
}

# 配置防火墙
configure_firewall() {
    print_info "配置防火墙..."
    
    # 允许管理后台端口
    sudo ufw allow 8090/tcp
    
    print_success "防火墙配置完成"
}

# 创建SSL证书（可选）
setup_ssl() {
    print_info "是否配置SSL证书？(y/n)"
    read -r setup_ssl_choice
    
    if [[ "$setup_ssl_choice" == "y" || "$setup_ssl_choice" == "Y" ]]; then
        print_info "安装Certbot..."
        sudo apt install -y certbot python3-certbot-nginx
        
        print_info "获取SSL证书..."
        print_warning "请确保域名已正确解析到此服务器"
        sudo certbot --nginx -d ${SERVER_HOST}
        
        # 设置自动续期
        echo "0 12 * * * /usr/bin/certbot renew --quiet" | sudo crontab -
        
        print_success "SSL证书配置完成"
    else
        print_info "跳过SSL证书配置"
    fi
}

# 健康检查
health_check() {
    print_info "执行健康检查..."
    
    # 检查前端站点
    if curl -f http://localhost/ > /dev/null 2>&1; then
        print_success "前端站点访问正常"
    else
        print_warning "前端站点访问异常，请检查配置"
    fi
    
    # 检查管理后台
    if curl -f http://localhost:8090/ > /dev/null 2>&1; then
        print_success "管理后台访问正常"
    else
        print_warning "管理后台访问异常，请检查配置"
    fi
}

# 创建更新脚本
create_update_script() {
    print_info "创建前端更新脚本..."
    
    cat > /opt/liutech/scripts/update-frontend.sh << 'EOF'
#!/bin/bash

# LiuTech 前端更新脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "开始更新前端..."

# 进入项目目录
cd "$PROJECT_DIR/../"

# 拉取最新代码
git pull origin main

# 构建前端
cd Web
npm install
npm run build

# 部署前端
sudo rm -rf /var/www/liutech/web/*
sudo cp -r dist/* /var/www/liutech/web/
sudo chown -R www-data:www-data /var/www/liutech/web

# 构建管理后台
cd ../Admin
npm install
npm run build

# 部署管理后台
sudo rm -rf /var/www/liutech/admin/*
sudo cp -r dist/* /var/www/liutech/admin/
sudo chown -R www-data:www-data /var/www/liutech/admin

echo "前端更新完成！"
EOF

    chmod +x /opt/liutech/scripts/update-frontend.sh
    sudo chown liutech:liutech /opt/liutech/scripts/update-frontend.sh
    
    print_success "前端更新脚本创建完成"
}

# 主函数
main() {
    print_info "开始部署LiuTech前端应用..."
    
    check_source
    backup_frontend
    create_prod_env
    build_frontend
    deploy_frontend_files
    configure_nginx
    configure_firewall
    setup_ssl
    health_check
    create_update_script
    
    print_success "前端应用部署完成！"
    print_info "访问信息："
    print_info "  前端站点: http://${SERVER_HOST}"
    print_info "  管理后台: http://${SERVER_HOST}:8090"
    print_info "  更新脚本: /opt/liutech/scripts/update-frontend.sh"
    print_info ""
    print_info "常用命令："
    print_info "  重新加载Nginx: sudo systemctl reload nginx"
    print_info "  查看Nginx日志: sudo tail -f /var/log/nginx/liutech_access.log"
    print_info "  更新前端: /opt/liutech/scripts/update-frontend.sh"
}

# 执行主函数
main "$@"