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

**认证要求：** 无需认证，公开接口

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

**认证要求：** 无需认证，公开接口

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

**认证要求：** 需要用户认证

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

**认证要求：** 需要用户认证

**请求头：**
```
Authorization: Bearer {token}
Content-Type: application/json
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

### 4.1 获取用户统计信息

**接口地址：** `GET /user/stats`

**接口描述：** 获取当前用户的统计信息，包括文章数量、评论数量、积分等

**认证要求：** 需要用户认证

**请求头：**
```
Authorization: Bearer {token}
```

**成功响应：**

```json
{
  "code": 200,
  "message": "获取统计信息成功",
  "data": {
    "username": "liuxin",
    "email": "liuxin@example.com",
    "avatarUrl": "https://example.com/avatar.jpg",
    "nickname": "刘鑫",
    "bio": "全栈开发工程师",
    "points": 1250.50,
    "lastLoginAt": "2024-01-15T10:30:00",
    "createdAt": "2024-01-01T00:00:00",
    "commentCount": 25,
    "postCount": 12,
    "draftCount": 3,
    "viewCount": 0,
    "lastCommentAt": "2024-01-14T15:20:00",
    "lastPostAt": "2024-01-13T09:45:00"
  }
}
```

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 401 | 未授权访问，请先登录 | Token无效或过期 |
| 1001 | 用户不存在 | 用户已被删除 |

---

### 5. 更新个人资料

**接口地址：** `PUT /user/profile`

**接口描述：** 用户更新个人资料信息

**认证要求：** 需要用户认证

**请求头：**
```
Authorization: Bearer {token}
Content-Type: application/json
```

**请求参数：**

```json
{
  "email": "newemail@example.com",
  "avatarUrl": "http://example.com/new_avatar.jpg",
  "nickname": "新昵称",
  "bio": "这是我的个人简介"
}
```

**参数说明：**

| 参数名 | 类型 | 必填 | 说明 | 验证规则 |
|--------|------|------|------|----------|
| email | String | 否 | 邮箱地址 | 有效邮箱格式 |
| avatarUrl | String | 否 | 头像URL | - |
| nickname | String | 否 | 昵称 | 最大50个字符 |
| bio | String | 否 | 个人简介 | 最大500个字符 |

**成功响应：**

```json
{
  "code": 200,
  "message": "个人资料更新成功",
  "data": {
    "username": "testuser",
    "email": "newemail@example.com",
    "avatarUrl": "http://example.com/new_avatar.jpg",
    "nickname": "新昵称",
    "bio": "这是我的个人简介",
    "points": 100,
    "lastLoginAt": "2024-01-15T10:30:00"
  }
}
```

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 401 | 未授权访问，请先登录 | Token无效或过期 |
| 1003 | 邮箱已被注册 | 邮箱已被其他用户使用 |
| 400 | 请求参数错误 | 参数验证失败 |

---

## ✅ 签到模块

### 5.1 每日签到

**接口地址：** `POST /user/checkin`

**接口描述：** 用户每日签到，获得积分奖励

**认证要求：** 需要用户认证

**请求头：**
```
Authorization: Bearer {token}
```

**成功响应：**

```json
{
  "code": 200,
  "message": "签到成功",
  "data": {
    "points": 10,
    "continuousDays": 5,
    "totalPoints": 150,
    "todaySigned": true
  }
}
```

**响应字段说明：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| points | Integer | 本次签到获得的积分 |
| continuousDays | Integer | 连续签到天数 |
| totalPoints | Integer | 用户总积分 |
| todaySigned | Boolean | 今日是否已签到 |

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 401 | 未授权访问，请先登录 | Token无效或过期 |
| 1301 | 您已经签到过了 | 今日已签到 |

---

### 5.2 获取签到状态

**接口地址：** `GET /user/checkin/status`

**接口描述：** 获取用户当前签到状态信息

**认证要求：** 需要用户认证

**请求头：**
```
Authorization: Bearer {token}
```

**成功响应：**

```json
{
  "code": 200,
  "message": "获取签到状态成功",
  "data": {
    "todaySigned": true,
    "continuousDays": 5,
    "totalPoints": 150,
    "lastSignDate": "2025-01-30"
  }
}
```

**响应字段说明：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| todaySigned | Boolean | 今日是否已签到 |
| continuousDays | Integer | 连续签到天数 |
| totalPoints | Integer | 用户总积分 |
| lastSignDate | String | 最后签到日期（yyyy-MM-dd格式） |

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 401 | 未授权访问，请先登录 | Token无效或过期 |

---

### 6. 获取用户资料

**接口地址：** `GET /user/profile`

**接口描述：** 获取指定用户的公开资料信息

**认证要求：** 无需认证，公开接口

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| username | String | 是 | 用户名 |

**成功响应：**

```json
{
  "code": 200,
  "message": "获取用户资料成功",
  "data": {
    "username": "testuser",
    "nickname": "测试用户",
    "avatarUrl": "http://example.com/avatar.jpg",
    "bio": "这是我的个人简介",
    "postCount": 12,
    "commentCount": 25,
    "points": 150,
    "createdAt": "2024-01-01T00:00:00",
    "lastLoginAt": "2024-01-15T10:30:00"
  }
}
```

**响应字段说明：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| username | String | 用户名 |
| nickname | String | 昵称 |
| avatarUrl | String | 头像URL |
| bio | String | 个人简介 |
| postCount | Integer | 发布的文章数量 |
| commentCount | Integer | 发表的评论数量 |
| points | Integer | 用户积分 |
| createdAt | String | 注册时间 |
| lastLoginAt | String | 最后登录时间 |

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 1001 | 用户不存在 | 用户名不存在 |

---

### 6.1 获取作者资料

**接口地址：** `GET /user/author/profile`

**接口描述：** 获取作者详细信息，用于展示作者页面

**认证要求：** 无需认证，公开接口

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| username | String | 是 | 作者用户名 |

**成功响应：**

```json
{
  "code": 200,
  "message": "获取作者资料成功",
  "data": {
    "username": "author",
    "nickname": "作者昵称",
    "avatarUrl": "http://example.com/avatar.jpg",
    "bio": "我是技术博客作者，专注于Java和Vue开发",
    "postCount": 30,
    "totalViews": 5000,
    "totalLikes": 200,
    "followersCount": 150,
    "createdAt": "2023-06-01T00:00:00",
    "socialLinks": {
      "github": "https://github.com/author",
      "wechat": "author_wx"
    }
  }
}
```

**响应字段说明：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| username | String | 用户名 |
| nickname | String | 昵称 |
| avatarUrl | String | 头像URL |
| bio | String | 个人简介 |
| postCount | Integer | 发布的文章数量 |
| totalViews | Integer | 文章总浏览量 |
| totalLikes | Integer | 获得的总点赞数 |
| followersCount | Integer | 粉丝数量 |
| createdAt | String | 注册时间 |
| socialLinks | Object | 社交媒体链接（可选） |

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 1001 | 用户不存在 | 作者用户名不存在 |

---

## 🛠️ 用户管理模块（管理员）

### 7. 获取用户列表

**接口地址：** `GET /user/`

**接口描述：** 管理员获取所有用户列表

**认证要求：** 需要管理员认证

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

### 8. 根据ID获取用户

**接口地址：** `GET /user/{id}`

**接口描述：** 管理员根据用户ID获取用户详细信息

**认证要求：** 需要管理员认证

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

### 9. 创建用户（管理员）

**接口地址：** `POST /user/`

**接口描述：** 管理员创建新用户

**认证要求：** 需要管理员认证

**请求头：**
```
Authorization: Bearer {admin_token}
Content-Type: application/json
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

