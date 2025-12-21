@echo off
:: 切换UTF-8编码，并重定向输出避免多余提示
chcp 65001 >nul
REM LiuTech Project Build Script (Windows)
REM Author: Liu Xin
REM Description: Ensure JAR is repackaged and images are rebuilt after code changes

echo ==========================================
echo LiuTech Project Build Started
echo Time: %date% %time%
echo ==========================================
echo.

goto :main

:error_exit
echo [ERROR] %~1
pause
exit /b 1

:main
REM 1. Build Backend
echo [1/5] Building Backend Service...
echo Cleaning and compiling backend project...
:: 检查pom.xml是否存在，提前报错
if not exist "LiuTech/pom.xml" (
    call :error_exit "Backend目录下的pom.xml文件不存在！"
)
call mvn -f LiuTech/pom.xml clean package -DskipTests
:: 用if errorlevel 1替代%errorlevel%，更稳定
if errorlevel 1 (
    call :error_exit "Backend compilation failed!"
)

echo Building backend Docker image...
:: 移除冗余的call
docker build -t liutech-backend:latest -f LiuTech/Dockerfile LiuTech
if errorlevel 1 (
    call :error_exit "Backend image build failed!"
)
echo.

REM 2. Build AI Service
echo [2/5] Building AI Service...
echo Cleaning and compiling AI project...
if not exist "LiuTech-AI/pom.xml" (
    call :error_exit "AI Service目录下的pom.xml文件不存在！"
)
call mvn -f LiuTech-AI/pom.xml clean package -DskipTests
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
:: 检查Web目录是否存在
if not exist "Web" (
    call :error_exit "Web前端目录不存在！"
)
:: 用cd /d确保跨盘符切换，||表示切换失败则直接退出
cd /d Web || call :error_exit "进入Web目录失败！"
echo Installing Web frontend dependencies...
call npm install
if errorlevel 1 (
    :: 失败后先回退目录，再退出
    cd ..
    call :error_exit "Web frontend dependency installation failed!"
)

echo Building Web frontend...
call npm run build
if errorlevel 1 (
    cd ..
    call :error_exit "Web frontend build failed!"
)

echo Building Web Docker image...
docker build -t liutech-web:latest .
if errorlevel 1 (
    cd ..
    call :error_exit "Web frontend image build failed!"
)
:: 回退到上级目录
cd ..
echo.

REM 4. Build Admin Frontend
echo [4/5] Building Admin Frontend...
if not exist "Admin" (
    call :error_exit "Admin前端目录不存在！"
)
cd /d Admin || call :error_exit "进入Admin目录失败！"
echo Installing Admin frontend dependencies...
call npm install
if errorlevel 1 (
    cd ..
    call :error_exit "Admin frontend dependency installation failed!"
)

echo Building Admin frontend...
call npm run build
if errorlevel 1 (
    cd ..
    call :error_exit "Admin frontend build failed!"
)

echo Building Admin Docker image...
docker build -t liutech-admin:latest .
if errorlevel 1 (
    cd ..
    call :error_exit "Admin frontend image build failed!"
)
cd ..
echo.

REM 5. Build Nginx
echo [5/5] Building Nginx Service...
if not exist "nginx/Dockerfile" (
    call :error_exit "Nginx目录下的Dockerfile文件不存在！"
)
:: 修复路径：将nginx/改为nginx，避免解析异常
docker build -t liutech-nginx:latest nginx
if errorlevel 1 (
    call :error_exit "Nginx image build failed!"
)
echo.

REM Display build results
echo ==========================================
echo [SUCCESS] Build completed! Image list:
:: 移除冗余的call，findstr区分大小写，加/i忽略大小写
docker images | findstr /i liutech
echo ==========================================
echo.
echo [INFO] Use the following commands to start services:
echo   Local development: docker-compose up -d
echo ==========================================
echo Build script completed successfully!
echo 打包完成！使用以下命令 docker-compose up -d 在根目录启动容器编排启动服务：
pause
