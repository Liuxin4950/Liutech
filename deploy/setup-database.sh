#!/bin/bash

# LiuTech 数据库配置脚本
# 作者: 刘鑫
# 时间: 2025-09-29

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 数据库配置
DB_NAME="liutech"
DB_AI_NAME="liutech_ai"
DB_USER="liutech_user"

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

# 检查MySQL是否运行
check_mysql() {
    print_info "检查MySQL服务状态..."
    
    if ! systemctl is-active --quiet mysql; then
        print_error "MySQL服务未运行，请先启动MySQL服务"
        exit 1
    fi
    
    print_success "MySQL服务正常运行"
}

# 获取数据库密码
get_passwords() {
    print_info "请设置数据库密码..."
    
    # 获取root密码
    read -s -p "请输入MySQL root密码: " MYSQL_ROOT_PASSWORD
    echo
    
    # 获取应用数据库密码
    read -s -p "请设置应用数据库密码: " DB_PASSWORD
    echo
    
    # 确认密码
    read -s -p "请再次输入应用数据库密码: " DB_PASSWORD_CONFIRM
    echo
    
    if [[ "$DB_PASSWORD" != "$DB_PASSWORD_CONFIRM" ]]; then
        print_error "两次输入的密码不一致！"
        exit 1
    fi
    
    print_success "密码设置完成"
}

# 创建数据库和用户
create_database() {
    print_info "创建数据库和用户..."
    
    # 创建SQL脚本
    cat > /tmp/setup_db.sql << EOF
-- 创建数据库
CREATE DATABASE IF NOT EXISTS ${DB_NAME} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ${DB_AI_NAME} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建用户
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';

-- 授权
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'localhost';
GRANT ALL PRIVILEGES ON ${DB_AI_NAME}.* TO '${DB_USER}'@'localhost';

-- 刷新权限
FLUSH PRIVILEGES;
EOF
    
    # 执行SQL脚本
    mysql -u root -p${MYSQL_ROOT_PASSWORD} < /tmp/setup_db.sql
    
    # 删除临时文件
    rm -f /tmp/setup_db.sql
    
    print_success "数据库和用户创建完成"
}

# 导入数据库结构
import_schema() {
    print_info "导入数据库结构..."
    
    # 检查SQL文件是否存在
    if [[ ! -f "../sql.sql" ]]; then
        print_error "找不到数据库结构文件 sql.sql"
        exit 1
    fi
    
    # 导入主数据库结构
    mysql -u ${DB_USER} -p${DB_PASSWORD} ${DB_NAME} < ../sql.sql
    
    # 检查AI数据库SQL文件
    if [[ -f "../LiuTech-AI/sql.sql" ]]; then
        mysql -u ${DB_USER} -p${DB_PASSWORD} ${DB_AI_NAME} < ../LiuTech-AI/sql.sql
        print_success "AI数据库结构导入完成"
    else
        print_warning "未找到AI数据库结构文件，跳过AI数据库初始化"
    fi
    
    print_success "数据库结构导入完成"
}

