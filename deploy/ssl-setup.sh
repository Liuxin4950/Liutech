#!/bin/bash

# LiuTech SSL证书配置脚本
# 作者: 刘鑫
# 时间: 2025-09-29

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

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

# 检查域名解析
check_domain_resolution() {
    local domain=$1
    print_info "检查域名解析: $domain"
    
    local server_ip=$(curl -s ifconfig.me)
    local domain_ip=$(dig +short $domain | tail -n1)
    
    if [[ "$server_ip" == "$domain_ip" ]]; then
        print_success "域名解析正确: $domain -> $server_ip"
        return 0
    else
        print_error "域名解析错误: $domain -> $domain_ip (应该是: $server_ip)"
        return 1
    fi
}

# 安装Certbot
install_certbot() {
    print_info "安装Certbot..."
    
    # 更新包列表
    sudo apt update
    
    # 安装snapd（如果未安装）
    if ! command -v snap &> /dev/null; then
        sudo apt install -y snapd
        sudo systemctl enable --now snapd.socket
        sudo ln -sf /var/lib/snapd/snap /snap
    fi
    
    # 通过snap安装certbot
    sudo snap install core; sudo snap refresh core
    sudo snap install --classic certbot
    
    # 创建符号链接
    sudo ln -sf /snap/bin/certbot /usr/bin/certbot
    
    print_success "Certbot安装完成"
}

# 获取SSL证书
obtain_ssl_certificate() {
    local domain=$1
    local email=$2
    
    print_info "为域名 $domain 获取SSL证书..."
    
    # 停止nginx以释放80端口
    sudo systemctl stop nginx
    
    # 使用standalone模式获取证书
    sudo certbot certonly \
        --standalone \
        --email "$email" \
        --agree-tos \
        --no-eff-email \
        --domains "$domain"
    
    if [[ $? -eq 0 ]]; then
        print_success "SSL证书获取成功"
    else
        print_error "SSL证书获取失败"
        sudo systemctl start nginx
        exit 1
    fi
    
    # 重新启动nginx
    sudo systemctl start nginx
}

# 配置Nginx SSL
configure_nginx_ssl() {
    local domain=$1
    
    print_info "配置Nginx SSL..."
    
    # 备份原配置
    sudo cp /etc/nginx/sites-available/liutech /etc/nginx/sites-available/liutech.backup
    
    # 创建SSL配置
    sudo tee /etc/nginx/sites-available/liutech << EOF
# LiuTech 博客系统 Nginx SSL 配置
# 作者: 刘鑫
# 时间: $(date '+%Y-%m-%d')

# HTTP重定向到HTTPS
server {
    listen 80;
    server_name ${domain} www.${domain};
    return 301 https://\$server_name\$request_uri;
}

# HTTPS前端站点配置
server {
    listen 443 ssl http2;
    server_name ${domain} www.${domain};
    root /var/www/liutech/web;
    index index.html index.htm;
    
    # SSL配置
    ssl_certificate /etc/letsencrypt/live/${domain}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/${domain}/privkey.pem;
    ssl_trusted_certificate /etc/letsencrypt/live/${domain}/chain.pem;
    
    # SSL安全配置
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-RSA-AES128-GCM-SHA256:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-RSA-AES128-SHA256:ECDHE-RSA-AES256-SHA384:ECDHE-RSA-AES128-SHA:ECDHE-RSA-AES256-SHA:DHE-RSA-AES128-SHA256:DHE-RSA-AES256-SHA256:DHE-RSA-AES128-SHA:DHE-RSA-AES256-SHA:!aNULL:!eNULL:!EXPORT:!DES:!RC4:!MD5:!PSK:!SRP:!CAMELLIA;
    ssl_prefer_server_ciphers on;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;
    ssl_stapling on;
    ssl_stapling_verify on;
    
    # 安全头
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "no-referrer-when-downgrade" always;
    add_header Content-Security-Policy "default-src 'self' http: https: data: blob: 'unsafe-inline'" always;
    
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
    
    # 日志配置
    access_log /var/log/nginx/liutech_ssl_access.log;
    error_log /var/log/nginx/liutech_ssl_error.log;
}

# HTTPS管理后台配置
server {
    listen 8443 ssl http2;
    server_name ${domain};
    root /var/www/liutech/admin;
    index index.html index.htm;
    
    # SSL配置
    ssl_certificate /etc/letsencrypt/live/${domain}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/${domain}/privkey.pem;
    ssl_trusted_certificate /etc/letsencrypt/live/${domain}/chain.pem;
    
    # SSL安全配置
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-RSA-AES128-GCM-SHA256:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-RSA-AES128-SHA256:ECDHE-RSA-AES256-SHA384:ECDHE-RSA-AES128-SHA:ECDHE-RSA-AES256-SHA:DHE-RSA-AES128-SHA256:DHE-RSA-AES256-SHA256:DHE-RSA-AES128-SHA:DHE-RSA-AES256-SHA:!aNULL:!eNULL:!EXPORT:!DES:!RC4:!MD5:!PSK:!SRP:!CAMELLIA;
    ssl_prefer_server_ciphers on;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;
    
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
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header X-Content-Type-Options "nosniff" always;
    
    # 日志配置
    access_log /var/log/nginx/liutech_admin_ssl_access.log;
    error_log /var/log/nginx/liutech_admin_ssl_error.log;
}
EOF

    # 测试配置
    if sudo nginx -t; then
        print_success "Nginx SSL配置测试通过"
        sudo systemctl reload nginx
    else
        print_error "Nginx SSL配置测试失败，恢复备份"
        sudo cp /etc/nginx/sites-available/liutech.backup /etc/nginx/sites-available/liutech
        sudo systemctl reload nginx
        exit 1
    fi
}

