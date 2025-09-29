#!/bin/bash

# LiuTech 后端服务部署脚本
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
BACKEND_DIR="$DEPLOY_DIR/backend"
AI_SERVICE_DIR="$DEPLOY_DIR/ai-service"
BACKUP_DIR="$DEPLOY_DIR/backup"
LOG_DIR="$DEPLOY_DIR/logs"

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
    print_info "检查项目源码..."
    
    if [[ ! -d "../LiuTech" ]]; then
        print_error "找不到后端项目目录 ../LiuTech"
        exit 1
    fi
    
    if [[ ! -d "../LiuTech-AI" ]]; then
        print_error "找不到AI服务项目目录 ../LiuTech-AI"
        exit 1
    fi
    
    if [[ ! -f "../pom.xml" ]]; then
        print_error "找不到父项目pom.xml文件"
        exit 1
    fi
    
    print_success "项目源码检查通过"
}

# 备份现有服务
backup_services() {
    print_info "备份现有服务..."
    
    local backup_timestamp=$(date +%Y%m%d_%H%M%S)
    local backup_path="$BACKUP_DIR/backup_$backup_timestamp"
    
    mkdir -p "$backup_path"
    
    # 备份现有JAR文件
    if [[ -f "$BACKEND_DIR/liutech-backend.jar" ]]; then
        cp "$BACKEND_DIR/liutech-backend.jar" "$backup_path/"
        print_success "后端服务已备份"
    fi
    
    if [[ -f "$AI_SERVICE_DIR/liutech-ai.jar" ]]; then
        cp "$AI_SERVICE_DIR/liutech-ai.jar" "$backup_path/"
        print_success "AI服务已备份"
    fi
    
    # 备份配置文件
    if [[ -f "$BACKEND_DIR/application-prod.yml" ]]; then
        cp "$BACKEND_DIR/application-prod.yml" "$backup_path/"
    fi
    
    if [[ -f "$AI_SERVICE_DIR/application-prod.yml" ]]; then
        cp "$AI_SERVICE_DIR/application-prod.yml" "$backup_path/"
    fi
    
    print_success "服务备份完成: $backup_path"
}

# 停止现有服务
stop_services() {
    print_info "停止现有服务..."
    
    # 停止后端服务
    if systemctl is-active --quiet liutech-backend; then
        sudo systemctl stop liutech-backend
        print_success "后端服务已停止"
    fi
    
    # 停止AI服务
    if systemctl is-active --quiet liutech-ai; then
        sudo systemctl stop liutech-ai
        print_success "AI服务已停止"
    fi
}

# 构建项目
build_project() {
    print_info "构建项目..."
    
    cd ..
    
    # 清理并构建
    mvn clean package -DskipTests -Pprod
    
    if [[ $? -ne 0 ]]; then
        print_error "项目构建失败"
        exit 1
    fi
    
    print_success "项目构建完成"
    
    cd deploy
}

# 部署后端服务
deploy_backend() {
    print_info "部署后端服务..."
    
    # 检查JAR文件
    local jar_file="../LiuTech/target/liutech-backend-0.0.1-SNAPSHOT.jar"
    if [[ ! -f "$jar_file" ]]; then
        print_error "找不到后端JAR文件: $jar_file"
        exit 1
    fi
    
    # 复制JAR文件
    cp "$jar_file" "$BACKEND_DIR/liutech-backend.jar"
    sudo chown liutech:liutech "$BACKEND_DIR/liutech-backend.jar"
    
    print_success "后端服务部署完成"
}

# 部署AI服务
deploy_ai_service() {
    print_info "部署AI服务..."
    
    # 检查JAR文件
    local jar_file="../LiuTech-AI/target/liutech-ai-0.0.1-SNAPSHOT.jar"
    if [[ ! -f "$jar_file" ]]; then
        print_error "找不到AI服务JAR文件: $jar_file"
        exit 1
    fi
    
    # 复制JAR文件
    cp "$jar_file" "$AI_SERVICE_DIR/liutech-ai.jar"
    sudo chown liutech:liutech "$AI_SERVICE_DIR/liutech-ai.jar"
    
    print_success "AI服务部署完成"
}

