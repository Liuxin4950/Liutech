#!/bin/bash

# LiuTech 系统监控配置脚本
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
MONITOR_DIR="$DEPLOY_DIR/monitor"
SCRIPTS_DIR="$DEPLOY_DIR/scripts"

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

# 创建监控目录
create_monitor_dirs() {
    print_info "创建监控目录..."
    
    sudo mkdir -p "$MONITOR_DIR"/{logs,alerts,reports}
    sudo chown -R liutech:liutech "$MONITOR_DIR"
    
    print_success "监控目录创建完成"
}

# 创建系统监控脚本
create_system_monitor() {
    print_info "创建系统监控脚本..."
    
    cat > "$SCRIPTS_DIR/system-monitor.sh" << 'EOF'
#!/bin/bash

# LiuTech 系统监控脚本
# 作者: 刘鑫

# 配置
MONITOR_DIR="/opt/liutech/monitor"
LOG_FILE="$MONITOR_DIR/logs/system-$(date +%Y%m%d).log"
ALERT_FILE="$MONITOR_DIR/alerts/alerts-$(date +%Y%m%d).log"

# 阈值配置
CPU_THRESHOLD=80
MEMORY_THRESHOLD=80
DISK_THRESHOLD=85
LOAD_THRESHOLD=2.0

# 日志函数
log_message() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$LOG_FILE"
}

# 告警函数
send_alert() {
    local message="$1"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ALERT: $message" >> "$ALERT_FILE"
    echo "ALERT: $message"
}

# 检查CPU使用率
check_cpu() {
    local cpu_usage=$(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | awk -F'%' '{print $1}')
    cpu_usage=${cpu_usage%.*}
    
    log_message "CPU使用率: ${cpu_usage}%"
    
    if (( cpu_usage > CPU_THRESHOLD )); then
        send_alert "CPU使用率过高: ${cpu_usage}% (阈值: ${CPU_THRESHOLD}%)"
    fi
}

# 检查内存使用率
check_memory() {
    local memory_info=$(free | grep Mem)
    local total=$(echo $memory_info | awk '{print $2}')
    local used=$(echo $memory_info | awk '{print $3}')
    local memory_usage=$((used * 100 / total))
    
    log_message "内存使用率: ${memory_usage}%"
    
    if (( memory_usage > MEMORY_THRESHOLD )); then
        send_alert "内存使用率过高: ${memory_usage}% (阈值: ${MEMORY_THRESHOLD}%)"
    fi
}

# 检查磁盘使用率
check_disk() {
    local disk_usage=$(df -h / | awk 'NR==2 {print $5}' | sed 's/%//')
    
    log_message "磁盘使用率: ${disk_usage}%"
    
    if (( disk_usage > DISK_THRESHOLD )); then
        send_alert "磁盘使用率过高: ${disk_usage}% (阈值: ${DISK_THRESHOLD}%)"
    fi
}

# 检查系统负载
check_load() {
    local load_avg=$(uptime | awk -F'load average:' '{print $2}' | awk '{print $1}' | sed 's/,//')
    
    log_message "系统负载: $load_avg"
    
    if (( $(echo "$load_avg > $LOAD_THRESHOLD" | bc -l) )); then
        send_alert "系统负载过高: $load_avg (阈值: $LOAD_THRESHOLD)"
    fi
}

# 检查服务状态
check_services() {
    local services=("liutech-backend" "liutech-ai" "nginx" "mysql")
    
    for service in "${services[@]}"; do
        if systemctl is-active --quiet "$service"; then
            log_message "服务 $service: 运行正常"
        else
            send_alert "服务 $service: 未运行"
        fi
    done
}

# 检查端口状态
check_ports() {
    local ports=("80:Nginx" "443:Nginx-SSL" "8080:Backend" "8081:AI-Service" "3306:MySQL")
    
    for port_info in "${ports[@]}"; do
        local port=$(echo $port_info | cut -d':' -f1)
        local service=$(echo $port_info | cut -d':' -f2)
        
        if netstat -tuln | grep -q ":$port "; then
            log_message "端口 $port ($service): 监听正常"
        else
            send_alert "端口 $port ($service): 未监听"
        fi
    done
}

# 检查应用健康状态
check_app_health() {
    # 检查后端API
    if curl -f http://localhost:8080/api/health > /dev/null 2>&1; then
        log_message "后端API: 健康"
    else
        send_alert "后端API: 不健康"
    fi
    
    # 检查AI服务
    if curl -f http://localhost:8081/ai/health > /dev/null 2>&1; then
        log_message "AI服务: 健康"
    else
        send_alert "AI服务: 不健康"
    fi
    
    # 检查前端
    if curl -f http://localhost/ > /dev/null 2>&1; then
        log_message "前端: 可访问"
    else
        send_alert "前端: 不可访问"
    fi
}

