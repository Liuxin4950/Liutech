# LiuTech 博客系统 API 接口文档

## 📋 文档说明

本文档描述了 LiuTech 博客系统的所有 REST API 接口，采用统一的响应格式和错误处理机制。

### 基础信息
- **Base URL**: `http://localhost:8080`
- **Content-Type**: `application/json`
- **字符编码**: `UTF-8`

### 统一响应格式

所有接口均采用以下统一响应格式：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

**响应字段说明：**
- `code`: 状态码，200表示成功，其他表示失败
- `message`: 响应消息
- `data`: 响应数据（可为null）

### 认证机制

系统采用 JWT (JSON Web Token) 进行身份认证：
- 登录成功后获取 token
- 后续请求需在 Header 中携带：`Authorization: Bearer {token}`

---

## 🔐 用户认证模块

### 1. 用户注册

**接口地址：** `POST /user/register`

**接口描述：** 创建新用户账户，包括用户名唯一性检查、邮箱唯一性检查、密码加密等

**请求参数：**

```json
{
  "username": "testuser",
  "email": "test@example.com",
  "password": "password123",
  "avatar": "http://example.com/avatar.jpg",
  "nickname": "测试用户"
}
```

**参数说明：**

| 参数名 | 类型 | 必填 | 说明 | 验证规则 |
|--------|------|------|------|----------|
| username | String | 是 | 用户名 | 3-20位字符 |
| email | String | 是 | 邮箱地址 | 有效邮箱格式 |
| password | String | 是 | 密码 | 至少8位且包含字母和数字 |
| avatar | String | 否 | 头像URL | - |
| nickname | String | 否 | 昵称 | - |

**成功响应：**

```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "username": "testuser",
    "email": "test@example.com",
    "avatarUrl": "http://example.com/avatar.jpg",
    "points": 0,
    "lastLoginAt": null
  }
}
```

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 1002 | 用户名已存在 | 用户名重复 |
| 1003 | 邮箱已被注册 | 邮箱重复 |
| 400 | 请求参数错误 | 参数验证失败 |

---

### 2. 用户登录

**接口地址：** `POST /user/login`

**接口描述：** 用户登录验证，返回JWT访问令牌

**请求参数：**

```json
{
  "username": "testuser",
  "password": "password123"
}
```

**参数说明：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| username | String | 是 | 用户名 |
| password | String | 是 | 密码 |

**成功响应：**

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 1004 | 用户名或密码错误 | 登录凭据无效 |
| 1001 | 用户不存在 | 用户名不存在 |
| 1005 | 账户已被禁用，请联系管理员 | 账户被禁用 |

---

## 👤 用户信息模块

### 3. 获取当前用户信息

**接口地址：** `GET /user/current`

**接口描述：** 获取当前登录用户的详细信息

**请求头：**
```
Authorization: Bearer {token}
```

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "username": "testuser",
    "email": "test@example.com",
    "avatarUrl": "http://example.com/avatar.jpg",
    "points": 100,
    "lastLoginAt": "2024-01-15T10:30:00"
  }
}
```

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 401 | 未授权访问，请先登录 | Token无效或过期 |
| 1001 | 用户不存在 | 用户已被删除 |

---

### 4. 修改密码

**接口地址：** `PUT /user/password`

**接口描述：** 用户修改登录密码

**请求头：**
```
Authorization: Bearer {token}
```

**请求参数：**

```json
{
  "oldPassword": "oldpassword123",
  "newPassword": "newpassword123",
  "confirmPassword": "newpassword123"
}
```

**参数说明：**

| 参数名 | 类型 | 必填 | 说明 | 验证规则 |
|--------|------|------|------|----------|
| oldPassword | String | 是 | 原密码 | - |
| newPassword | String | 是 | 新密码 | 6-20位字符 |
| confirmPassword | String | 是 | 确认密码 | 必须与新密码一致 |

**成功响应：**

```json
{
  "code": 200,
  "message": "密码修改成功",
  "data": null
}
```

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 401 | 未授权访问，请先登录 | Token无效 |
| 1004 | 用户名或密码错误 | 原密码错误 |
| 400 | 请求参数错误 | 新密码格式错误或确认密码不一致 |

---

## 🛠️ 用户管理模块（管理员）

### 5. 获取用户列表

**接口地址：** `GET /user/`

**接口描述：** 管理员获取所有用户列表

**请求头：**
```
Authorization: Bearer {admin_token}
```

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "username": "user1",
      "email": "user1@example.com",
      "avatarUrl": "http://example.com/avatar1.jpg",
      "points": 100,
      "status": 1,
      "lastLoginAt": "2024-01-15T10:30:00",
      "createdAt": "2024-01-01T00:00:00"
    }
  ]
}
```

---

### 6. 根据ID获取用户