### 10. 更新用户信息

**接口地址：** `PUT /user/{id}`

**接口描述：** 管理员更新用户信息

**认证要求：** 需要管理员认证

**请求头：**
```
Authorization: Bearer {admin_token}
Content-Type: application/json
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

### 11. 删除用户

**接口地址：** `DELETE /user/{id}`

**接口描述：** 管理员删除用户（软删除）

**认证要求：** 需要管理员认证

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

## 📂 分类管理模块

### 3.1 获取所有分类

**请求地址：** `GET /categories`

**描述：** 获取所有分类列表（包含文章数量）

**认证要求：** 无需认证，公开接口

**成功响应：**

```json
{
  "code": 200,
  "message": "查询成功",
  "data": [
    {
      "id": 1,
      "name": "技术分享",
      "description": "技术相关文章",
      "postCount": 15
    },
    {
      "id": 2,
      "name": "生活随笔",
      "description": "生活感悟文章",
      "postCount": 8
    }
  ]
}
```

### 3.2 根据ID获取分类详情

**请求地址：** `GET /categories/{id}`

**描述：** 根据分类ID获取分类详细信息

**认证要求：** 无需认证，公开接口

**路径参数：**

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| id | Long | 是 | 分类ID |

**成功响应：**

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "id": 1,
    "name": "技术分享",
    "description": "技术相关文章"
  }
}
```

**错误响应：**

```json
{
  "code": 404,
  "message": "分类不存在",
  "data": null
}
```

---

## 🏷️ 标签管理模块

### 4.1 获取所有标签

**请求地址：** `GET /tags`

**描述：** 获取所有标签列表（包含文章数量）

**认证要求：** 无需认证，公开接口

**成功响应：**

```json
{
  "code": 200,
  "message": "查询成功",
  "data": [
    {
      "id": 1,
      "name": "Java",
      "postCount": 12
    },
    {
      "id": 2,
      "name": "Spring Boot",
      "postCount": 8
    }
  ]
}
```

### 4.2 根据ID获取标签详情

**请求地址：** `GET /tags/{id}`

**描述：** 根据标签ID获取标签详细信息

**认证要求：** 无需认证，公开接口

**路径参数：**

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| id | Long | 是 | 标签ID |

**成功响应：**

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "id": 1,
    "name": "Java"
  }
}
```

**错误响应：**

```json
{
  "code": 404,
  "message": "标签不存在",
  "data": null
}
```

### 4.3 获取热门标签

**请求地址：** `GET /tags/hot`

**描述：** 获取热门标签列表（按文章数量排序）

**认证要求：** 无需认证，公开接口

**请求参数：**

| 参数名 | 类型 | 必填 | 描述 | 默认值 |
|--------|------|------|------|--------|
| limit | Integer | 否 | 限制数量 | 20 |

**成功响应：**

```json
{
  "code": 200,
  "message": "查询成功",
  "data": [
    {
      "id": 1,
      "name": "Java",
      "postCount": 12
    },
    {
      "id": 2,
      "name": "Spring Boot",
      "postCount": 8
    }
  ]
}
```

### 4.4 根据文章ID查询标签列表

**请求地址：** `GET /tags/post/{postId}`

**描述：** 根据文章ID获取该文章关联的所有标签

**认证要求：** 无需认证，公开接口

**路径参数：**

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| postId | Long | 是 | 文章ID |

**成功响应：**

```json
{
  "code": 200,
  "message": "查询成功",
  "data": [
    {
      "id": 1,
      "name": "Spring Boot",
      "description": "Spring Boot框架相关",
      "postCount": 15
    },
    {
      "id": 3,
      "name": "Java",
      "description": "Java编程语言",
      "postCount": 25
    }
  ]
}
```

**错误响应：**

```json
{
  "code": 1101,
  "message": "文章不存在",
  "data": null
}
```

### 4.5 搜索标签

**接口地址：** `GET /tags/search`

**接口描述：** 根据名称搜索标签

**认证要求：** 无需认证，公开接口

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 | 默认值 |
|--------|------|------|------|--------|
| name | String | 是 | 标签名称关键词 | - |
| limit | Integer | 否 | 返回数量 | 20 |

**成功响应：**

```json
{
  "code": 200,
  "message": "搜索成功",
  "data": [
    {
      "id": 1,
      "name": "Spring Boot",
      "description": "Spring Boot框架相关",
      "postCount": 15
    },
    {
      "id": 5,
      "name": "Spring Cloud",
      "description": "Spring Cloud微服务框架",
      "postCount": 8
    }
  ]
}
```

**响应字段说明：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | Long | 标签ID |
| name | String | 标签名称 |
| description | String | 标签描述（可选） |
| postCount | Integer | 关联的文章数量 |

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 400 | 搜索关键词不能为空 | 标签名称参数为空 |
| 400 | 搜索关键词长度不能超过50个字符 | 标签名称长度限制 |

---

## 📝 文章管理模块

### 10. 获取文章列表

**接口地址：** `GET /posts`

**接口描述：** 分页获取文章列表，支持按分类、标签、关键词筛选和排序

**认证要求：** 无需认证，公开接口

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 | 默认值 |
|--------|------|------|------|--------|
| page | Integer | 否 | 页码 | 1 |
| size | Integer | 否 | 每页大小 | 10 |
| categoryId | Long | 否 | 分类ID | - |
| tagId | Long | 否 | 标签ID | - |
| keyword | String | 否 | 搜索关键词 | - |
| sortBy | String | 否 | 排序方式：latest/popular | latest |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "id": 1,
        "title": "文章标题",
        "summary": "文章摘要",
        "category": {
          "id": 1,
          "name": "技术分享"
        },
        "author": {
          "id": 1,
          "username": "author",
          "avatarUrl": "http://example.com/avatar.jpg"
        },
        "tags": [
          {
            "id": 1,
            "name": "Java"
          }
        ],
        "commentCount": 5,
        "createdAt": "2024-01-15T10:30:00",
        "updatedAt": "2024-01-15T10:30:00"
      }
    ],
    "total": 100,
    "size": 10,
    "current": 1,
    "pages": 10
  }
}
```

---

### 11. 获取文章详情

**接口地址：** `GET /posts/{id}`

**接口描述：** 根据文章ID获取文章详细信息

