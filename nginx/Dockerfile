# Nginx Dockerfile
# 作者: 刘鑫
# 时间: 2025-01-01

# 明确指定x86_64架构的nginx镜像
FROM nginx:alpine

# 复制自定义配置文件
COPY nginx.conf /etc/nginx/nginx.conf
COPY conf.d/ /etc/nginx/conf.d/

# 暴露端口
EXPOSE 80 443

# 启动Nginx
CMD ["nginx", "-g", "daemon off;"]