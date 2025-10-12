# Web前端项目Dockerfile
# 作者: 刘鑫
# 时间: 2025-01-01

# 第一阶段：构建阶段 使用node镜像为基础，因为需要npm来构建项目
FROM node:20-alpine as builder

# 设置工作目录 设置存储目录在容器根目录/app文件夹下
WORKDIR /app

# 复制package.json和package-lock.json到工作目录
COPY package*.json ./

# 安装依赖
RUN npm ci

# 复制源代码
COPY . .

# 构建项目
RUN npm run build

# 第二阶段：生产阶段
FROM nginx:alpine

# 复制构建产物到nginx目录
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制nginx配置文件
COPY nginx.conf /etc/nginx/nginx.conf

# 暴露端口
EXPOSE 80

# 启动nginx
CMD ["nginx", "-g", "daemon off;"]