# 主监控函数
main() {
    mkdir -p "$(dirname "$LOG_FILE")"
    mkdir -p "$(dirname "$ALERT_FILE")"
    
    log_message "开始系统监控检查"
    
    check_cpu
    check_memory
    check_disk
    check_load
    check_services
    check_ports
    check_app_health
    
    log_message "系统监控检查完成"
}

main "$@"
EOF

    chmod +x "$SCRIPTS_DIR/system-monitor.sh"
    
    print_success "系统监控脚本创建完成"
}

# 创建日志清理脚本
create_log_cleanup() {
    print_info "创建日志清理脚本..."
    
    cat > "$SCRIPTS_DIR/cleanup-logs.sh" << 'EOF'
#!/bin/bash

# LiuTech 日志清理脚本
# 作者: 刘鑫

# 配置
DEPLOY_DIR="/opt/liutech"
DAYS_TO_KEEP=30

echo "开始清理日志文件..."

# 清理应用日志
find "$DEPLOY_DIR/logs" -name "*.log" -mtime +$DAYS_TO_KEEP -delete 2>/dev/null || true

# 清理监控日志
find "$DEPLOY_DIR/monitor/logs" -name "*.log" -mtime +$DAYS_TO_KEEP -delete 2>/dev/null || true

# 清理告警日志
find "$DEPLOY_DIR/monitor/alerts" -name "*.log" -mtime +$DAYS_TO_KEEP -delete 2>/dev/null || true

# 清理Nginx日志
find /var/log/nginx -name "*.log.*" -mtime +$DAYS_TO_KEEP -delete 2>/dev/null || true

# 清理系统日志
sudo journalctl --vacuum-time=${DAYS_TO_KEEP}d

echo "日志清理完成"
EOF

    chmod +x "$SCRIPTS_DIR/cleanup-logs.sh"
    
    print_success "日志清理脚本创建完成"
}