**认证要求：** 无需认证，公开接口

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 文章ID |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "title": "文章标题",
    "content": "文章内容（Markdown格式）",
    "summary": "文章摘要",
    "category": {
      "id": 1,
      "name": "技术分享"
    },
    "author": {
      "id": 1,
      "username": "author",
      "avatarUrl": "http://example.com/avatar.jpg"
    },
    "tags": [
      {
        "id": 1,
        "name": "Java"
      }
    ],
    "commentCount": 5,
    "createdAt": "2024-01-15T10:30:00",
    "updatedAt": "2024-01-15T10:30:00"
  }
}
```

---

### 12. 创建文章

**接口地址：** `POST /posts`

**接口描述：** 创建新文章

**认证要求：** 需要用户认证

**请求头：**
```
Authorization: Bearer {token}
Content-Type: application/json
```

**请求参数：**

```json
{
  "title": "文章标题",
  "content": "文章内容（Markdown格式）",
  "summary": "文章摘要",
  "categoryId": 1,
  "tagIds": [1, 2, 3],
  "status": "published"
}
```

**参数说明：**

| 参数名 | 类型 | 必填 | 说明 | 验证规则 |
|--------|------|------|------|----------|
| title | String | 是 | 文章标题 | 1-255字符 |
| content | String | 是 | 文章内容 | 不能为空 |
| summary | String | 否 | 文章摘要 | 最多500字符 |
| categoryId | Long | 是 | 分类ID | 必须存在 |
| tagIds | List<Long> | 否 | 标签ID列表 | - |
| status | String | 否 | 文章状态 | draft/published/archived，默认draft |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": 7
}
```

**响应说明：**
- `data` 字段直接返回创建成功的文章ID（Long类型）

---

### 13. 更新文章

**接口地址：** `PUT /posts/{id}`

**接口描述：** 更新文章信息

**认证要求：** 需要用户认证（仅作者或管理员可操作）

**请求头：**
```
Authorization: Bearer {token}
Content-Type: application/json
```

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 文章ID |

**请求参数：**

```json
{
  "id": 8,
  "title": "更新后的文章标题",
  "content": "更新后的文章内容",
  "summary": "更新后的文章摘要",
  "categoryId": 2,
  "tagIds": [2, 3, 4],
  "status": "published"
}
```

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": true
}
```

**响应说明：**
- `data` 字段返回布尔值，true表示更新成功

---

### 14. 删除文章

**接口地址：** `DELETE /posts/{id}`

**接口描述：** 删除文章（软删除）

**认证要求：** 需要用户认证（仅作者或管理员可操作）

**请求头：**
```
Authorization: Bearer {token}
```

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 文章ID |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": true
}
```

**响应说明：**
- `data` 字段返回布尔值，true表示删除成功

---

### 15. 发布文章

**接口地址：** `PUT /posts/{id}/publish`

**接口描述：** 发布文章（将状态改为已发布）

**认证要求：** 需要用户认证（仅作者或管理员可操作）

**请求头：**
```
Authorization: Bearer {token}
```

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 文章ID |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": true
}
```

**响应说明：**
- `data` 字段返回布尔值，true表示发布成功

---

### 16. 取消发布文章

**接口地址：** `PUT /posts/{id}/unpublish`

**接口描述：** 取消发布文章（将状态改为草稿）

**认证要求：** 需要用户认证（仅作者或管理员可操作）

**请求头：**
```
Authorization: Bearer {token}
```

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 文章ID |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": true
}
```

**响应说明：**
- `data` 字段返回布尔值，true表示取消发布成功

---

### 17. 获取热门文章

**接口地址：** `GET /posts/hot`

**接口描述：** 获取热门文章列表

**认证要求：** 无需认证，公开接口

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 | 默认值 |
|--------|------|------|------|--------|
| limit | Integer | 否 | 返回数量 | 10 |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "title": "热门文章标题",
      "summary": "文章摘要",
      "category": {
        "id": 1,
        "name": "技术分享"
      },
      "author": {
        "id": 1,
        "username": "author",
        "avatarUrl": "http://example.com/avatar.jpg"
      },
      "commentCount": 25,
      "createdAt": "2024-01-15T10:30:00"
    }
  ]
}
```

---

### 18. 获取最新文章

**接口地址：** `GET /posts/latest`

**接口描述：** 获取最新文章列表

**认证要求：** 无需认证，公开接口

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 | 默认值 |
|--------|------|------|------|--------|
| limit | Integer | 否 | 返回数量 | 10 |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "title": "最新文章标题",
      "summary": "文章摘要",
      "category": {
        "id": 1,
        "name": "技术分享"
      },
      "author": {
        "id": 1,
        "username": "author",
        "avatarUrl": "http://example.com/avatar.jpg"
      },
      "commentCount": 5,
      "createdAt": "2024-01-15T10:30:00"
    }
  ]
}
```

---

### 18.1 搜索文章

**接口地址：** `GET /posts/search`

**接口描述：** 根据关键词搜索文章，支持标题、内容、摘要的全文搜索

**认证要求：** 无需认证，公开接口

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 | 默认值 |
|--------|------|------|------|--------|
| keyword | String | 是 | 搜索关键词 | - |
| page | Integer | 否 | 页码 | 1 |
| size | Integer | 否 | 每页大小 | 10 |
| categoryId | Long | 否 | 分类ID筛选 | - |
| tagId | Long | 否 | 标签ID筛选 | - |
| sortBy | String | 否 | 排序方式：relevance/latest/popular | relevance |

**成功响应：**

```json
{
  "code": 200,
  "message": "搜索成功",
  "data": {
    "records": [
      {
        "id": 1,
        "title": "Spring Boot 入门教程",
        "summary": "详细介绍Spring Boot的基础知识...",
        "category": {
          "id": 1,
          "name": "后端开发"
        },
        "author": {
          "id": 1,
          "username": "liuxin",
          "avatarUrl": "https://example.com/avatar.jpg"
        },
        "tags": [
          {
            "id": 1,
            "name": "Spring Boot"
          }
        ],
        "commentCount": 5,
        "createdAt": "2024-01-15T10:30:00",
        "updatedAt": "2024-01-15T10:30:00"
      }
    ],
    "total": 25,
    "size": 10,
    "current": 1,
    "pages": 3,
    "keyword": "Spring Boot"
  }
}
```

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 400 | 搜索关键词不能为空 | 关键词参数验证失败 |
| 400 | 搜索关键词长度不能超过100个字符 | 关键词长度限制 |

### 19. 点赞/取消点赞文章

**接口地址：** `POST /posts/{id}/like`

**接口描述：** 切换文章的点赞状态（点赞或取消点赞）

**认证要求：** 需要用户认证

**请求头：**
```
Authorization: Bearer {token}
```

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 文章ID |

**成功响应（点赞）：**

```json
{
  "code": 200,
  "message": "点赞成功",
  "data": "liked"
}
```

**成功响应（取消点赞）：**

```json
{
  "code": 200,
  "message": "取消点赞成功",
  "data": "unliked"
}
```

**响应字段说明：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| data | String | 操作结果：liked（已点赞）、unliked（已取消点赞） |

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 401 | 未授权访问，请先登录 | Token无效或过期 |
| 1101 | 文章不存在 | 文章ID不存在 |
| 1301 | 您已经点赞过了 | 重复点赞（实际接口会自动切换状态） |

---

### 19.1 收藏/取消收藏文章

**接口地址：** `POST /posts/{id}/favorite`

**接口描述：** 切换文章的收藏状态（收藏或取消收藏）

**认证要求：** 需要用户认证

**请求头：**
```
Authorization: Bearer {token}
```

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 文章ID |

**成功响应（收藏）：**

```json
{
  "code": 200,
  "message": "收藏成功",
  "data": "favorited"
}
```

**成功响应（取消收藏）：**

```json
{
  "code": 200,
  "message": "取消收藏成功",
  "data": "unfavorited"
}
```

**响应字段说明：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| data | String | 操作结果：favorited（已收藏）、unfavorited（已取消收藏） |

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 401 | 未授权访问，请先登录 | Token无效或过期 |
| 1101 | 文章不存在 | 文章ID不存在 |
| 1302 | 您已经收藏过了 | 重复收藏（实际接口会自动切换状态） |