**接口地址：** `GET /user/{id}`

**接口描述：** 管理员根据用户ID获取用户详细信息

**请求头：**
```
Authorization: Bearer {admin_token}
```

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 用户ID |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "username": "testuser",
    "email": "test@example.com",
    "avatarUrl": "http://example.com/avatar.jpg",
    "points": 100,
    "status": 1,
    "lastLoginAt": "2024-01-15T10:30:00",
    "createdAt": "2024-01-01T00:00:00",
    "updatedAt": "2024-01-15T10:30:00"
  }
}
```

---

### 7. 创建用户（管理员）

**接口地址：** `POST /user/`

**接口描述：** 管理员创建新用户

**请求头：**
```
Authorization: Bearer {admin_token}
```

**请求参数：**

```json
{
  "username": "newuser",
  "email": "newuser@example.com",
  "passwordHash": "encrypted_password",
  "avatarUrl": "http://example.com/avatar.jpg",
  "status": 1
}
```

---

### 8. 更新用户信息

**接口地址：** `PUT /user/{id}`

**接口描述：** 管理员更新用户信息

**请求头：**
```
Authorization: Bearer {admin_token}
```

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 用户ID |

**请求参数：**

```json
{
  "username": "updateduser",
  "email": "updated@example.com",
  "avatarUrl": "http://example.com/new_avatar.jpg",
  "status": 1,
  "points": 200
}
```

---

### 9. 删除用户

**接口地址：** `DELETE /user/{id}`

**接口描述：** 管理员删除用户（软删除）

**请求头：**
```
Authorization: Bearer {admin_token}
```

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 用户ID |

**成功响应：**

```json
{
  "code": 200,
  "message": "用户删除成功",
  "data": null
}
```

---

## 🏠 系统模块

### 10. 系统首页

**接口地址：** `GET /`

**接口描述：** 获取系统首页信息

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "欢迎来到LiuTech博客系统！"
}
```

---

## 📊 错误码参考

### 成功状态码
| 状态码 | 说明 |
|--------|------|
| 200 | 操作成功 |

### 客户端错误 (4xx)
| 状态码 | 错误信息 | 说明 |
|--------|----------|------|
| 400 | 请求参数错误 | 参数验证失败 |
| 401 | 未授权访问，请先登录 | 用户未登录或token无效 |
| 403 | 权限不足，禁止访问 | 用户权限不足 |
| 404 | 请求的资源不存在 | 资源未找到 |

### 用户相关业务错误 (1000-1099)
| 状态码 | 错误信息 | 说明 |
|--------|----------|------|
| 1001 | 用户不存在 | 根据用户名或ID查询用户失败 |
| 1002 | 用户名已存在 | 注册时用户名重复 |
| 1003 | 邮箱已被注册 | 注册时邮箱重复 |
| 1004 | 用户名或密码错误 | 登录凭据无效 |
| 1005 | 账户已被禁用，请联系管理员 | 账户被禁用 |
| 1006 | 用户名不能为空 | 用户名为空 |
| 1007 | 密码格式不正确 | 密码格式验证失败 |

### 文章相关业务错误 (1100-1199)
| 状态码 | 错误信息 | 说明 |
|--------|----------|------|
| 1101 | 文章不存在 | 文章未找到 |
| 1102 | 文章标题已存在 | 文章标题重复 |

### 服务器错误 (5xx)
| 状态码 | 错误信息 | 说明 |
|--------|----------|------|
| 500 | 系统异常，请联系管理员 | 服务器内部错误 |
| 501 | 数据库操作异常 | 数据库错误 |
| 502 | 网络异常，请稍后重试 | 网络连接错误 |

---

## 🔧 开发指南

### 接口扩展规范

1. **模块化设计**：按功能模块组织接口，便于维护和扩展
2. **统一响应格式**：所有接口使用 `Result<T>` 统一响应格式
3. **错误码管理**：在 `ErrorCode` 枚举中统一管理错误码
4. **参数验证**：使用 Bean Validation 进行参数校验
5. **安全认证**：敏感操作需要 JWT 认证

### 新增模块步骤

1. 在对应包下创建 Controller 类
2. 定义请求/响应 DTO 类
3. 在 `ErrorCode` 中添加相关错误码
4. 更新本文档，添加新接口说明
5. 编写单元测试

### 版本管理

- **当前版本**：v1.0.0
- **更新日期**：2024-01-15
- **维护人员**：刘鑫

---

## 📝 更新日志

### v1.0.0 (2024-01-15)
- 初始版本发布
- 完成用户认证模块
- 完成用户信息管理模块
- 完成用户管理模块（管理员）
- 建立统一的错误处理机制
- 实现 JWT 认证机制

---

*本文档将随着系统功能的扩展持续更新，请关注最新版本。*