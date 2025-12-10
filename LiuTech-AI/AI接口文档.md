# LiuTech AI 接口文档

## 项目概述

LiuTech AI 是一个基于 Spring Boot 3.x 和 Spring AI 的智能聊天服务系统，提供完整的AI对话功能，包括普通聊天、流式聊天、会话管理和历史记录查询等功能。

### 技术栈
- **后端框架**: Spring Boot 3.x
- **AI框架**: Spring AI
- **数据库**: MySQL + MyBatis Plus
- **认证**: JWT Token
- **AI模型**: 硅基流动 (SiliconFlow) API

### 基础信息
- **服务地址**: http://localhost:8081
- **API版本**: v1
- **认证方式**: JWT Bearer Token
- **数据格式**: JSON

---

## 认证说明

所有API接口都需要JWT Token认证，请在请求头中添加：

```
Authorization: Bearer <your-jwt-token>
```

### 获取Token
请联系系统管理员获取JWT Token，或使用登录接口获取。

---

## 聊天接口

### 1. 普通聊天接口

**接口地址**: `POST /ai/chat`

**功能描述**: 一次性返回完整的AI回复，适合不需要实时反馈的场景。

**请求参数**:
```json
{
  "message": "你好，请介绍一下你自己",
  "mode": "normal",
  "model": "THUDM/GLM-Z1-9B-0414",
  "temperature": 0.2,
  "maxTokens": 1000,
  "conversationId": 123,
  "context": {
    "page": "chat",
    "userId": "1001"
  }
}
```

**参数说明**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| message | String | 是 | 用户消息内容，最大2000字符 |
| mode | String | 否 | 聊天模式，默认"normal" |
| model | String | 否 | AI模型名称，默认使用系统配置 |
| temperature | Double | 否 | 温度参数(0.0-1.0)，控制随机性 |
| maxTokens | Integer | 否 | 最大令牌数，控制回复长度 |
| conversationId | Long | 否 | 会话ID，为空时创建新会话 |
| context | Map | 否 | 前端上下文信息 |

**响应示例**:
```json
{
  "success": true,
  "message": "你好！我是LiuTech AI助手...",
  "model": "THUDM/GLM-Z1-9B-0414",
  "processingTime": 1250,
  "responseLength": 156,
  "emotion": "friendly",
  "action": null,
  "conversationId": 123
}
```

**响应字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| success | Boolean | 请求是否成功 |
| message | String | AI回复内容或错误信息 |
| model | String | 使用的AI模型 |
| processingTime | Long | 处理耗时(毫秒) |
| responseLength | Integer | 回复内容长度 |
| emotion | String | 情绪标签 |
| action | String | 动作指令 |
| conversationId | Long | 会话ID |

---

### 2. 流式聊天接口

**接口地址**: `POST /ai/chat/stream`

**功能描述**: 通过Server-Sent Events (SSE) 实时推送AI回复，提供更好的用户体验。

**请求参数**: 与普通聊天接口相同

**响应格式**: SSE事件流，包含以下事件类型：

#### start 事件
```json
{
  "event": "start",
  "data": {
    "conversationId": 123,
    "model": "THUDM/GLM-Z1-9B-0414"
  }
}
```

#### data 事件
```json
{
  "event": "data",
  "data": {
    "content": "你好！",
    "conversationId": 123
  }
}
```

#### complete 事件
```json
{
  "event": "complete",
  "data": {
    "conversationId": 123,
    "responseLength": 156
  }
}
```

#### error 事件
```json
{
  "event": "error",
  "data": {
    "conversationId": 123,
    "error": "AI服务连接失败"
  }
}
```

**JavaScript 客户端示例**:
```javascript
const eventSource = new EventSource('/ai/chat/stream', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + token
  },
  body: JSON.stringify({
    message: "你好",
    conversationId: 123
  })
});

eventSource.onmessage = function(event) {
  const data = JSON.parse(event.data);
  console.log('收到数据:', data);
  
  switch(event.type) {
    case 'start':
      console.log('开始流式响应');
      break;
    case 'data':
      // 实时显示AI回复
      appendToChat(data.content);
      break;
    case 'complete':
      console.log('流式响应完成');
      eventSource.close();
      break;
    case 'error':
      console.error('流式响应错误:', data.error);
      eventSource.close();
      break;
  }
};
```