---

### 20. 获取用户收藏列表

**接口地址：** `GET /posts/favorites`

**接口描述：** 获取当前用户的收藏文章列表

**认证要求：** 需要用户认证

**请求头：**
```
Authorization: Bearer {token}
```

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 | 默认值 |
|--------|------|------|------|--------|
| page | Integer | 否 | 页码 | 1 |
| size | Integer | 否 | 每页大小 | 10 |

**成功响应：**

```json
{
  "code": 200,
  "message": "获取收藏列表成功",
  "data": {
    "records": [
      {
        "id": 1,
        "title": "收藏的文章标题",
        "summary": "文章摘要",
        "category": {
          "id": 1,
          "name": "技术分享"
        },
        "author": {
          "id": 1,
          "username": "author",
          "avatarUrl": "http://example.com/avatar.jpg"
        },
        "tags": [
          {
            "id": 1,
            "name": "Java"
          }
        ],
        "commentCount": 5,
        "favoritedAt": "2025-01-15T10:30:00",
        "createdAt": "2025-01-10T09:00:00"
      }
    ],
    "total": 25,
    "size": 10,
    "current": 1,
    "pages": 3
  }
}
```

**响应字段说明：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| records | Array | 收藏文章列表 |
| favoritedAt | String | 收藏时间 |
| total | Integer | 总收藏数 |
| size | Integer | 每页大小 |
| current | Integer | 当前页码 |
| pages | Integer | 总页数 |

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 401 | 未授权访问，请先登录 | Token无效或过期 |

---

### 21. 获取草稿箱列表

**接口地址：** `GET /posts/drafts`

**接口描述：** 分页获取当前用户的草稿文章列表，支持关键词搜索

**认证要求：** 需要用户认证

**请求头：**
```
Authorization: Bearer {token}
```

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 | 默认值 |
|--------|------|------|------|--------|
| page | Integer | 否 | 页码 | 1 |
| size | Integer | 否 | 每页大小 | 10 |
| keyword | String | 否 | 搜索关键词（标题、内容、摘要） | - |
| sortBy | String | 否 | 排序方式：latest/oldest | latest |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "id": 1,
        "title": "草稿文章标题",
        "summary": "草稿文章摘要",
        "category": {
          "id": 1,
          "name": "技术分享"
        },
        "author": {
          "id": 1,
          "username": "author",
          "avatarUrl": "http://example.com/avatar.jpg"
        },
        "tags": [
          {
            "id": 1,
            "name": "Java"
          }
        ],
        "commentCount": 0,
        "createdAt": "2024-01-15T10:30:00",
        "updatedAt": "2024-01-16T14:20:00"
      }
    ],
    "total": 5,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 401 | 未授权访问，请先登录 | Token无效或过期 |
| 403 | 权限不足 | 只能查看自己的草稿 |

**接口说明：**
- 只返回当前登录用户创建的草稿文章（status = 'draft'）
- 支持按标题、内容、摘要进行关键词搜索
- 默认按更新时间倒序排列（最新修改的在前）
- 返回的文章包含完整的分类、作者、标签信息

---

## 💬 评论模块

### 20. 分页查询文章评论

**接口地址：** `GET /comments/post/{postId}`

**接口描述：** 分页查询指定文章的评论列表

**认证要求：** 无需认证，公开接口

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| postId | Long | 是 | 文章ID |

**查询参数：**

| 参数名 | 类型 | 必填 | 说明 | 默认值 |
|--------|------|------|------|--------|
| page | Integer | 否 | 页码 | 1 |
| size | Integer | 否 | 每页大小 | 10 |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "id": 1,
        "postId": 1,
        "content": "这是一条评论内容",
        "parentId": null,
        "createdAt": "2024-01-15T10:30:00",
        "user": {
          "id": 1,
          "username": "testuser",
          "avatarUrl": "http://example.com/avatar.jpg"
        },
        "children": []
      }
    ],
    "total": 50,
    "size": 10,
    "current": 1,
    "pages": 5
  }
}
```

---

### 21. 获取文章树形评论结构

**接口地址：** `GET /comments/post/{postId}/tree`

**接口描述：** 获取指定文章的树形评论结构，包含顶级评论及其子评论

**认证要求：** 无需认证，公开接口

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| postId | Long | 是 | 文章ID |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "postId": 1,
      "content": "这是一条顶级评论",
      "parentId": null,
      "createdAt": "2024-01-15T10:30:00",
      "user": {
        "id": 1,
        "username": "testuser",
        "avatarUrl": "http://example.com/avatar.jpg"
      },
      "children": [
        {
          "id": 2,
          "postId": 1,
          "content": "这是一条回复评论",
          "parentId": 1,
          "createdAt": "2024-01-15T11:00:00",
          "user": {
            "id": 2,
            "username": "replier",
            "avatarUrl": "http://example.com/avatar2.jpg"
          },
          "children": []
        }
      ]
    }
  ]
}
```

---

### 22. 创建评论

**接口地址：** `POST /comments`

**接口描述：** 创建新评论或回复已有评论

**认证要求：** 需要用户认证

**请求头：**
```
Authorization: Bearer {token}
Content-Type: application/json
```

**请求参数：**

```json
{
  "postId": 1,
  "content": "这是一条新评论",
  "parentId": null
}
```

**参数说明：**

| 参数名 | 类型 | 必填 | 说明 | 验证规则 |
|--------|------|------|------|----------|
| postId | Long | 是 | 文章ID | 必须存在 |
| content | String | 是 | 评论内容 | 不能为空，最多1000字符 |
| parentId | Long | 否 | 父评论ID | 用于回复评论，可选 |

**成功响应：**

```json
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "id": 3,
    "postId": 1,
    "content": "这是一条新评论",
    "parentId": null,
    "createdAt": "2024-01-15T12:00:00",
    "user": {
      "id": 1,
      "username": "testuser",
      "avatarUrl": "http://example.com/avatar.jpg"
    },
    "children": []
  }
}
```

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 401 | 未授权访问，请先登录 | 用户未登录 |
| 400 | 评论内容不能为空 | 参数验证失败 |
| 400 | 父评论不存在 | 回复的评论不存在 |
| 400 | 父评论与当前文章不匹配 | 回复评论与文章不匹配 |

---

### 23. 统计文章评论数量

**接口地址：** `GET /comments/post/{postId}/count`

**接口描述：** 统计指定文章的评论总数

**认证要求：** 无需认证，公开接口

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| postId | Long | 是 | 文章ID |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": 25
}
```

**响应说明：**
- `data` 字段直接返回评论数量（Integer类型）

---

### 24. 查询子评论

**接口地址：** `GET /comments/{parentId}/children`

**接口描述：** 查询指定评论的所有子评论

**认证要求：** 无需认证，公开接口

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| parentId | Long | 是 | 父评论ID |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 2,
      "postId": 1,
      "content": "这是一条回复评论",
      "parentId": 1,
      "userId": 2,
      "createdAt": "2024-01-15T11:00:00",
      "updatedAt": "2024-01-15T11:00:00"
    }
  ]
}
```

---

### 25. 查询最新评论

**接口地址：** `GET /comments/latest`

**接口描述：** 查询系统最新评论列表

**认证要求：** 无需认证，公开接口

**查询参数：**

