@echo off
chcp 65001
REM LiuTech Project Build Script (Windows)
REM Author: Liu Xin
REM Description: Ensure JAR is repackaged and images are rebuilt after code changes

echo ==========================================
echo LiuTech Project Build Started
echo Time: %date% %time%
echo ==========================================

REM 1. Build Backend
echo [1/5] Building Backend Service...
echo Cleaning and compiling backend project...
call mvn -f LiuTech/pom.xml clean package -DskipTests
if %errorlevel% neq 0 (
    echo [ERROR] Backend compilation failed!
    pause
    exit /b 1
)

echo Building backend Docker image...
call docker build -t liutech-backend:latest -f LiuTech/Dockerfile LiuTech
if %errorlevel% neq 0 (
    echo [ERROR] Backend image build failed!
    pause
    exit /b 1
)

REM 2. Build AI Service
echo [2/5] Building AI Service...
echo Cleaning and compiling AI project...
call mvn -f LiuTech-AI/pom.xml clean package -DskipTests
if %errorlevel% neq 0 (
    echo [ERROR] AI service compilation failed!
    pause
    exit /b 1
)

echo Building AI Docker image...
call docker build -t liutech-ai:latest -f LiuTech-AI/Dockerfile LiuTech-AI
if %errorlevel% neq 0 (
    echo [ERROR] AI image build failed!
    pause
    exit /b 1
)

REM 3. Build Web Frontend
echo [3/5] Building Web Frontend...
cd Web
call npm ci
if %errorlevel% neq 0 (
    echo [ERROR] Web frontend dependency installation failed!
    cd ..
    pause
    exit /b 1
)

call npm run build
if %errorlevel% neq 0 (
    echo [ERROR] Web frontend build failed!
    cd ..
    pause
    exit /b 1
)

call docker build -t liutech-web:latest .
if %errorlevel% neq 0 (
    echo [ERROR] Web frontend image build failed!
    cd ..
    pause
    exit /b 1
)
cd ..

REM 4. Build Admin Frontend
echo [4/5] Building Admin Frontend...
cd Admin
call npm ci
if %errorlevel% neq 0 (
    echo [ERROR] Admin frontend dependency installation failed!
    cd ..
    pause
    exit /b 1
)

call npm run build
if %errorlevel% neq 0 (
    echo [ERROR] Admin frontend build failed!
    cd ..
    pause
    exit /b 1
)

call docker build -t liutech-admin:latest .
if %errorlevel% neq 0 (
    echo [ERROR] Admin frontend image build failed!
    cd ..
    pause
    exit /b 1
)
cd ..

REM 5. Build Nginx
echo [5/5] Building Nginx Service...
call docker build -t liutech-nginx:latest nginx/
if %errorlevel% neq 0 (
    echo [ERROR] Nginx image build failed!
    pause
    exit /b 1
)

REM 5. Display build results
echo ==========================================
echo [SUCCESS] Build completed! Image list:
call docker images | findstr liutech
echo ==========================================

echo [INFO] Use the following commands to start services:
echo   Local development: docker-compose up -d
echo ==========================================
echo Build script completed successfully!
echo 打包完成！使用以下命令 docker-compose up -d 在根目录启动容器编排启动服务：
pause
