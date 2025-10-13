# Nginx Dockerfile
# 作者: 刘鑫
# 时间: 2025-01-01

FROM nginx:latest

# 复制自定义配置文件
COPY nginx.conf /etc/nginx/nginx.conf
COPY conf.d/ /etc/nginx/conf.d/

# 暴露端口
EXPOSE 80 443

# 启动Nginx
CMD ["nginx", "-g", "daemon off;"]