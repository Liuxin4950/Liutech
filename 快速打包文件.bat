@echo off
:: 切换UTF-8编码，并重定向输出避免多余提示
chcp 65001 >nul
REM LiuTech Project Build Script (Windows)
REM Author: Liu Xin
REM Description: Ensure JAR is repackaged and images are rebuilt after code changes

cd /d "%~dp0" 2>nul
if errorlevel 1 (
    echo [ERROR] 进入脚本所在目录失败！
    pause
    exit /b 1
)

set "PROJECT_ROOT=%cd%"

echo ==========================================
echo LiuTech Project Build Started
echo Time: %date% %time%
echo ==========================================
echo.

goto :main

:error_exit
echo [ERROR] %~1
cd /d "%PROJECT_ROOT%" 2>nul
if not defined LIUTECH_NO_PAUSE pause
exit /b 1

:main
REM Prerequisites
where mvn >nul 2>nul
if errorlevel 1 (
    call :error_exit "未找到mvn，请先安装Maven并配置PATH！"
)
where docker >nul 2>nul
if errorlevel 1 (
    call :error_exit "未找到docker，请先安装Docker Desktop并配置PATH！"
)
where npm >nul 2>nul
if errorlevel 1 (
    call :error_exit "未找到npm，请先安装Node.js并配置PATH！"
)

REM 1. Build Backend
echo [1/5] Building Backend Service...
echo Cleaning and compiling backend project...
if not exist "LiuTech\pom.xml" (
    call :error_exit "Backend目录下的pom.xml文件不存在！"
)
call mvn -f "LiuTech\pom.xml" clean package -DskipTests
if errorlevel 1 (
    call :error_exit "Backend compilation failed!"
)

echo Building backend Docker image...
docker build -t liutech-backend:latest -f LiuTech/Dockerfile LiuTech
if errorlevel 1 (
    call :error_exit "Backend image build failed!"
)
echo.

REM 2. Build AI Service
echo [2/5] Building AI Service...
echo Cleaning and compiling AI project...
if not exist "LiuTech-AI\pom.xml" (
    call :error_exit "AI Service目录下的pom.xml文件不存在！"
)
call mvn -f "LiuTech-AI\pom.xml" clean package -DskipTests
if errorlevel 1 (
    call :error_exit "AI service compilation failed!"
)

echo Building AI Docker image...
docker build -t liutech-ai:latest -f LiuTech-AI/Dockerfile LiuTech-AI
if errorlevel 1 (
    call :error_exit "AI image build failed!"
)
echo.

REM 3. Build Web Frontend
echo [3/5] Building Web Frontend...
if not exist "Web" (
    call :error_exit "Web前端目录不存在！"
)
if not exist "Web\Dockerfile" (
    call :error_exit "Web/Dockerfile文件不存在！"
)

cd /d "%PROJECT_ROOT%\Web"
if errorlevel 1 (
    call :error_exit "进入Web目录失败！"
)

echo Installing Web frontend dependencies...
call npm install
if errorlevel 1 (
    call :error_exit "Web frontend dependency installation failed!"
)

echo Building Web frontend...
call npm run build
if errorlevel 1 (
    call :error_exit "Web frontend build failed!"
)

echo Building Web Docker image...
docker build -t liutech-web:latest -f Dockerfile .
if errorlevel 1 (
    call :error_exit "Web frontend image build failed!"
)

cd /d "%PROJECT_ROOT%"
if errorlevel 1 (
    call :error_exit "回退到项目根目录失败！"
)
echo.

REM 4. Build Admin Frontend
echo [4/5] Building Admin Frontend...
if not exist "Admin" (
    call :error_exit "Admin前端目录不存在！"
)
if not exist "Admin\Dockerfile" (
    call :error_exit "Admin/Dockerfile文件不存在！"
)

cd /d "%PROJECT_ROOT%\Admin"
if errorlevel 1 (
    call :error_exit "进入Admin目录失败！"
)

echo Installing Admin frontend dependencies...
call npm install
if errorlevel 1 (
    call :error_exit "Admin frontend dependency installation failed!"
)

echo Building Admin frontend...
call npm run build
if errorlevel 1 (
    call :error_exit "Admin frontend build failed!"
)

echo Building Admin Docker image...
docker build -t liutech-admin:latest -f Dockerfile .
if errorlevel 1 (
    call :error_exit "Admin frontend image build failed!"
)

cd /d "%PROJECT_ROOT%"
if errorlevel 1 (
    call :error_exit "回退到项目根目录失败！"
)
echo.

REM 5. Build Nginx
echo [5/5] Building Nginx Service...
if not exist "nginx\Dockerfile" (
    call :error_exit "Nginx目录下的Dockerfile文件不存在！"
)
docker build -t liutech-nginx:latest nginx
if errorlevel 1 (
    call :error_exit "Nginx image build failed!"
)
echo.

REM Display build results
echo ==========================================
echo [SUCCESS] Build completed! Image list:
docker images | findstr /i liutech
echo ==========================================
echo.
echo [INFO] Use the following commands to start services:
echo   Local development: docker-compose up -d
echo ==========================================
echo Build script completed successfully!
echo 打包完成！使用以下命令 docker-compose up -d 在根目录启动容器编排启动服务：
if not defined LIUTECH_NO_PAUSE pause
