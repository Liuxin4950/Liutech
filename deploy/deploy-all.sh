#!/bin/bash

# LiuTech 博客系统一键部署脚本
# 作者: 刘鑫
# 时间: 2025-09-29

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'

# 配置变量
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

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

print_step() {
    print_message $PURPLE "$1"
}

print_banner() {
    echo -e "${CYAN}"
    echo "=================================================================="
    echo "                    LiuTech 博客系统部署工具                      "
    echo "                        作者: 刘鑫                               "
    echo "                    时间: $(date '+%Y-%m-%d')                    "
    echo "=================================================================="
    echo -e "${NC}"
}

# 检查运行环境
check_environment() {
    print_step "步骤 1/8: 检查运行环境"
    
    # 检查是否为root用户
    if [[ $EUID -ne 0 ]]; then
        print_error "请使用root用户运行此脚本"
        exit 1
    fi
    
    # 检查操作系统
    if ! grep -q "Ubuntu" /etc/os-release; then
        print_error "此脚本仅支持Ubuntu系统"
        exit 1
    fi
    
    # 检查Ubuntu版本
    local ubuntu_version=$(lsb_release -rs)
    if [[ $(echo "$ubuntu_version < 20.04" | bc -l) -eq 1 ]]; then
        print_warning "建议使用Ubuntu 20.04或更高版本"
    fi
    
    # 检查网络连接
    if ! ping -c 1 google.com &> /dev/null; then
        print_warning "网络连接可能存在问题，部分功能可能受影响"
    fi
    
    print_success "环境检查完成"
}

# 收集部署信息
collect_deployment_info() {
    print_step "步骤 2/8: 收集部署信息"
    
    echo "请提供以下部署信息："
    
    # 服务器域名或IP
    read -p "服务器域名或IP地址 (默认: localhost): " SERVER_HOST
    SERVER_HOST=${SERVER_HOST:-localhost}
    
    # 数据库密码
    read -s -p "MySQL root密码: " MYSQL_ROOT_PASSWORD
    echo
    read -s -p "应用数据库密码: " APP_DB_PASSWORD
    echo
    
    # 邮箱（用于SSL证书）
    read -p "邮箱地址 (用于SSL证书，可选): " EMAIL
    
    # 是否配置SSL
    read -p "是否配置SSL证书？(y/n，默认: n): " SETUP_SSL
    SETUP_SSL=${SETUP_SSL:-n}
    
    # 是否配置监控
    read -p "是否配置系统监控？(y/n，默认: y): " SETUP_MONITOR
    SETUP_MONITOR=${SETUP_MONITOR:-y}
    
    # 确认信息
    echo
    print_info "部署配置确认："
    print_info "  服务器地址: $SERVER_HOST"
    print_info "  配置SSL: $SETUP_SSL"
    print_info "  配置监控: $SETUP_MONITOR"
    echo
    
    read -p "确认开始部署？(y/n): " CONFIRM
    if [[ "$CONFIRM" != "y" && "$CONFIRM" != "Y" ]]; then
        print_info "部署已取消"
        exit 0
    fi
    
    print_success "部署信息收集完成"
}

# 系统初始化
system_initialization() {
    print_step "步骤 3/8: 系统初始化"
    
    print_info "执行系统初始化脚本..."
    chmod +x "$SCRIPT_DIR/ubuntu-deploy.sh"
    "$SCRIPT_DIR/ubuntu-deploy.sh"
    
    print_success "系统初始化完成"
}

# 数据库配置
database_setup() {
    print_step "步骤 4/8: 数据库配置"
    
    print_info "执行数据库配置脚本..."
    chmod +x "$SCRIPT_DIR/setup-database.sh"
    
    # 设置环境变量
    export MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD"
    export APP_DB_PASSWORD="$APP_DB_PASSWORD"
    
    "$SCRIPT_DIR/setup-database.sh"
    
    print_success "数据库配置完成"
}

# 后端部署
backend_deployment() {
    print_step "步骤 5/8: 后端服务部署"
    
    print_info "执行后端部署脚本..."
    chmod +x "$SCRIPT_DIR/deploy-backend.sh"
    "$SCRIPT_DIR/deploy-backend.sh"
    
    print_success "后端服务部署完成"
}

