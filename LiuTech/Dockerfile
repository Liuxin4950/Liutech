# 作者：刘鑫
# 修改时间：2025-11-11
# 使用 Eclipse Temurin JRE 21 Alpine 作为基础镜像（运行Jar更轻量）
FROM eclipse-temurin:21-jre-alpine

# 设置工作目录
WORKDIR /app

# 安装时区数据并设置上海时区（alpine镜像默认不包含tzdata）
RUN apk add --no-cache tzdata && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 创建必要的目录
RUN mkdir -p /app/logs /app/uploads

# 复制jar文件到容器中
COPY target/liutech-backend-*.jar app.jar

# 时区已在上方设置，无需重复

# 暴露端口
EXPOSE 8080

# 启动应用
CMD ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