| 参数名 | 类型 | 必填 | 说明 | 默认值 |
|--------|------|------|------|--------|
| limit | Integer | 否 | 返回数量 | 10 |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 3,
      "postId": 1,
      "content": "最新评论内容",
      "parentId": null,
      "userId": 1,
      "createdAt": "2024-01-15T12:00:00",
      "updatedAt": "2024-01-15T12:00:00"
    }
  ]
}
```

---

### 26. 查询评论详情

**接口地址：** `GET /comments/{id}`

**接口描述：** 根据评论ID查询评论详细信息

**认证要求：** 无需认证，公开接口

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 评论ID |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "postId": 1,
    "content": "评论内容",
    "parentId": null,
    "userId": 1,
    "createdAt": "2024-01-15T10:30:00",
    "updatedAt": "2024-01-15T10:30:00"
  }
}
```

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 404 | 评论不存在 | 指定ID的评论不存在 |

---

## 📁 文件管理模块

### 文件上传配置

系统支持多种文件类型上传，具体限制如下：

| 文件类型 | 最大大小 | 支持格式 | 说明 |
|----------|----------|----------|------|
| 图片文件 | 10MB | jpg, jpeg, png, gif, webp | 用于文章配图、头像等 |
| 文档文件 | 100MB | pdf, doc, docx, xls, xlsx, ppt, pptx, txt | 用于文档分享下载 |
| 资源文件 | 100MB | 所有常见格式 | 可设置积分下载 |

### 30. 上传图片

**接口地址：** `POST /upload/image`

**接口描述：** 上传图片文件，支持jpg、jpeg、png、gif、webp格式

**认证要求：** 需要用户认证

**请求头：**
```
Authorization: Bearer {token}
Content-Type: multipart/form-data
```

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| file | File | 是 | 图片文件（最大10MB） |

**成功响应：**

```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "resourceId": 123,
    "fileName": "image_20250131_001.jpg",
    "fileUrl": "http://localhost:8080/uploads/images/2025/01/31/image_20250131_001.jpg",
    "fileSize": 1024000,
    "uploadTime": "2025-01-31T10:30:00"
  }
}
```

**响应字段说明：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| resourceId | Long | 资源ID |
| fileName | String | 存储的文件名 |
| fileUrl | String | 文件访问URL |
| fileSize | Long | 文件大小（字节） |
| uploadTime | String | 上传时间 |

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 401 | 未授权访问，请先登录 | Token无效或过期 |
| 400 | 文件不能为空 | 未选择文件 |
| 400 | 文件大小超出限制 | 超过10MB |
| 400 | 不支持的文件格式 | 不是允许的图片格式 |

---

### 31. 上传文档文件

**接口地址：** `POST /upload/document`

**接口描述：** 上传文档文件，支持pdf、doc、docx等格式

**认证要求：** 需要用户认证

**请求头：**
```
Authorization: Bearer {token}
Content-Type: multipart/form-data
```

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| file | File | 是 | 文档文件（最大100MB） |
| description | String | 否 | 文件描述 |

**成功响应：**

```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "resourceId": 124,
    "fileName": "document_20250131_001.pdf",
    "originalName": "技术文档.pdf",
    "fileUrl": "http://localhost:8080/uploads/documents/2025/01/31/document_20250131_001.pdf",
    "fileSize": 2048000,
    "description": "Java开发规范文档",
    "downloadType": 0,
    "pointsNeeded": 0,
    "uploadTime": "2025-01-31T10:30:00"
  }
}
```

**响应字段说明：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| resourceId | Long | 资源ID |
| fileName | String | 存储的文件名 |
| originalName | String | 原始文件名 |
| fileUrl | String | 文件访问URL |
| fileSize | Long | 文件大小（字节） |
| description | String | 文件描述 |
| downloadType | Integer | 下载类型（0免费，1积分） |
| pointsNeeded | Integer | 下载所需积分 |
| uploadTime | String | 上传时间 |

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 401 | 未授权访问，请先登录 | Token无效或过期 |
| 400 | 文件不能为空 | 未选择文件 |
| 400 | 文件大小超出限制 | 超过100MB |
| 400 | 不支持的文件格式 | 不是允许的文档格式 |

---

### 32. 上传资源文件

**接口地址：** `POST /upload/resource`

**接口描述：** 上传资源文件，可设置为积分下载

**认证要求：** 需要用户认证

**请求头：**
```
Authorization: Bearer {token}
Content-Type: multipart/form-data
```

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 | 默认值 |
|--------|------|------|------|--------|
| file | File | 是 | 资源文件（最大100MB） | - |
| description | String | 否 | 文件描述 | - |
| draftKey | String | 否 | 草稿关联键（用于关联文章草稿） | - |
| type | String | 否 | 附件类型 | - |
| downloadType | Integer | 否 | 下载类型（0免费，1积分） | 0 |
| pointsNeeded | Integer | 否 | 下载所需积分 | 0 |

**成功响应：**

```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "resourceId": 125,
    "fileName": "resource_20250131_001.zip",
    "originalName": "项目源码.zip",
    "fileUrl": "http://localhost:8080/uploads/resources/2025/01/31/resource_20250131_001.zip",
    "fileSize": 5120000,
    "description": "Spring Boot项目源码",
    "downloadType": 1,
    "pointsNeeded": 50,
    "draftKey": "draft_123",
    "type": "sourcecode",
    "uploadTime": "2025-01-31T10:30:00"
  }
}
```

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 401 | 未授权访问，请先登录 | Token无效或过期 |
| 400 | 文件不能为空 | 未选择文件 |
| 400 | 文件大小超出限制 | 超过100MB |
| 400 | 积分设置无效 | 积分下载时必须设置所需积分 |

---

### 33. TinyMCE编辑器图片上传

**接口地址：** `POST /upload/tinymce/image`

**接口描述：** 专用于TinyMCE富文本编辑器的图片上传接口

**认证要求：** 需要用户认证

**请求头：**
```
Authorization: Bearer {token}
Content-Type: multipart/form-data
```

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| file | File | 是 | 图片文件（最大10MB） |

**成功响应（TinyMCE专用格式）：**

```json
{
  "location": "http://localhost:8080/uploads/images/2025/01/31/tinymce_20250131_001.jpg"
}
```

**错误响应（TinyMCE专用格式）：**

```json
{
  "error": "文件大小超出限制"
}
```

**说明：** 此接口返回格式专为TinyMCE编辑器设计，直接返回图片URL或错误信息

---

### 34. 查询草稿附件列表

**接口地址：** `GET /upload/attachments/draft/{draftKey}`

**接口描述：** 查询指定草稿关联的所有附件

**认证要求：** 需要用户认证

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| draftKey | String | 是 | 草稿关联键 |

**成功响应：**

```json
{
  "code": 200,
  "message": "查询成功",
  "data": [
    {
      "resourceId": 125,
      "fileName": "image_001.jpg",
      "fileUrl": "http://localhost:8080/uploads/images/2025/01/31/image_001.jpg",
      "fileSize": 1024000,
      "type": "image",
      "uploadTime": "2025-01-31T10:30:00"
    }
  ]
}
```

---

### 35. 查询文章附件列表

**接口地址：** `GET /upload/attachments/post/{postId}`

**接口描述：** 查询指定文章关联的所有附件

**认证要求：** 需要用户认证

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| postId | Long | 是 | 文章ID |