# 配置自动续期
setup_auto_renewal() {
    print_info "配置SSL证书自动续期..."
    
    # 创建续期脚本
    sudo tee /opt/liutech/scripts/renew-ssl.sh << 'EOF'
#!/bin/bash

# SSL证书续期脚本

echo "开始续期SSL证书..."

# 续期证书
/usr/bin/certbot renew --quiet

# 重新加载nginx
if systemctl is-active --quiet nginx; then
    systemctl reload nginx
    echo "Nginx已重新加载"
fi

echo "SSL证书续期完成"
EOF

    chmod +x /opt/liutech/scripts/renew-ssl.sh
    
    # 添加到crontab
    (crontab -l 2>/dev/null; echo "0 12 * * * /opt/liutech/scripts/renew-ssl.sh >> /var/log/ssl-renewal.log 2>&1") | crontab -
    
    print_success "SSL证书自动续期配置完成"
}

# 配置防火墙
configure_firewall_ssl() {
    print_info "配置防火墙SSL端口..."
    
    # 允许HTTPS端口
    sudo ufw allow 443/tcp
    sudo ufw allow 8443/tcp
    
    print_success "防火墙SSL端口配置完成"
}

# 健康检查
health_check_ssl() {
    local domain=$1
    
    print_info "执行SSL健康检查..."
    
    # 检查证书有效性
    if openssl s_client -connect ${domain}:443 -servername ${domain} < /dev/null 2>/dev/null | openssl x509 -noout -dates; then
        print_success "SSL证书有效"
    else
        print_warning "SSL证书检查异常"
    fi
    
    # 检查HTTPS访问
    if curl -f https://${domain}/ > /dev/null 2>&1; then
        print_success "HTTPS前端站点访问正常"
    else
        print_warning "HTTPS前端站点访问异常"
    fi
    
    # 检查管理后台HTTPS
    if curl -f https://${domain}:8443/ > /dev/null 2>&1; then
        print_success "HTTPS管理后台访问正常"
    else
        print_warning "HTTPS管理后台访问异常"
    fi
}

# 主函数
main() {
    print_info "开始配置LiuTech SSL证书..."
    
    # 获取域名和邮箱
    read -p "请输入域名 (例如: example.com): " DOMAIN
    if [[ -z "$DOMAIN" ]]; then
        print_error "域名不能为空"
        exit 1
    fi
    
    read -p "请输入邮箱地址: " EMAIL
    if [[ -z "$EMAIL" ]]; then
        print_error "邮箱地址不能为空"
        exit 1
    fi
    
    # 检查域名解析
    if ! check_domain_resolution "$DOMAIN"; then
        print_warning "域名解析检查失败，是否继续？(y/n)"
        read -r continue_choice
        if [[ "$continue_choice" != "y" && "$continue_choice" != "Y" ]]; then
            print_info "SSL配置已取消"
            exit 0
        fi
    fi
    
    install_certbot
    obtain_ssl_certificate "$DOMAIN" "$EMAIL"
    configure_nginx_ssl "$DOMAIN"
    setup_auto_renewal
    configure_firewall_ssl
    health_check_ssl "$DOMAIN"
    
    print_success "SSL证书配置完成！"
    print_info "访问信息："
    print_info "  HTTPS前端站点: https://${DOMAIN}"
    print_info "  HTTPS管理后台: https://${DOMAIN}:8443"
    print_info "  续期脚本: /opt/liutech/scripts/renew-ssl.sh"
    print_info ""
    print_info "证书信息："
    print_info "  证书路径: /etc/letsencrypt/live/${DOMAIN}/"
    print_info "  自动续期: 每天12:00执行检查"
    print_info ""
    print_info "常用命令："
    print_info "  手动续期: sudo certbot renew"
    print_info "  查看证书: sudo certbot certificates"
    print_info "  测试续期: sudo certbot renew --dry-run"
}

# 执行主函数
main "$@"