# 创建生产环境配置文件
create_prod_config() {
    print_info "创建生产环境配置文件..."
    
    # 创建后端生产配置
    cat > /opt/liutech/backend/application-prod.yml << EOF
# LiuTech 生产环境配置
# 作者: 刘鑫
# 时间: $(date '+%Y-%m-%d')

spring:
  application:
    name: LiuTech
  main:
    allow-circular-references: true
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
      enabled: true
  datasource:
    url: jdbc:mysql://localhost:3306/${DB_NAME}?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC&useUnicode=true&characterEncoding=utf8&autoReconnect=true&failOverReadOnly=false
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 10
      idle-timeout: 600000
      connection-timeout: 30000
      max-lifetime: 1800000
      leak-detection-threshold: 60000
      pool-name: HikariCP-Blog-Prod

mybatis-plus:
  mapper-locations: classpath:/mapper/**/*.xml
  type-aliases-package: chat.liuxin.liutech.model
  configuration:
    map-underscore-to-camel-case: true
    # 生产环境关闭SQL日志
    # log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: auto
      table-underline: true
      logic-delete-field: deletedAt
      logic-delete-value: "NOW()"
      logic-not-delete-value: "NULL"

logging:
  level:
    root: INFO
    chat.liuxin.liutech: INFO
    com.baomidou.mybatisplus: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: /opt/liutech/logs/backend.log
    max-size: 100MB
    max-history: 30

server:
  port: 8080

# 文件上传配置
file:
  upload-dir: /opt/liutech/uploads
  tomcat:
    uri-encoding: UTF-8

# JWT 配置 - 生产环境请修改密钥
jwt:
  secret: \${JWT_SECRET:liutech2024secretkey-very-long-and-secure-key-for-hmac-sha256-algorithm-minimum-256-bits}
  expiration: 604800000  # 7天，单位：毫秒
  header: Authorization
  prefix: Bearer
EOF

    # 创建AI服务生产配置
    cat > /opt/liutech/ai-service/application-prod.yml << EOF
# LiuTech AI Service 生产环境配置
# 作者: 刘鑫
# 时间: $(date '+%Y-%m-%d')

server:
  port: 8081

spring:
  application:
    name: LiuTech-AI
  datasource:
    url: jdbc:mysql://localhost:3306/${DB_AI_NAME}?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 60000
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: gemma3:4b
          temperature: 0.2
          timeout: 30s

mybatis-plus:
  mapper-locations: classpath:/mapper/**/*.xml
  type-aliases-package: chat.liuxin.ai.entity
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto
      table-underline: true
      logic-delete-value: 1
      logic-not-delete-value: 0

# JWT 配置
jwt:
  secret: \${JWT_SECRET:liutech2024secretkey-very-long-and-secure-key-for-hmac-sha256-algorithm-minimum-256-bits}
  expiration: 604800000
  header: Authorization
  prefix: Bearer

logging:
  level:
    root: INFO
    chat.liuxin.ai: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: /opt/liutech/logs/ai-service.log
    max-size: 100MB
    max-history: 30
EOF

    # 设置文件权限
    sudo chown liutech:liutech /opt/liutech/backend/application-prod.yml
    sudo chown liutech:liutech /opt/liutech/ai-service/application-prod.yml
    sudo chmod 600 /opt/liutech/backend/application-prod.yml
    sudo chmod 600 /opt/liutech/ai-service/application-prod.yml
    
    print_success "生产环境配置文件创建完成"
}

# 创建环境变量文件
create_env_file() {
    print_info "创建环境变量文件..."
    
    cat > /opt/liutech/.env << EOF
# LiuTech 环境变量配置
# 作者: 刘鑫
# 时间: $(date '+%Y-%m-%d')

# 数据库配置
DB_HOST=localhost
DB_NAME=${DB_NAME}
DB_AI_NAME=${DB_AI_NAME}
DB_USERNAME=${DB_USER}
DB_PASSWORD=${DB_PASSWORD}

# 应用配置
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
AI_SERVICE_PORT=8081

# JWT配置 - 生产环境请修改
JWT_SECRET=liutech2024secretkey-very-long-and-secure-key-for-hmac-sha256-algorithm-minimum-256-bits

# 文件上传配置
FILE_UPLOAD_PATH=/opt/liutech/uploads

# 日志配置
LOG_PATH=/opt/liutech/logs
EOF

    sudo chown liutech:liutech /opt/liutech/.env
    sudo chmod 600 /opt/liutech/.env
    
    print_success "环境变量文件创建完成"
}

# 测试数据库连接
test_connection() {
    print_info "测试数据库连接..."
    
    # 测试主数据库连接
    if mysql -u ${DB_USER} -p${DB_PASSWORD} -e "USE ${DB_NAME}; SELECT 1;" > /dev/null 2>&1; then
        print_success "主数据库连接测试成功"
    else
        print_error "主数据库连接测试失败"
        exit 1
    fi
    
    # 测试AI数据库连接
    if mysql -u ${DB_USER} -p${DB_PASSWORD} -e "USE ${DB_AI_NAME}; SELECT 1;" > /dev/null 2>&1; then
        print_success "AI数据库连接测试成功"
    else
        print_error "AI数据库连接测试失败"
        exit 1
    fi
}

# 主函数
main() {
    print_info "开始配置LiuTech数据库..."
    
    check_mysql
    get_passwords
    create_database
    import_schema
    create_prod_config
    create_env_file
    test_connection
    
    print_success "数据库配置完成！"
    print_info "数据库信息："
    print_info "  主数据库: ${DB_NAME}"
    print_info "  AI数据库: ${DB_AI_NAME}"
    print_info "  用户名: ${DB_USER}"
    print_info "  配置文件: /opt/liutech/backend/application-prod.yml"
    print_info "  环境变量: /opt/liutech/.env"
}

# 执行主函数
main "$@"