**成功响应：** 格式同草稿附件列表

---

### 36. 删除附件

**接口地址：** `DELETE /upload/attachments/{resourceId}`

**接口描述：** 删除指定附件（仅限上传者）

**认证要求：** 需要用户认证

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| resourceId | Long | 是 | 资源ID |

**成功响应：**

```json
{
  "code": 200,
  "message": "附件删除成功",
  "data": null
}
```

---

### 37. 更新附件元信息

**接口地址：** `PUT /upload/attachments/{resourceId}/meta`

**接口描述：** 更新附件的下载类型和积分设置

**认证要求：** 需要用户认证（仅限上传者）

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| resourceId | Long | 是 | 资源ID |

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 | 默认值 |
|--------|------|------|------|--------|
| downloadType | Integer | 否 | 下载类型（0免费，1积分） | 0 |
| pointsNeeded | Integer | 否 | 下载所需积分 | 0 |

**成功响应：**

```json
{
  "code": 200,
  "message": "附件设置已更新",
  "data": null
}
```

---

## 💾 资源下载模块

### 38. 购买资源

**接口地址：** `POST /api/resource/purchase/{resourceId}`

**接口描述：** 购买付费资源（扣除用户积分）

**认证要求：** 需要用户认证

**请求头：**
```
Authorization: Bearer {token}
```

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| resourceId | Long | 是 | 资源ID |

**成功响应：**

```json
{
  "code": 200,
  "message": "购买成功",
  "data": "购买成功"
}
```

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 401 | 未授权访问，请先登录 | Token无效或过期 |
| 400 | 积分不足 | 用户积分不足以购买此资源 |
| 404 | 资源不存在 | 资源ID不存在 |
| 400 | 资源已购买 | 用户已经购买过此资源 |

---

### 39. 下载资源

**接口地址：** `GET /api/resource/download/{resourceId}`

**接口描述：** 下载资源文件（需要已购买或免费资源）

**认证要求：** 需要用户认证

**请求头：**
```
Authorization: Bearer {token}
```

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| resourceId | Long | 是 | 资源ID |

**成功响应：**
- 返回文件流，可直接下载
- 响应头包含文件名和Content-Type

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 401 | 未授权访问，请先登录 | Token无效或过期 |
| 404 | 资源不存在 | 资源ID不存在 |
| 400 | 请先购买资源 | 付费资源未购买 |
| 400 | 积分不足需要购买 | 付费资源积分不足 |

---

### 40. 检查购买状态

**接口地址：** `GET /api/resource/check/{resourceId}`

**接口描述：** 检查用户是否已购买指定资源

**认证要求：** 需要用户认证

**请求头：**
```
Authorization: Bearer {token}
```

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| resourceId | Long | 是 | 资源ID |

**成功响应（已购买）：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": true
}
```

**成功响应（未购买）：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": false
}
```

**响应字段说明：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| data | Boolean | true表示已购买，false表示未购买 |

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 401 | 未授权访问，请先登录 | Token无效或过期 |
| 404 | 资源不存在 | 资源ID不存在 |

---

## 📢 公告管理模块

### 41. 获取有效公告列表

**接口地址：** `GET /announcements/list`

**接口描述：** 分页获取所有有效的公告（前台用户）

**认证要求：** 无需认证，公开接口

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 | 默认值 |
|--------|------|------|------|--------|
| current | Long | 否 | 当前页码 | 1 |
| size | Long | 否 | 每页大小 | 10 |

**成功响应：**

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "records": [
      {
        "id": 1,
        "title": "系统维护通知",
        "content": "系统将于今晚22:00进行维护...",
        "type": 1,
        "isTop": 1,
        "viewCount": 1250,
        "createdAt": "2025-01-30T10:00:00",
        "updatedAt": "2025-01-30T10:00:00"
      }
    ],
    "total": 10,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

**响应字段说明：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | Long | 公告ID |
| title | String | 公告标题 |
| content | String | 公告内容 |
| type | Integer | 公告类型（1系统通知，2活动公告等） |
| isTop | Integer | 是否置顶（0否，1是） |
| viewCount | Integer | 浏览次数 |
| createdAt | String | 创建时间 |
| updatedAt | String | 更新时间 |

---

### 42. 获取置顶公告

**接口地址：** `GET /announcements/top`

**接口描述：** 获取所有置顶公告列表

**认证要求：** 无需认证，公开接口

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 | 默认值 |
|--------|------|------|------|--------|
| limit | Integer | 否 | 返回数量限制 | 5 |

**成功响应：**

```json
{
  "code": 200,
  "message": "查询成功",
  "data": [
    {
      "id": 1,
      "title": "重要通知：系统升级",
      "content": "系统将进行重大升级...",
      "type": 1,
      "isTop": 1,
      "createdAt": "2025-01-30T10:00:00"
    }
  ]
}
```

---

### 43. 获取最新公告

**接口地址：** `GET /announcements/latest`

**接口描述：** 获取最新的公告列表

**认证要求：** 无需认证，公开接口

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 | 默认值 |
|--------|------|------|------|--------|
| limit | Integer | 否 | 返回数量限制 | 10 |

**成功响应：** 格式同置顶公告

---

### 44. 获取公告详情

**接口地址：** `GET /announcements/{id}`

**接口描述：** 根据ID获取公告详细信息

**认证要求：** 无需认证，公开接口

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 公告ID |