# 前端部署
frontend_deployment() {
    print_step "步骤 6/8: 前端应用部署"
    
    print_info "执行前端部署脚本..."
    chmod +x "$SCRIPT_DIR/deploy-frontend.sh"
    
    # 设置环境变量
    export SERVER_HOST="$SERVER_HOST"
    
    "$SCRIPT_DIR/deploy-frontend.sh"
    
    print_success "前端应用部署完成"
}

# SSL配置
ssl_setup() {
    if [[ "$SETUP_SSL" == "y" || "$SETUP_SSL" == "Y" ]]; then
        print_step "步骤 7/8: SSL证书配置"
        
        if [[ -z "$EMAIL" ]]; then
            print_warning "未提供邮箱地址，跳过SSL配置"
        else
            print_info "执行SSL配置脚本..."
            chmod +x "$SCRIPT_DIR/ssl-setup.sh"
            
            # 设置环境变量
            export DOMAIN="$SERVER_HOST"
            export EMAIL="$EMAIL"
            
            "$SCRIPT_DIR/ssl-setup.sh"
            
            print_success "SSL证书配置完成"
        fi
    else
        print_info "跳过SSL证书配置"
    fi
}

# 监控配置
monitoring_setup() {
    if [[ "$SETUP_MONITOR" == "y" || "$SETUP_MONITOR" == "Y" ]]; then
        print_step "步骤 8/8: 系统监控配置"
        
        print_info "执行监控配置脚本..."
        chmod +x "$SCRIPT_DIR/monitor-setup.sh"
        "$SCRIPT_DIR/monitor-setup.sh"
        
        print_success "系统监控配置完成"
    else
        print_info "跳过系统监控配置"
    fi
}

# 健康检查
health_check() {
    print_info "执行系统健康检查..."
    
    local all_healthy=true
    
    # 检查服务状态
    local services=("liutech-backend" "liutech-ai" "nginx" "mysql")
    for service in "${services[@]}"; do
        if systemctl is-active --quiet "$service"; then
            print_success "服务 $service: 运行正常"
        else
            print_error "服务 $service: 运行异常"
            all_healthy=false
        fi
    done
    
    # 检查端口监听
    local ports=("80:HTTP" "8080:Backend" "8081:AI-Service" "3306:MySQL")
    for port_info in "${ports[@]}"; do
        local port=$(echo $port_info | cut -d':' -f1)
        local service=$(echo $port_info | cut -d':' -f2)
        
        if netstat -tuln | grep -q ":$port "; then
            print_success "端口 $port ($service): 监听正常"
        else
            print_error "端口 $port ($service): 监听异常"
            all_healthy=false
        fi
    done
    
    # 检查HTTP访问
    if curl -f http://localhost/ > /dev/null 2>&1; then
        print_success "前端站点: 访问正常"
    else
        print_error "前端站点: 访问异常"
        all_healthy=false
    fi
    
    # 检查API访问
    if curl -f http://localhost:8080/api/health > /dev/null 2>&1; then
        print_success "后端API: 访问正常"
    else
        print_warning "后端API: 访问异常（可能需要实现健康检查端点）"
    fi
    
    if [[ "$all_healthy" == true ]]; then
        print_success "系统健康检查通过"
    else
        print_warning "系统健康检查发现问题，请检查相关服务"
    fi
}

