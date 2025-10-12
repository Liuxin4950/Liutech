# 使用OpenJDK 21作为基础镜像
FROM openjdk:21

# 设置工作目录
WORKDIR /app

# 创建必要的目录
RUN mkdir -p /app/logs /app/uploads

# 复制jar文件到容器中
COPY target/liutech-backend-*.jar app.jar

# 设置时区
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime

# 暴露端口
EXPOSE 8080

# 启动应用
CMD ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]