---

### 3. 服务状态检查

**接口地址**: `GET /ai/status`

**功能描述**: 检查AI服务是否正常运行。

**响应示例**:
```json
"服务可用，用户ID: 1001"
```

---

## 历史记录接口

### 1. 获取聊天历史

**接口地址**: `GET /ai/chat/history`

**功能描述**: 分页查询用户的聊天历史记录。

**请求参数**:
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | Integer | 否 | 1 | 页码，从1开始 |
| size | Integer | 否 | 20 | 每页大小，最大100 |

**请求示例**:
```
GET /ai/chat/history?page=1&size=20
```

**响应示例**:
```json
{
  "success": true,
  "data": [
    {
      "id": 456,
      "conversationId": 123,
      "role": "user",
      "content": "你好，请介绍一下你自己",
      "model": "THUDM/GLM-Z1-9B-0414",
      "tokens": 25,
      "status": 1,
      "seqNo": 1,
      "createdAt": "2025-12-10T10:30:00"
    },
    {
      "id": 457,
      "conversationId": 123,
      "role": "assistant",
      "content": "你好！我是LiuTech AI助手...",
      "model": "THUDM/GLM-Z1-9B-0414",
      "tokens": 156,
      "status": 1,
      "seqNo": 2,
      "createdAt": "2025-12-10T10:30:02"
    }
  ],
  "page": 1,
  "size": 20,
  "total": 156,
  "userId": "1001"
}
```

---

### 2. 清空聊天记忆

**接口地址**: `DELETE /ai/chat/memory`

**功能描述**: 清空用户的所有聊天记录（仅删除消息表，保留会话表）。

**响应示例**:
```json
{
  "success": true,
  "message": "聊天记忆已清空"
}
```

---

## 会话管理接口

### 1. 获取会话列表

**接口地址**: `GET /ai/conversations`

**功能描述**: 获取用户的会话列表，按最后消息时间倒序排列。

**请求参数**:
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| type | String | 否 | - | 会话类型筛选 |
| page | Integer | 否 | 1 | 页码 |
| size | Integer | 否 | 20 | 每页大小 |

**响应示例**:
```json
[
  {
    "id": 123,
    "userId": "1001",
    "title": "技术讨论",
    "createdAt": "2025-12-10T10:00:00",
    "updatedAt": "2025-12-10T10:30:00"
  },
  {
    "id": 122,
    "userId": "1001",
    "title": "新会话",
    "createdAt": "2025-12-09T15:20:00",
    "updatedAt": "2025-12-09T15:25:00"
  }
]
```

---

### 2. 创建新会话

**接口地址**: `POST /ai/conversations`

**功能描述**: 创建新的聊天会话。

**请求参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | String | 否 | 会话类型 |
| title | String | 否 | 会话标题，默认"新会话" |

**请求示例**:
```
POST /ai/conversations
Content-Type: application/x-www-form-urlencoded

title=产品咨询
```

**响应示例**:
```json
{
  "success": true,
  "message": "会话创建成功",
  "conversationId": 124
}
```

---

### 3. 获取会话消息

**接口地址**: `GET /ai/conversations/{id}/messages`

**功能描述**: 分页获取指定会话的消息列表。

**请求参数**:
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | Integer | 否 | 1 | 页码 |
| size | Integer | 否 | 50 | 每页大小 |

**响应示例**:
```json
[
  {
    "id": 457,
    "conversationId": 123,
    "role": "assistant",
    "content": "你好！我是LiuTech AI助手...",
    "model": "THUDM/GLM-Z1-9B-0414",
    "tokens": 156,
    "status": 1,
    "seqNo": 2,
    "createdAt": "2025-12-10T10:30:02"
  }
]
```

---

### 4. 重命名会话

**接口地址**: `PUT /ai/conversations/{id}/rename`

**功能描述**: 修改会话标题。

**请求参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | String | 是 | 新的会话标题 |

**请求示例**:
```
PUT /ai/conversations/123/rename
Content-Type: application/x-www-form-urlencoded

title=新的会话标题
```

**响应示例**:
```json
{
  "success": true,
  "message": "会话重命名成功"
}
```

