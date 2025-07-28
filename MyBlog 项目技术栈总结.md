


          
我需要分析项目结构和技术栈，查看后端Spring Boot项目配置、前端Vue项目配置以及数据库设计等关键文件，为后续开发提供技术栈总结和开发建议。
        
            
toolName: view_files
            
status: success
          
            
filePath: e:\MyBlog\LiuTech\pom.xml
          
查看前端Vue项目的package.json配置文件，了解前端技术栈。
        
            
toolName: view_files
            
status: success
          
            
filePath: e:\MyBlog\Web\package.json
          
查看父级pom.xml文件，了解整体项目的依赖版本管理。
        
            
toolName: view_files
            
status: success
          
            
filePath: e:\MyBlog\pom.xml
          
查看数据库设计文档，了解数据库结构和设计。
        
            
toolName: view_files
            
status: success
          
            
filePath: e:\MyBlog\数据库设计文档.md
          
查看后端应用配置文件，了解数据库连接和其他配置信息。
        
            
toolName: view_files
            
status: success
          
            
filePath: e:\MyBlog\LiuTech\src\main\resources\application.yml
          
查看前端项目的主要配置文件，了解构建工具和TypeScript配置。
        
            
toolName: view_files
            
status: success
          
            
filePath: e:\MyBlog\Web\vite.config.ts
e:\MyBlog\Web\tsconfig.json
          
查看项目的部署文档和需求分析，了解项目的整体架构和功能需求。
        
            
toolName: view_files
            
status: success
          
            
filePath: e:\MyBlog\部署文档.md
e:\MyBlog\需求分析.md
          
查看后端项目的主要包结构，了解代码组织架构。
        
            
toolName: view_folder
            
status: success
          
e:\MyBlog\LiuTech\src\main\java\chat\liuxin\liutech
          
查看前端项目的主要目录结构，了解前端代码组织。
        
            
toolName: view_folder
            
status: success
          
e:\MyBlog\Web\src
          
# 🚀 LiuTech 项目技术栈总结与开发建议

## 📋 项目概览
**LiuTech** 是一个前后端分离的个人博客系统，采用现代化技术栈构建，支持文章管理、用户系统、评论功能等核心特性。

---

## 🛠️ 技术栈详情

### 🔧 后端技术栈 (LiuTech模块)
- **框架**: Spring Boot 3.5.3 + Java 21
- **数据库**: MySQL 8.3.0 + MyBatis-Plus 3.5.12
- **安全**: Spring Security + JWT (jjwt 0.11.5)
- **工具库**: Lombok 1.18.32
- **构建工具**: Maven
- **连接池**: HikariCP
- **端口**: 8080

### 🎨 前端技术栈 (Web模块)
- **框架**: Vue 3.5.17 + TypeScript 5.8.3
- **构建工具**: Vite 7.0.3
- **状态管理**: Pinia 3.0.3
- **路由**: Vue Router 4.5.1
- **HTTP客户端**: Axios 1.10.0
- **端口**: 3000

### 🗄️ 数据库设计
- **核心表**: users, posts, categories, tags, comments
- **关联表**: post_tags (多对多关系)
- **扩展表**: email_verifications, resources, download_logs
- **特性**: 软删除、审计字段、积分系统

---

## 🎯 开发建议与最佳实践

### 📁 项目结构规范
```
后端 (LiuTech/)
├── controller/     # 控制器层 (web/admin分离)
├── service/        # 业务逻辑层
├── mapper/         # 数据访问层
├── model/          # 实体类
├── req/resl/       # 请求/响应DTO
├── common/         # 通用组件 (异常、错误码、统一响应)
├── config/         # 配置类
├── filter/         # 过滤器
└── utils/          # 工具类

前端 (Web/src/)
├── views/          # 页面组件
├── components/     # 通用组件
├── layouts/        # 布局组件
├── stores/         # Pinia状态管理
├── services/       # API服务
├── utils/          # 工具函数
├── composables/    # 组合式函数
└── assets/         # 静态资源
```

### 🔐 安全开发要点
1. **JWT认证**: 已配置JWT过滤器，注意token过期处理
2. **密码加密**: 使用Spring Security的BCrypt
3. **权限控制**: 基于Spring Security的方法级权限
4. **SQL注入防护**: MyBatis-Plus自动防护
5. **CORS配置**: 需要配置跨域访问策略

### 📊 数据库操作建议
1. **MyBatis-Plus配置**:
   - 已启用驼峰命名转换
   - 配置了逻辑删除 (deleted_at)
   - 主键自增策略
2. **分页查询**: 使用MyBatis-Plus的Page插件
3. **审计字段**: created_at, updated_at, created_by, updated_by

### 🎨 前端开发规范
1. **组件设计**: 使用组合式API (Composition API)
2. **状态管理**: Pinia store按模块划分 (user, post等)
3. **样式管理**: 统一使用 `styles.css` 全局样式
4. **错误处理**: 已配置全局错误处理机制
5. **类型安全**: 充分利用TypeScript类型检查

### 🔄 API设计原则
1. **统一响应格式**: 使用Result类包装所有响应
2. **错误码规范**: 已定义完整的ErrorCode枚举
3. **RESTful风格**: 遵循REST API设计规范
4. **版本控制**: 预留API版本控制机制

### 🚀 部署与运维
1. **环境配置**: 
   - 开发: localhost:8080 (后端) + localhost:3000 (前端)
   - 生产: 需配置反向代理和静态资源服务
2. **构建命令**:
   - 后端: `mvn spring-boot:run` 或 `mvn clean package`
   - 前端: `npm run dev` (开发) / `npm run build` (生产)
3. **监控日志**: 已配置Logback，建议添加业务日志

### 💡 扩展功能建议
1. **缓存策略**: 考虑引入Redis缓存热点数据
2. **文件上传**: 集成OSS或本地文件存储
3. **邮件服务**: 配置SMTP发送验证码和通知
4. **搜索功能**: 可集成Elasticsearch提升搜索体验
5. **监控告警**: 集成Spring Boot Actuator健康检查

---

## 🔧 关键配置文件
- **后端配置**: `application.yml` (数据库、MyBatis-Plus、服务器)
- **前端配置**: `vite.config.ts`, `package.json`
- **依赖管理**: 父级 `pom.xml` 统一版本管理
- **数据库**: 参考 `数据库设计文档.md` 和 `sql.sql`

## 📚 开发参考文档
- **API文档**: <mcfile name="API文档.md" path="e:\MyBlog\LiuTech\API文档.md"></mcfile>
- **数据库设计**: <mcfile name="数据库设计文档.md" path="e:\MyBlog\数据库设计文档.md"></mcfile>
- **部署指南**: <mcfile name="部署文档.md" path="e:\MyBlog\部署文档.md"></mcfile>
- **需求分析**: <mcfile name="需求分析.md" path="e:\MyBlog\需求分析.md"></mcfile>

这个技术栈总结将作为后续开发的重要参考，确保项目的一致性和可维护性。
        