# 创建备份脚本
create_backup_script() {
    print_info "创建备份脚本..."
    
    cat > "$SCRIPTS_DIR/backup-system.sh" << 'EOF'
#!/bin/bash

# LiuTech 系统备份脚本
# 作者: 刘鑫

set -e

# 配置
BACKUP_DIR="/opt/liutech/backup"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_PATH="$BACKUP_DIR/backup_$DATE"

# 数据库配置
DB_USER="liutech_user"
DB_PASS=""
DB_NAMES=("liutech" "liutech_ai")

echo "开始系统备份..."

# 创建备份目录
mkdir -p "$BACKUP_PATH"/{database,config,uploads,logs}

# 备份数据库
echo "备份数据库..."
for db in "${DB_NAMES[@]}"; do
    mysqldump -u "$DB_USER" -p"$DB_PASS" "$db" > "$BACKUP_PATH/database/${db}_$DATE.sql"
    echo "数据库 $db 备份完成"
done

# 备份配置文件
echo "备份配置文件..."
cp -r /opt/liutech/backend/application-prod.yml "$BACKUP_PATH/config/" 2>/dev/null || true
cp -r /opt/liutech/ai-service/application-prod.yml "$BACKUP_PATH/config/" 2>/dev/null || true
cp -r /etc/nginx/sites-available/liutech "$BACKUP_PATH/config/" 2>/dev/null || true

# 备份上传文件
echo "备份上传文件..."
if [[ -d "/opt/liutech/uploads" ]]; then
    cp -r /opt/liutech/uploads/* "$BACKUP_PATH/uploads/" 2>/dev/null || true
fi

# 备份重要日志
echo "备份重要日志..."
cp -r /opt/liutech/logs/* "$BACKUP_PATH/logs/" 2>/dev/null || true

# 压缩备份
echo "压缩备份文件..."
cd "$BACKUP_DIR"
tar -czf "backup_$DATE.tar.gz" "backup_$DATE"
rm -rf "backup_$DATE"

# 清理旧备份（保留7天）
find "$BACKUP_DIR" -name "backup_*.tar.gz" -mtime +7 -delete

echo "系统备份完成: $BACKUP_DIR/backup_$DATE.tar.gz"
EOF

    chmod +x "$SCRIPTS_DIR/backup-system.sh"
    
    print_success "备份脚本创建完成"
}

# 创建性能报告脚本
create_performance_report() {
    print_info "创建性能报告脚本..."
    
    cat > "$SCRIPTS_DIR/performance-report.sh" << 'EOF'
#!/bin/bash

# LiuTech 性能报告脚本
# 作者: 刘鑫

# 配置
REPORT_DIR="/opt/liutech/monitor/reports"
REPORT_FILE="$REPORT_DIR/performance-$(date +%Y%m%d).html"

# 创建HTML报告
cat > "$REPORT_FILE" << 'HTML_EOF'
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>LiuTech 系统性能报告</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .header { background: #f4f4f4; padding: 20px; border-radius: 5px; }
        .section { margin: 20px 0; padding: 15px; border: 1px solid #ddd; border-radius: 5px; }
        .metric { display: flex; justify-content: space-between; margin: 10px 0; }
        .good { color: green; }
        .warning { color: orange; }
        .critical { color: red; }
        table { width: 100%; border-collapse: collapse; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background-color: #f2f2f2; }
    </style>
</head>
<body>
    <div class="header">
        <h1>LiuTech 系统性能报告</h1>
        <p>生成时间: $(date '+%Y-%m-%d %H:%M:%S')</p>
    </div>
HTML_EOF

# 系统信息
cat >> "$REPORT_FILE" << HTML_EOF
    <div class="section">
        <h2>系统信息</h2>
        <div class="metric"><span>操作系统:</span><span>$(lsb_release -d | cut -f2)</span></div>
        <div class="metric"><span>内核版本:</span><span>$(uname -r)</span></div>
        <div class="metric"><span>运行时间:</span><span>$(uptime -p)</span></div>
        <div class="metric"><span>当前用户:</span><span>$(whoami)</span></div>
    </div>
HTML_EOF

# CPU信息
local cpu_usage=$(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | awk -F'%' '{print $1}')
local cpu_class="good"
if (( ${cpu_usage%.*} > 80 )); then cpu_class="critical"; elif (( ${cpu_usage%.*} > 60 )); then cpu_class="warning"; fi

cat >> "$REPORT_FILE" << HTML_EOF
    <div class="section">
        <h2>CPU 使用情况</h2>
        <div class="metric"><span>CPU使用率:</span><span class="$cpu_class">${cpu_usage}%</span></div>
        <div class="metric"><span>CPU核心数:</span><span>$(nproc)</span></div>
        <div class="metric"><span>平均负载:</span><span>$(uptime | awk -F'load average:' '{print $2}')</span></div>
    </div>
HTML_EOF

# 内存信息
local memory_info=$(free -h)
cat >> "$REPORT_FILE" << HTML_EOF
    <div class="section">
        <h2>内存使用情况</h2>
        <table>
            <tr><th>类型</th><th>总计</th><th>已用</th><th>可用</th><th>使用率</th></tr>
HTML_EOF

echo "$memory_info" | grep -E "Mem|Swap" | while read line; do
    local type=$(echo $line | awk '{print $1}' | sed 's/://')
    local total=$(echo $line | awk '{print $2}')
    local used=$(echo $line | awk '{print $3}')
    local available=$(echo $line | awk '{print $7}')
    local usage_percent=$(echo $line | awk '{printf "%.1f", ($3/$2)*100}')
    
    local class="good"
    if (( $(echo "$usage_percent > 80" | bc -l) )); then class="critical"; elif (( $(echo "$usage_percent > 60" | bc -l) )); then class="warning"; fi
    
    cat >> "$REPORT_FILE" << HTML_EOF
            <tr><td>$type</td><td>$total</td><td>$used</td><td>$available</td><td class="$class">${usage_percent}%</td></tr>
HTML_EOF
done

cat >> "$REPORT_FILE" << HTML_EOF
        </table>
    </div>
HTML_EOF

# 磁盘信息
cat >> "$REPORT_FILE" << HTML_EOF
    <div class="section">
        <h2>磁盘使用情况</h2>
        <table>
            <tr><th>文件系统</th><th>大小</th><th>已用</th><th>可用</th><th>使用率</th><th>挂载点</th></tr>
HTML_EOF

df -h | grep -vE '^Filesystem|tmpfs|cdrom' | while read line; do
    local filesystem=$(echo $line | awk '{print $1}')
    local size=$(echo $line | awk '{print $2}')
    local used=$(echo $line | awk '{print $3}')
    local available=$(echo $line | awk '{print $4}')
    local usage=$(echo $line | awk '{print $5}')
    local mount=$(echo $line | awk '{print $6}')
    
    local usage_num=$(echo $usage | sed 's/%//')
    local class="good"
    if (( usage_num > 85 )); then class="critical"; elif (( usage_num > 70 )); then class="warning"; fi
    
    cat >> "$REPORT_FILE" << HTML_EOF
            <tr><td>$filesystem</td><td>$size</td><td>$used</td><td>$available</td><td class="$class">$usage</td><td>$mount</td></tr>
HTML_EOF
done

cat >> "$REPORT_FILE" << HTML_EOF
        </table>
    </div>
HTML_EOF

# 服务状态
cat >> "$REPORT_FILE" << HTML_EOF
    <div class="section">
        <h2>服务状态</h2>
        <table>
            <tr><th>服务名称</th><th>状态</th><th>启动时间</th></tr>
HTML_EOF

local services=("liutech-backend" "liutech-ai" "nginx" "mysql")
for service in "${services[@]}"; do
    if systemctl is-active --quiet "$service"; then
        local status="<span class='good'>运行中</span>"
        local start_time=$(systemctl show "$service" --property=ActiveEnterTimestamp --value)
    else
        local status="<span class='critical'>已停止</span>"
        local start_time="N/A"
    fi
    
    cat >> "$REPORT_FILE" << HTML_EOF
            <tr><td>$service</td><td>$status</td><td>$start_time</td></tr>
HTML_EOF
done

cat >> "$REPORT_FILE" << HTML_EOF
        </table>
    </div>
HTML_EOF

# 网络连接
cat >> "$REPORT_FILE" << HTML_EOF
    <div class="section">
        <h2>网络连接</h2>
        <div class="metric"><span>活跃连接数:</span><span>$(netstat -an | grep ESTABLISHED | wc -l)</span></div>
        <div class="metric"><span>监听端口:</span><span>$(netstat -tuln | grep LISTEN | wc -l)</span></div>
    </div>
HTML_EOF

# 结束HTML
cat >> "$REPORT_FILE" << HTML_EOF
    <div class="section">
        <h2>报告说明</h2>
        <p>此报告每日自动生成，用于监控LiuTech博客系统的运行状态。</p>
        <p>颜色说明：<span class="good">绿色 - 正常</span>，<span class="warning">橙色 - 警告</span>，<span class="critical">红色 - 严重</span></p>
    </div>
</body>
</html>
HTML_EOF

echo "性能报告已生成: $REPORT_FILE"
EOF

    chmod +x "$SCRIPTS_DIR/performance-report.sh"
    
    print_success "性能报告脚本创建完成"
}

# 配置定时任务
setup_cron_jobs() {
    print_info "配置定时任务..."
    
    # 创建crontab配置
    cat > /tmp/liutech-cron << EOF
# LiuTech 系统监控定时任务
# 作者: 刘鑫
# 时间: $(date '+%Y-%m-%d')

# 每5分钟执行系统监控
*/5 * * * * /opt/liutech/scripts/system-monitor.sh

# 每天凌晨2点执行备份
0 2 * * * /opt/liutech/scripts/backup-system.sh

# 每天凌晨3点清理日志
0 3 * * * /opt/liutech/scripts/cleanup-logs.sh

# 每天早上8点生成性能报告
0 8 * * * /opt/liutech/scripts/performance-report.sh

# 每周日凌晨4点重启服务（可选）
# 0 4 * * 0 /opt/liutech/scripts/manage-services.sh restart
EOF

    # 安装crontab
    crontab /tmp/liutech-cron
    rm /tmp/liutech-cron
    
    print_success "定时任务配置完成"
}