**成功响应：**

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "id": 1,
    "title": "系统维护通知",
    "content": "系统将于今晚22:00-24:00进行维护升级...",
    "type": 1,
    "status": 1,
    "isTop": 1,
    "viewCount": 1250,
    "author": {
      "id": 1,
      "username": "admin",
      "nickname": "系统管理员"
    },
    "createdAt": "2025-01-30T10:00:00",
    "updatedAt": "2025-01-30T10:00:00"
  }
}
```

**错误响应：**

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 1401 | 公告不存在 | 公告ID不存在或已删除 |

---

## 🛠️ 管理员公告管理

### 45. 获取所有公告（管理员）

**接口地址：** `GET /announcements/admin/list`

**接口描述：** 管理员分页查询所有公告（包括已删除）

**认证要求：** 需要管理员认证

**请求头：**
```
Authorization: Bearer {admin_token}
```

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 | 默认值 |
|--------|------|------|------|--------|
| current | Long | 否 | 当前页码 | 1 |
| size | Long | 否 | 每页大小 | 10 |
| status | Integer | 否 | 状态筛选（0草稿，1发布，2下线） | - |
| type | Integer | 否 | 类型筛选 | - |
| includeDeleted | Boolean | 否 | 是否包含已删除 | false |

**成功响应：** 格式同普通公告列表，包含更多字段

---

### 46. 创建公告

**接口地址：** `POST /announcements`

**接口描述：** 创建新公告

**认证要求：** 需要管理员认证

**请求头：**
```
Authorization: Bearer {admin_token}
Content-Type: application/json
```

**请求参数：**

```json
{
  "title": "新功能上线通知",
  "content": "我们很高兴地宣布...",
  "type": 1,
  "status": 1,
  "isTop": 0
}
```

**参数说明：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| title | String | 是 | 公告标题 |
| content | String | 是 | 公告内容 |
| type | Integer | 否 | 公告类型 | 1 |
| status | Integer | 否 | 状态（0草稿，1发布） | 0 |
| isTop | Integer | 否 | 是否置顶 | 0 |

**成功响应：**

```json
{
  "code": 200,
  "message": "创建成功",
  "data": 123
}
```

**响应说明：** data字段返回新创建的公告ID

---

### 47. 更新公告

**接口地址：** `PUT /announcements/{id}`

**接口描述：** 更新公告信息

**认证要求：** 需要管理员认证

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 公告ID |

**请求参数：** 同创建公告

**成功响应：**

```json
{
  "code": 200,
  "message": "更新成功",
  "data": true
}
```

---

### 48. 删除公告

**接口地址：** `DELETE /announcements/{id}`

**接口描述：** 删除公告（软删除）

**认证要求：** 需要管理员认证

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 公告ID |

**成功响应：**

```json
{
  "code": 200,
  "message": "删除成功",
  "data": true
}
```

---

### 49. 批量删除公告

**接口地址：** `DELETE /announcements/batch`

**接口描述：** 批量删除公告

**认证要求：** 需要管理员认证

**请求参数：**

```json
{
  "ids": [1, 2, 3, 4, 5]
}
```

**成功响应：**

```json
{
  "code": 200,
  "message": "批量删除成功",
  "data": true
}
```

---

### 50. 更新公告状态

**接口地址：** `PUT /announcements/{id}/status`

**接口描述：** 更新公告发布状态

**认证要求：** 需要管理员认证

**请求参数：**

```json
{
  "status": 1
}
```

**成功响应：**

```json
{
  "code": 200,
  "message": "状态更新成功",
  "data": true
}
```

---

### 51. 批量更新公告状态

**接口地址：** `PUT /announcements/batch/status`

**接口描述：** 批量更新公告状态

**认证要求：** 需要管理员认证

**请求参数：**

```json
{
  "ids": [1, 2, 3],
  "status": 1
}
```

---

### 52. 恢复公告

**接口地址：** `PUT /announcements/{id}/restore`

**接口描述：** 恢复已删除的公告

**认证要求：** 需要管理员认证

**成功响应：**

```json
{
  "code": 200,
  "message": "恢复成功",
  "data": true
}
```

---

### 53. 切换置顶状态

**接口地址：** `PUT /announcements/{id}/top`

**接口描述：** 置顶或取消置顶公告

**认证要求：** 需要管理员认证

**请求参数：**

```json
{
  "isTop": 1
}
```

**成功响应：**

```json
{
  "code": 200,
  "message": "置顶状态更新成功",
  "data": true
}
```

---

## 🏠 系统模块

### 27. 系统首页

**接口地址：** `GET /`

**接口描述：** 获取系统首页信息

**认证要求：** 无需认证，公开接口

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "欢迎来到LiuTech博客系统！"
}
```

---

### 28. 获取首页数据聚合

**接口地址：** `GET /dashboard`

**接口描述：** 获取首页所需的聚合数据，包括最新文章、热门文章、分类列表、热门标签、最新评论等

**认证要求：** 无需认证，公开接口

**成功响应：**

```json
{
  "code": 200,
  "message": "获取成功",
  "data": {
    "latestPosts": [
      {
        "id": 1,
        "title": "Spring Boot 入门教程",
        "summary": "详细介绍Spring Boot的基础知识...",
        "category": {
          "id": 1,
          "name": "后端开发"
        },
        "author": {
          "id": 1,
          "username": "liuxin",
          "avatarUrl": "https://example.com/avatar.jpg"
        },
        "tags": [
          {
            "id": 1,
            "name": "Spring Boot"
          }
        ],
        "commentCount": 5,
        "createdAt": "2024-01-15T10:30:00",
        "updatedAt": "2024-01-15T10:30:00"
      }
    ],
    "hotPosts": [
      {
        "id": 2,
        "title": "Vue.js 实战项目",
        "summary": "使用Vue.js构建现代化前端应用...",
        "category": {
          "id": 2,
          "name": "前端开发"
        },
        "author": {
          "id": 1,
          "username": "liuxin",
          "avatarUrl": "https://example.com/avatar.jpg"
        },
        "tags": [
          {
            "id": 2,
            "name": "Vue.js"
          }
        ],
        "commentCount": 12,
        "createdAt": "2024-01-10T14:20:00",
        "updatedAt": "2024-01-10T14:20:00"
      }
    ],
    "categories": [
      {
        "id": 1,
        "name": "后端开发",
        "description": "Java、Spring Boot等后端技术",
        "postCount": 15
      }
    ],
    "hotTags": [
      {
        "id": 1,
        "name": "Spring Boot",
        "postCount": 8
      }
    ],
    "latestComments": [
      {
        "id": 1,
        "postId": 1,
        "content": "这篇文章写得很好！",
        "parentId": null,
        "createdAt": "2024-01-15T10:30:00",
        "user": {
          "id": 2,
          "username": "reader",
          "avatarUrl": "https://example.com/avatar.jpg"
        }
      }
    ]
  }
}
```

**错误响应：**

```json
{
  "code": 500,
  "message": "获取首页数据失败",
  "data": null
}
```

---

### 29. 获取网站统计信息

**接口地址：** `GET /stats`

**接口描述：** 获取网站的基础统计信息，包括文章、分类、标签、评论的总数

**认证要求：** 无需认证，公开接口

**成功响应：**

```json
{
  "code": 200,
  "message": "获取成功",
  "data": {
    "postCount": 25,
    "categoryCount": 5,
    "tagCount": 15,
    "commentCount": 68
  }
}
```

**错误响应：**

