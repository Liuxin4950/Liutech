@echo off
chcp 65001
REM LiuTech 项目构建脚本 (Windows)
REM 作者: 刘鑫
REM 描述: 确保代码更改后重新打包JAR并重建镜像

echo ==========================================
echo LiuTech 项目构建开始
echo 时间: %date% %time%
echo ==========================================

REM 1. 构建后端服务
echo [1/5] 构建后端服务...
echo 清理并编译后端项目...
call mvn -f LiuTech/pom.xml clean package -DskipTests
if %errorlevel% neq 0 (
    echo [错误] 后端编译失败!
    pause
    exit /b 1
)

echo 构建后端Docker镜像...
call docker build -t liutech-backend:latest -f LiuTech/Dockerfile LiuTech
if %errorlevel% neq 0 (
    echo [错误] 后端镜像构建失败!
    pause
    exit /b 1
)

REM 2. 构建AI服务
echo [2/5] 构建AI服务...
echo 清理并编译AI项目...
call mvn -f LiuTech-AI/pom.xml clean package -DskipTests
if %errorlevel% neq 0 (
    echo [错误] AI服务编译失败!
    pause
    exit /b 1
)

echo 构建AI Docker镜像...
call docker build -t liutech-ai:latest -f LiuTech-AI/Dockerfile LiuTech-AI
if %errorlevel% neq 0 (
    echo [错误] AI镜像构建失败!
    pause
    exit /b 1
)

REM 3. 构建Web前端
echo [3/5] 构建Web前端...
cd Web
call npm ci
if %errorlevel% neq 0 (
    echo [错误] Web前端依赖安装失败!
    cd ..
    pause
    exit /b 1
)

call npm run build
if %errorlevel% neq 0 (
    echo [错误] Web前端构建失败!
    cd ..
    pause
    exit /b 1
)

call docker build -t liutech-web:latest .
if %errorlevel% neq 0 (
    echo [错误] Web前端镜像构建失败!
    cd ..
    pause
    exit /b 1
)
cd ..

REM 4. 构建管理后台前端
echo [4/5] 构建管理后台前端...
cd Admin
call npm ci
if %errorlevel% neq 0 (
    echo [错误] 管理后台前端依赖安装失败!
    cd ..
    pause
    exit /b 1
)

call npm run build
if %errorlevel% neq 0 (
    echo [错误] 管理后台前端构建失败!
    cd ..
    pause
    exit /b 1
)

call docker build -t liutech-admin:latest .
if %errorlevel% neq 0 (
    echo [错误] 管理后台前端镜像构建失败!
    cd ..
    pause
    exit /b 1
)
cd ..

REM 5. 构建Nginx服务
echo [5/5] 构建Nginx服务...
call docker build -t liutech-nginx:latest nginx/
if %errorlevel% neq 0 (
    echo [错误] Nginx镜像构建失败!
    pause
    exit /b 1
)

REM 5. 显示构建结果
echo ==========================================
echo [成功] 构建完成！镜像列表：
call docker images | findstr liutech
echo ==========================================

echo [信息] 使用以下命令启动服务：
echo   本地开发：docker-compose up -d
echo ==========================================
echo 构建脚本执行成功！
echo 打包完成！使用以下命令 docker-compose up -d 在根目录启动容器编排启动服务：
pause
