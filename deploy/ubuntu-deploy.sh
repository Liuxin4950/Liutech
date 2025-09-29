#!/bin/bash

# LiuTech 博客系统 Ubuntu 服务器部署脚本
# 作者: 刘鑫
# 时间: 2025-09-29
# 描述: 自动化部署LiuTech博客系统到Ubuntu服务器

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置变量
PROJECT_NAME="liutech"
DEPLOY_USER="liutech"
DEPLOY_DIR="/opt/liutech"
BACKUP_DIR="/opt/liutech/backup"
LOG_DIR="/opt/liutech/logs"
NGINX_CONF_DIR="/etc/nginx/sites-available"
SYSTEMD_DIR="/etc/systemd/system"

# 数据库配置
DB_NAME="liutech"
DB_AI_NAME="liutech_ai"
DB_USER="liutech_user"

# 服务端口
BACKEND_PORT=8080
AI_SERVICE_PORT=8081
FRONTEND_PORT=80

# 打印带颜色的消息
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

# 检查是否为root用户
check_root() {
    if [[ $EUID -eq 0 ]]; then
        print_error "请不要使用root用户运行此脚本！"
        exit 1
    fi
}

# 检查系统版本
check_system() {
    print_info "检查系统版本..."
    
    if [[ ! -f /etc/os-release ]]; then
        print_error "无法检测系统版本"
        exit 1
    fi
    
    source /etc/os-release
    
    if [[ "$ID" != "ubuntu" ]]; then
        print_error "此脚本仅支持Ubuntu系统"
        exit 1
    fi
    
    # 检查Ubuntu版本
    version_id=$(echo $VERSION_ID | cut -d. -f1)
    if [[ $version_id -lt 20 ]]; then
        print_warning "建议使用Ubuntu 20.04或更高版本"
    fi
    
    print_success "系统检查通过: $PRETTY_NAME"
}

# 更新系统包
update_system() {
    print_info "更新系统包..."
    sudo apt update
    sudo apt upgrade -y
    print_success "系统包更新完成"
}

# 安装基础依赖
install_dependencies() {
    print_info "安装基础依赖..."
    
    sudo apt install -y \
        curl \
        wget \
        git \
        unzip \
        software-properties-common \
        apt-transport-https \
        ca-certificates \
        gnupg \
        lsb-release \
        ufw \
        htop \
        tree \
        vim
    
    print_success "基础依赖安装完成"
}

# 安装Java 21
install_java() {
    print_info "安装Java 21..."
    
    if java -version 2>&1 | grep -q "21"; then
        print_success "Java 21已安装"
        return
    fi
    
    sudo apt install -y openjdk-21-jdk
    
    # 设置JAVA_HOME
    echo 'export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64' | sudo tee -a /etc/environment
    echo 'export PATH=$PATH:$JAVA_HOME/bin' | sudo tee -a /etc/environment
    source /etc/environment
    
    print_success "Java 21安装完成"
}

# 安装Maven
install_maven() {
    print_info "安装Maven..."
    
    if mvn -version 2>/dev/null; then
        print_success "Maven已安装"
        return
    fi
    
    sudo apt install -y maven
    print_success "Maven安装完成"
}

# 安装Node.js
install_nodejs() {
    print_info "安装Node.js..."
    
    if node --version 2>/dev/null | grep -q "v2[0-9]"; then
        print_success "Node.js已安装"
        return
    fi
    
    # 安装NodeSource仓库
    curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
    sudo apt install -y nodejs
    
    # 安装yarn
    sudo npm install -g yarn
    
    print_success "Node.js安装完成"
}

# 安装MySQL
install_mysql() {
    print_info "安装MySQL..."
    
    if systemctl is-active --quiet mysql; then
        print_success "MySQL已安装并运行"
        return
    fi
    
    sudo apt install -y mysql-server
    
    # 启动MySQL服务
    sudo systemctl start mysql
    sudo systemctl enable mysql
    
    print_success "MySQL安装完成"
    print_warning "请手动运行 'sudo mysql_secure_installation' 进行安全配置"
}

# 安装Nginx
install_nginx() {
    print_info "安装Nginx..."
    
    if systemctl is-active --quiet nginx; then
        print_success "Nginx已安装并运行"
        return
    fi
    
    sudo apt install -y nginx
    
    # 启动Nginx服务
    sudo systemctl start nginx
    sudo systemctl enable nginx
    
    print_success "Nginx安装完成"
}

# 创建部署用户和目录
create_deploy_user() {
    print_info "创建部署用户和目录..."
    
    # 创建部署用户
    if ! id "$DEPLOY_USER" &>/dev/null; then
        sudo useradd -m -s /bin/bash $DEPLOY_USER
        sudo usermod -aG sudo $DEPLOY_USER
        print_success "创建用户: $DEPLOY_USER"
    else
        print_success "用户已存在: $DEPLOY_USER"
    fi
    
    # 创建部署目录
    sudo mkdir -p $DEPLOY_DIR/{backend,ai-service,frontend,uploads,logs,backup,scripts}
    sudo chown -R $DEPLOY_USER:$DEPLOY_USER $DEPLOY_DIR
    
    print_success "部署目录创建完成: $DEPLOY_DIR"
}

# 配置防火墙
configure_firewall() {
    print_info "配置防火墙..."
    
    sudo ufw --force enable
    sudo ufw default deny incoming
    sudo ufw default allow outgoing
    
    # 允许SSH
    sudo ufw allow ssh
    
    # 允许HTTP和HTTPS
    sudo ufw allow 80/tcp
    sudo ufw allow 443/tcp
    
    # 允许应用端口（仅本地访问）
    sudo ufw allow from 127.0.0.1 to any port $BACKEND_PORT
    sudo ufw allow from 127.0.0.1 to any port $AI_SERVICE_PORT
    
    print_success "防火墙配置完成"
}

# 主函数
main() {
    print_info "开始LiuTech博客系统部署..."
    
    check_root
    check_system
    update_system
    install_dependencies
    install_java
    install_maven
    install_nodejs
    install_mysql
    install_nginx
    create_deploy_user
    configure_firewall
    
    print_success "基础环境部署完成！"
    print_info "接下来请执行以下步骤："
    print_info "1. 配置MySQL数据库 (运行 setup-database.sh)"
    print_info "2. 部署后端服务 (运行 deploy-backend.sh)"
    print_info "3. 部署前端应用 (运行 deploy-frontend.sh)"
    print_info "4. 配置Nginx (运行 setup-nginx.sh)"
}

# 执行主函数
main "$@"