---

### 5. 归档会话

**接口地址**: `PUT /ai/conversations/{id}/archive`

**功能描述**: 归档指定会话（设置状态为9）。

**响应示例**:
```json
{
  "success": true,
  "message": "会话已归档"
}
```

---

### 6. 删除会话

**接口地址**: `DELETE /ai/conversations/{id}`

**功能描述**: 删除指定会话及其所有消息。

**响应示例**:
```json
{
  "success": true,
  "message": "会话已删除"
}
```

---

## 错误处理

### 错误响应格式

所有错误响应都遵循统一格式：

```json
{
  "success": false,
  "message": "错误描述信息",
  "code": 401
}
```

### 常见错误码

| 错误码 | 说明 | 处理建议 |
|--------|------|----------|
| 401 | 未认证或Token已失效 | 重新获取JWT Token |
| 403 | 权限不足 | 检查用户权限 |
| 400 | 请求参数错误 | 检查请求参数格式 |
| 429 | 请求频率过高 | 降低请求频率 |
| 500 | 服务器内部错误 | 联系系统管理员 |

### AI服务特定错误

| 错误类型 | 说明 | 处理建议 |
|----------|------|----------|
| ConnectionException | AI服务连接失败 | 检查网络连接 |
| TimeoutException | AI服务响应超时 | 重试或降低请求复杂度 |
| AIServiceException | AI服务处理异常 | 检查输入内容或联系管理员 |

---

## 数据模型

### 消息状态说明

| 状态值 | 说明 |
|--------|------|
| 1 | 完成 |
| 0 | 流式中断 |
| 2 | 内容审核拒绝 |
| 3 | API异常 |
| 9 | 系统错误 |

### 角色类型

| 角色 | 说明 |
|------|------|
| user | 用户消息 |
| assistant | AI助手回复 |
| system | 系统消息 |

---

## 使用示例

### 完整的聊天流程

```javascript
// 1. 创建新会话
const createResponse = await fetch('/ai/conversations', {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer ' + token,
    'Content-Type': 'application/x-www-form-urlencoded'
  },
  body: 'title=新的对话'
});
const { conversationId } = await createResponse.json();

// 2. 发送消息（流式）
const eventSource = new EventSource('/ai/chat/stream', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + token
  },
  body: JSON.stringify({
    message: "你好，请介绍一下LiuTech AI",
    conversationId: conversationId
  })
});

// 3. 处理流式响应
let fullResponse = '';
eventSource.onmessage = function(event) {
  const data = JSON.parse(event.data);
  
  switch(event.type) {
    case 'start':
      console.log('开始接收AI回复');
      break;
    case 'data':
      fullResponse += data.content;
      updateChatUI(data.content);
      break;
    case 'complete':
      console.log('AI回复完成，总长度:', data.responseLength);
      eventSource.close();
      break;
    case 'error':
      console.error('AI回复出错:', data.error);
      eventSource.close();
      break;
  }
};

// 4. 获取历史记录
const historyResponse = await fetch('/ai/chat/history?page=1&size=20', {
  headers: {
    'Authorization': 'Bearer ' + token
  }
});
const history = await historyResponse.json();
```

---

## 性能优化建议

### 1. 流式接口使用
- 对于长文本回复，优先使用流式接口
- 流式接口可以显著提升用户体验
- 注意正确处理SSE连接的关闭

### 2. 会话管理
- 合理使用会话ID，避免频繁创建新会话
- 定期清理不需要的历史会话
- 使用归档功能管理长期会话

### 3. 错误处理
- 实现完善的错误重试机制
- 对网络错误进行友好提示
- 记录错误日志便于问题排查

### 4. 缓存策略
- 可以在客户端缓存会话列表
- 对频繁访问的历史记录进行本地缓存

---

## 版本更新记录

### v1.0.0 (2025-12-10)
- 初始版本发布
- 支持普通聊天和流式聊天
- 完整的会话管理功能
- JWT认证机制
- 历史记录查询

---

## 联系方式

如有问题或建议，请联系：
- **作者**: 刘鑫
- **邮箱**: [联系邮箱]
- **项目地址**: [项目仓库地址]

---

*本文档最后更新时间: 2025-12-10*