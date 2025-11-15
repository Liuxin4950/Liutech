FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache tzdata && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone
RUN mkdir -p /app/logs /app/uploads
COPY target/liutech-ai-*.jar app.jar
EXPOSE 8081
CMD ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]