# 生成部署报告
generate_deployment_report() {
    print_info "生成部署报告..."
    
    local report_file="/opt/liutech/deployment-report.txt"
    
    cat > "$report_file" << EOF
LiuTech 博客系统部署报告
========================

部署时间: $(date '+%Y-%m-%d %H:%M:%S')
部署用户: $(whoami)
服务器信息: $(uname -a)
Ubuntu版本: $(lsb_release -d | cut -f2)

部署配置:
---------
服务器地址: $SERVER_HOST
SSL配置: $SETUP_SSL
监控配置: $SETUP_MONITOR

服务状态:
---------
EOF

    # 添加服务状态
    local services=("liutech-backend" "liutech-ai" "nginx" "mysql")
    for service in "${services[@]}"; do
        if systemctl is-active --quiet "$service"; then
            echo "$service: 运行中" >> "$report_file"
        else
            echo "$service: 已停止" >> "$report_file"
        fi
    done
    
    cat >> "$report_file" << EOF

访问信息:
---------
前端站点: http://$SERVER_HOST
管理后台: http://$SERVER_HOST:8090
EOF

    if [[ "$SETUP_SSL" == "y" || "$SETUP_SSL" == "Y" ]]; then
        cat >> "$report_file" << EOF
HTTPS前端: https://$SERVER_HOST
HTTPS管理后台: https://$SERVER_HOST:8443
EOF
    fi
    
    cat >> "$report_file" << EOF

重要文件路径:
-----------
应用目录: /opt/liutech
配置文件: /opt/liutech/backend/application-prod.yml
日志目录: /opt/liutech/logs
备份目录: /opt/liutech/backup
脚本目录: /opt/liutech/scripts

常用命令:
---------
服务管理: /opt/liutech/scripts/manage-services.sh [start|stop|restart|status]
系统监控: /opt/liutech/scripts/system-monitor.sh
系统备份: /opt/liutech/scripts/backup-system.sh
前端更新: /opt/liutech/scripts/update-frontend.sh

注意事项:
---------
1. 请定期备份数据库和重要文件
2. 监控系统资源使用情况
3. 及时更新系统和应用程序
4. 保护好数据库密码和JWT密钥
5. 定期检查SSL证书有效期

技术支持:
---------
如有问题，请联系系统管理员或查看相关日志文件。
EOF

    print_success "部署报告已生成: $report_file"
}

# 显示部署结果
show_deployment_result() {
    echo
    print_banner
    
    print_success "🎉 LiuTech 博客系统部署完成！"
    echo
    
    print_info "📋 部署摘要："
    print_info "  ✅ 系统环境配置完成"
    print_info "  ✅ 数据库配置完成"
    print_info "  ✅ 后端服务部署完成"
    print_info "  ✅ 前端应用部署完成"
    
    if [[ "$SETUP_SSL" == "y" || "$SETUP_SSL" == "Y" ]]; then
        print_info "  ✅ SSL证书配置完成"
    fi
    
    if [[ "$SETUP_MONITOR" == "y" || "$SETUP_MONITOR" == "Y" ]]; then
        print_info "  ✅ 系统监控配置完成"
    fi
    
    echo
    print_info "🌐 访问地址："
    print_info "  前端站点: http://$SERVER_HOST"
    print_info "  管理后台: http://$SERVER_HOST:8090"
    
    if [[ "$SETUP_SSL" == "y" || "$SETUP_SSL" == "Y" ]]; then
        print_info "  HTTPS前端: https://$SERVER_HOST"
        print_info "  HTTPS管理后台: https://$SERVER_HOST:8443"
    fi
    
    echo
    print_info "🔧 管理命令："
    print_info "  服务管理: /opt/liutech/scripts/manage-services.sh [start|stop|restart|status]"
    print_info "  查看日志: tail -f /opt/liutech/logs/liutech-backend.log"
    print_info "  系统监控: /opt/liutech/scripts/system-monitor.sh"
    print_info "  系统备份: /opt/liutech/scripts/backup-system.sh"
    
    if [[ "$SETUP_MONITOR" == "y" || "$SETUP_MONITOR" == "Y" ]]; then
        print_info "  监控仪表板: /opt/liutech/monitor/dashboard.html"
    fi
    
    echo
    print_info "📄 部署报告: /opt/liutech/deployment-report.txt"
    
    echo
    print_warning "⚠️  重要提醒："
    print_warning "  1. 请妥善保管数据库密码和配置文件"
    print_warning "  2. 建议定期备份数据和配置"
    print_warning "  3. 监控系统资源使用情况"
    print_warning "  4. 及时更新系统和应用程序"
    
    echo
    print_success "🚀 部署完成，祝您使用愉快！"
}

# 错误处理
error_handler() {
    local line_number=$1
    print_error "部署过程中发生错误 (行号: $line_number)"
    print_error "请检查错误信息并重新运行部署脚本"
    exit 1
}

# 设置错误处理
trap 'error_handler $LINENO' ERR

# 主函数
main() {
    print_banner
    
    check_environment
    collect_deployment_info
    system_initialization
    database_setup
    backend_deployment
    frontend_deployment
    ssl_setup
    monitoring_setup
    health_check
    generate_deployment_report
    show_deployment_result
}

# 执行主函数
main "$@"