# 创建监控仪表板
create_dashboard() {
    print_info "创建监控仪表板..."
    
    cat > "$MONITOR_DIR/dashboard.html" << 'EOF'
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>LiuTech 监控仪表板</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f5f5f5; }
        .header { background: #2c3e50; color: white; padding: 20px; text-align: center; }
        .container { max-width: 1200px; margin: 0 auto; padding: 20px; }
        .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; }
        .card { background: white; border-radius: 8px; padding: 20px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        .card h3 { color: #2c3e50; margin-bottom: 15px; border-bottom: 2px solid #3498db; padding-bottom: 10px; }
        .metric { display: flex; justify-content: space-between; margin: 10px 0; padding: 10px; background: #f8f9fa; border-radius: 4px; }
        .status-good { color: #27ae60; font-weight: bold; }
        .status-warning { color: #f39c12; font-weight: bold; }
        .status-critical { color: #e74c3c; font-weight: bold; }
        .refresh-btn { background: #3498db; color: white; border: none; padding: 10px 20px; border-radius: 4px; cursor: pointer; margin: 10px 0; }
        .refresh-btn:hover { background: #2980b9; }
        .log-viewer { max-height: 300px; overflow-y: auto; background: #2c3e50; color: #ecf0f1; padding: 15px; border-radius: 4px; font-family: monospace; font-size: 12px; }
    </style>
</head>
<body>
    <div class="header">
        <h1>LiuTech 监控仪表板</h1>
        <p>实时系统监控 - 最后更新: <span id="lastUpdate"></span></p>
        <button class="refresh-btn" onclick="refreshData()">刷新数据</button>
    </div>
    
    <div class="container">
        <div class="grid">
            <div class="card">
                <h3>系统状态</h3>
                <div id="systemStatus">加载中...</div>
            </div>
            
            <div class="card">
                <h3>服务状态</h3>
                <div id="serviceStatus">加载中...</div>
            </div>
            
            <div class="card">
                <h3>资源使用</h3>
                <div id="resourceUsage">加载中...</div>
            </div>
            
            <div class="card">
                <h3>最新告警</h3>
                <div id="recentAlerts" class="log-viewer">加载中...</div>
            </div>
        </div>
    </div>

    <script>
        function updateTimestamp() {
            document.getElementById('lastUpdate').textContent = new Date().toLocaleString('zh-CN');
        }
        
        function refreshData() {
            updateTimestamp();
            // 这里可以添加AJAX调用来获取实时数据
            // 目前显示静态示例数据
            
            document.getElementById('systemStatus').innerHTML = `
                <div class="metric"><span>系统运行时间:</span><span class="status-good">5天 12小时</span></div>
                <div class="metric"><span>系统负载:</span><span class="status-good">0.8</span></div>
                <div class="metric"><span>网络状态:</span><span class="status-good">正常</span></div>
            `;
            
            document.getElementById('serviceStatus').innerHTML = `
                <div class="metric"><span>后端服务:</span><span class="status-good">运行中</span></div>
                <div class="metric"><span>AI服务:</span><span class="status-good">运行中</span></div>
                <div class="metric"><span>Nginx:</span><span class="status-good">运行中</span></div>
                <div class="metric"><span>MySQL:</span><span class="status-good">运行中</span></div>
            `;
            
            document.getElementById('resourceUsage').innerHTML = `
                <div class="metric"><span>CPU使用率:</span><span class="status-good">25%</span></div>
                <div class="metric"><span>内存使用率:</span><span class="status-warning">68%</span></div>
                <div class="metric"><span>磁盘使用率:</span><span class="status-good">45%</span></div>
            `;
            
            document.getElementById('recentAlerts').innerHTML = `
                [2025-09-29 14:28:54] INFO: 系统监控启动
                [2025-09-29 14:25:00] INFO: 所有服务运行正常
                [2025-09-29 14:20:00] INFO: 系统资源使用正常
                [2025-09-29 14:15:00] INFO: 网络连接正常
            `;
        }
        
        // 页面加载时初始化
        window.onload = function() {
            refreshData();
            // 每30秒自动刷新
            setInterval(refreshData, 30000);
        };
    </script>
</body>
</html>
EOF

    print_success "监控仪表板创建完成"
}

# 主函数
main() {
    print_info "开始配置LiuTech系统监控..."
    
    create_monitor_dirs
    create_system_monitor
    create_log_cleanup
    create_backup_script
    create_performance_report
    setup_cron_jobs
    create_dashboard
    
    print_success "系统监控配置完成！"
    print_info "监控功能："
    print_info "  系统监控: 每5分钟执行一次"
    print_info "  自动备份: 每天凌晨2点执行"
    print_info "  日志清理: 每天凌晨3点执行"
    print_info "  性能报告: 每天早上8点生成"
    print_info ""
    print_info "监控文件："
    print_info "  监控脚本: /opt/liutech/scripts/system-monitor.sh"
    print_info "  备份脚本: /opt/liutech/scripts/backup-system.sh"
    print_info "  清理脚本: /opt/liutech/scripts/cleanup-logs.sh"
    print_info "  报告脚本: /opt/liutech/scripts/performance-report.sh"
    print_info "  监控仪表板: /opt/liutech/monitor/dashboard.html"
    print_info ""
    print_info "常用命令："
    print_info "  查看定时任务: crontab -l"
    print_info "  手动执行监控: /opt/liutech/scripts/system-monitor.sh"
    print_info "  手动备份: /opt/liutech/scripts/backup-system.sh"
    print_info "  查看监控日志: tail -f /opt/liutech/monitor/logs/system-$(date +%Y%m%d).log"
}

# 执行主函数
main "$@"