```json
{
  "code": 500,
  "message": "获取统计信息失败",
  "data": null
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
| 40001 | 用户未登录 | 用户认证失败 |

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
| 1103 | 无权限操作此文章 | 用户无权限编辑或删除文章 |
| 1104 | 文章状态无效 | 文章状态值不正确 |
| 1105 | 分类不存在 | 指定的分类ID不存在 |
| 1106 | 标签不存在 | 指定的标签ID不存在 |

### 分类相关业务错误 (1100-1199)
| 状态码 | 错误信息 | 说明 |
|--------|----------|------|
| 1105 | 分类不存在 | 指定的分类ID不存在 |

### 标签相关业务错误 (1100-1199)
| 状态码 | 错误信息 | 说明 |
|--------|----------|------|
| 1106 | 标签不存在 | 指定的标签ID不存在 |

### 评论相关业务错误 (1200-1299)
| 状态码 | 错误信息 | 说明 |
|--------|----------|------|
| 1201 | 评论不存在 | 指定的评论ID不存在 |
| 1202 | 父评论不存在 | 回复的父评论不存在 |
| 1203 | 父评论与当前文章不匹配 | 回复评论与文章不匹配 |
| 1204 | 评论内容不能为空 | 评论内容验证失败 |
| 1205 | 评论内容超出长度限制 | 评论内容超过1000字符 |

### 点赞收藏相关业务错误 (1300-1399)
| 状态码 | 错误信息 | 说明 |
|--------|----------|------|
| 1301 | 您已经点赞过了 | 重复点赞操作 |
| 1302 | 您已经收藏过了 | 重复收藏操作 |
| 1303 | 取消点赞失败 | 取消点赞操作失败 |
| 1304 | 取消收藏失败 | 取消收藏操作失败 |

### 公告相关业务错误 (1400-1499)
| 状态码 | 错误信息 | 说明 |
|--------|----------|------|
| 1401 | 公告不存在 | 指定的公告ID不存在 |
| 1402 | 公告标题已存在 | 创建公告时标题重复 |
| 1403 | 无权限操作此公告 | 用户无权限编辑或删除公告 |

### 服务器错误 (5xx)
| 状态码 | 错误信息 | 说明 |
|--------|----------|------|
| 500 | 系统异常，请联系管理员 | 服务器内部错误 |
| 501 | 数据库操作异常 | 数据库错误 |
| 502 | 网络异常，请稍后重试 | 网络连接错误 |
| 503 | 操作失败 | 通用操作失败错误 |

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

- **当前版本**：v1.6.0

#### v1.6.0 (2025-01-31)
**✨ 新增多个功能模块，完善API生态**
- **新增签到模块**：每日签到和签到状态查询接口，支持积分奖励
- **新增文件管理模块**：图片、文档、资源上传，TinyMCE编辑器支持，附件管理
- **新增资源下载模块**：积分购买资源，下载验证，购买状态查询
- **新增公告管理模块**：前台公告展示，后台公告管理，支持置顶和批量操作
- **完善用户功能**：用户资料展示，作者资料，文章点赞/收藏功能
- **新增标签搜索**：支持按名称搜索标签功能
- **完善错误码体系**：新增1300-1399点赞收藏相关，1400-1499公告相关错误码
- **文档优化**：统一文件上传限制说明，完善认证要求描述

#### v1.5.0 (2025-01-30)
**✨ 新增系统模块和数据聚合功能**
- 新增首页数据聚合接口（GET /dashboard）
- 新增网站统计信息接口（GET /stats）
- 新增用户统计信息接口（GET /user/stats）
- 新增文章搜索接口（GET /posts/search）
- 新增根据文章ID查询标签接口（GET /tags/post/{postId}）
- 完善系统首页接口（GET /）
- 优化API文档结构，增强接口描述
- 完善错误码体系，新增通用操作错误码
- 提升用户体验，提供丰富的数据展示功能

#### v1.4.0 (2025-01-28)
**✨ 新增草稿箱功能**
- 新增草稿箱列表查询接口（GET /posts/drafts）
- 扩展PostQueryReq，支持按文章状态和作者ID筛选
- 更新PostsMapper，支持status和authorId参数查询
- 优化PostsService，增强文章查询灵活性
- 完善前端草稿箱页面，集成真实API接口
- 支持草稿文章的编辑、发布、删除操作
- 实现草稿箱关键词搜索和分页功能
- 优化用户体验，提供完整的草稿管理流程

### v1.3.0 (2025-01-15)
**✨ 新增评论模块**
- 新增评论分页查询接口（GET /comments/post/{postId}）
- 新增文章树形评论结构查询接口（GET /comments/post/{postId}/tree）
- 新增创建评论接口（POST /comments），支持回复功能
- 新增评论数量统计接口（GET /comments/post/{postId}/count）
- 新增子评论查询接口（GET /comments/{parentId}/children）
- 新增最新评论查询接口（GET /comments/latest）
- 新增评论详情查询接口（GET /comments/{id}）
- 创建CommentResl响应类，优化API返回数据结构
- 创建CreateCommentReq请求类，规范评论创建参数
- 新增评论相关错误码定义（1200-1299）
- 完善评论权限控制和参数验证

#### v1.2.1 (2024-01-XX)
**🔧 错误码修复与验证**
- 修复CategoriesController中错误码使用不一致问题（NOT_FOUND → CATEGORY_NOT_FOUND）
- 修复TagsController中错误码使用不一致问题（NOT_FOUND → TAG_NOT_FOUND）
- 修正API文档中分类和标签错误码定义（1200-1299/1300-1399 → 1105/1106）
- 验证所有Controller和Service层错误码使用的一致性
- 确保文档与代码实现完全对应

#### v1.2.0 (2024-01-XX)
**✨ 新增功能**
- 新增分类管理模块API
- 新增标签管理模块API
- 完善API文档认证要求说明
- 统一Header配置规范
- 新增分类和标签错误码定义
- 同步更新数据库设计文档
- 修复SQL文档不一致问题

- **历史版本**：v1.0.0
- **更新日期**：2025-01-31
- **维护人员**：刘鑫

---

## 📝 更新日志

### v1.6.0 (2025-01-31)
- **新增签到模块**：完整的签到功能，支持每日签到获得积分奖励
- **新增文件管理模块**：
  - 支持图片（最大10MB）、文档（最大100MB）、资源文件上传
  - TinyMCE编辑器专用图片上传接口
  - 草稿附件和文章附件管理功能
  - 附件元信息更新（下载类型、积分设置）
- **新增资源下载模块**：
  - 积分购买资源功能
  - 资源下载权限验证
  - 购买状态查询接口
- **新增公告管理模块**：
  - 前台公告展示（列表、置顶、最新、详情）
  - 后台管理功能（增删改查、批量操作、状态管理）
  - 支持公告置顶和恢复功能
- **完善用户功能**：
  - 用户公开资料展示
  - 作者资料详情页面
  - 文章点赞/收藏功能
  - 用户收藏列表查询
- **新增标签搜索功能**：支持按名称搜索标签
- **错误码优化**：新增点赞收藏（1300-1399）和公告（1400-1499）相关错误码

### v1.4.0 (2025-01-28)
- 新增草稿箱功能模块，支持用户管理个人草稿文章
- 实现草稿列表分页查询，支持关键词搜索和排序
- 扩展文章查询参数，支持按状态（draft/published）和作者筛选
- 优化MyBatis映射文件，增强SQL查询灵活性
- 完善前端草稿箱页面，提供完整的草稿管理功能
- 集成真实API接口，替换模拟数据
- 支持草稿的编辑、发布、删除等操作
- 优化用户体验，提供直观的草稿状态管理

### v1.3.0 (2025-01-15)
- 新增评论管理模块，支持评论的增删改查功能
- 实现评论分页查询和树形结构展示
- 支持评论回复功能，构建多层级评论体系
- 新增评论数量统计和最新评论查询
- 创建CommentResl响应类，优化API返回数据安全性
- 创建CreateCommentReq请求类，规范评论创建参数验证
- 新增评论相关错误码定义（1200-1299）
- 完善评论权限控制，确保用户认证安全
- 优化评论数据结构，避免敏感信息泄露

### v1.2.0 (2025-01-15)
- 新增分类管理模块，支持分类列表查询和详情获取
- 新增标签管理模块，支持标签列表查询、详情获取和热门标签
- 完善API文档认证要求说明，明确区分公开接口和需认证接口
- 统一Header配置规范，为需要认证的接口添加Content-Type
- 新增分类和标签相关错误码定义
- 同步更新数据库设计文档，添加posts表status字段
- 修复SQL文档与实际表结构不一致的问题

### v1.1.0 (2025-07-25)
- 新增文章管理模块
- 实现文章的增删改查功能
- 支持文章状态管理（草稿/已发布）
- 实现文章标签关联管理
- 新增热门文章和最新文章接口
- 完善权限控制和异常处理

### v1.0.0 (2025-07-20)
- 初始版本发布
- 完成用户认证模块
- 完成用户信息管理模块
- 完成用户管理模块（管理员）
- 建立统一的错误处理机制
- 实现 JWT 认证机制

---

*本文档将随着系统功能的扩展持续更新，请关注最新版本。*