# 创建systemd服务文件
create_systemd_services() {
    print_info "创建systemd服务文件..."
    
    # 创建后端服务文件
    sudo tee /etc/systemd/system/liutech-backend.service > /dev/null << EOF
[Unit]
Description=LiuTech Backend Service
After=network.target mysql.service
Wants=mysql.service

[Service]
Type=simple
User=liutech
Group=liutech
WorkingDirectory=/opt/liutech/backend
ExecStart=/usr/bin/java -Xms512m -Xmx2g -Dspring.profiles.active=prod -jar /opt/liutech/backend/liutech-backend.jar
ExecStop=/bin/kill -15 \$MAINPID
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=liutech-backend
KillMode=mixed
KillSignal=SIGTERM
TimeoutStopSec=30

# 环境变量
Environment=JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
EnvironmentFile=-/opt/liutech/.env

# 安全设置
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ReadWritePaths=/opt/liutech/logs /opt/liutech/uploads

[Install]
WantedBy=multi-user.target
EOF

    # 创建AI服务文件
    sudo tee /etc/systemd/system/liutech-ai.service > /dev/null << EOF
[Unit]
Description=LiuTech AI Service
After=network.target mysql.service
Wants=mysql.service

[Service]
Type=simple
User=liutech
Group=liutech
WorkingDirectory=/opt/liutech/ai-service
ExecStart=/usr/bin/java -Xms256m -Xmx1g -Dspring.profiles.active=prod -jar /opt/liutech/ai-service/liutech-ai.jar
ExecStop=/bin/kill -15 \$MAINPID
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=liutech-ai
KillMode=mixed
KillSignal=SIGTERM
TimeoutStopSec=30

# 环境变量
Environment=JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
EnvironmentFile=-/opt/liutech/.env

# 安全设置
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ReadWritePaths=/opt/liutech/logs

[Install]
WantedBy=multi-user.target
EOF

    # 重新加载systemd
    sudo systemctl daemon-reload
    
    # 启用服务
    sudo systemctl enable liutech-backend
    sudo systemctl enable liutech-ai
    
    print_success "systemd服务文件创建完成"
}

# 启动服务
start_services() {
    print_info "启动服务..."
    
    # 启动后端服务
    sudo systemctl start liutech-backend
    sleep 5
    
    if systemctl is-active --quiet liutech-backend; then
        print_success "后端服务启动成功"
    else
        print_error "后端服务启动失败"
        sudo journalctl -u liutech-backend --no-pager -n 20
        exit 1
    fi
    
    # 启动AI服务
    sudo systemctl start liutech-ai
    sleep 5
    
    if systemctl is-active --quiet liutech-ai; then
        print_success "AI服务启动成功"
    else
        print_error "AI服务启动失败"
        sudo journalctl -u liutech-ai --no-pager -n 20
        exit 1
    fi
}

# 健康检查
health_check() {
    print_info "执行健康检查..."
    
    # 检查后端服务
    local backend_health=false
    for i in {1..30}; do
        if curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
            backend_health=true
            break
        fi
        sleep 2
    done
    
    if $backend_health; then
        print_success "后端服务健康检查通过"
    else
        print_warning "后端服务健康检查失败，请检查日志"
    fi
    
    # 检查AI服务
    local ai_health=false
    for i in {1..30}; do
        if curl -f http://localhost:8081/actuator/health > /dev/null 2>&1; then
            ai_health=true
            break
        fi
        sleep 2
    done
    
    if $ai_health; then
        print_success "AI服务健康检查通过"
    else
        print_warning "AI服务健康检查失败，请检查日志"
    fi
}

# 创建管理脚本
create_management_scripts() {
    print_info "创建管理脚本..."
    
    # 创建服务管理脚本
    cat > /opt/liutech/scripts/manage-services.sh << 'EOF'
#!/bin/bash

# LiuTech 服务管理脚本

case "$1" in
    start)
        echo "启动LiuTech服务..."
        sudo systemctl start liutech-backend
        sudo systemctl start liutech-ai
        echo "服务启动完成"
        ;;
    stop)
        echo "停止LiuTech服务..."
        sudo systemctl stop liutech-backend
        sudo systemctl stop liutech-ai
        echo "服务停止完成"
        ;;
    restart)
        echo "重启LiuTech服务..."
        sudo systemctl restart liutech-backend
        sudo systemctl restart liutech-ai
        echo "服务重启完成"
        ;;
    status)
        echo "LiuTech服务状态："
        echo "后端服务："
        sudo systemctl status liutech-backend --no-pager -l
        echo ""
        echo "AI服务："
        sudo systemctl status liutech-ai --no-pager -l
        ;;
    logs)
        service=${2:-backend}
        case "$service" in
            backend)
                sudo journalctl -u liutech-backend -f
                ;;
            ai)
                sudo journalctl -u liutech-ai -f
                ;;
            *)
                echo "用法: $0 logs [backend|ai]"
                ;;
        esac
        ;;
    *)
        echo "用法: $0 {start|stop|restart|status|logs}"
        exit 1
        ;;
esac
EOF

    chmod +x /opt/liutech/scripts/manage-services.sh
    sudo chown liutech:liutech /opt/liutech/scripts/manage-services.sh
    
    print_success "管理脚本创建完成"
}

# 主函数
main() {
    print_info "开始部署LiuTech后端服务..."
    
    check_source
    backup_services
    stop_services
    build_project
    deploy_backend
    deploy_ai_service
    create_systemd_services
    start_services
    health_check
    create_management_scripts
    
    print_success "后端服务部署完成！"
    print_info "服务信息："
    print_info "  后端服务: http://localhost:8080"
    print_info "  AI服务: http://localhost:8081"
    print_info "  管理脚本: /opt/liutech/scripts/manage-services.sh"
    print_info ""
    print_info "常用命令："
    print_info "  查看服务状态: sudo systemctl status liutech-backend"
    print_info "  查看服务日志: sudo journalctl -u liutech-backend -f"
    print_info "  重启服务: sudo systemctl restart liutech-backend"
}

# 执行主函数
main "$@"