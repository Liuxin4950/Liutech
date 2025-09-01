# LiuTech AI 接口文档

> **作者**: 刘鑫  
> **创建时间**: 2025-09-01  
> **服务版本**: 1.0.0  
> **基础URL**: `http://localhost:8081/api/ai`

## 概述

LiuTech AI 是基于 Spring AI + Ollama 构建的智能助手服务，提供AI聊天对话、会话管理、流式响应等功能。

### 核心特性

- ✨ AI聊天对话（普通模式 + 流式模式）
- 🧠 智能会话记忆管理
- 👥 多用户会话隔离
- 📊 统计信息与健康检查
- 🔄 实时流式响应（SSE）
- 🎯 多模型支持

---

## API 端点列表

### 1. AI 聊天接口

#### 1.1 普通聊天模式

**接口地址**: `POST /api/ai/chat`

**功能描述**: 发送消息给AI并获取完整响应

**请求参数**:

```json
{
  "userId": "test_user",
  "sessionId": "session_001",
  "message": "你好，银月！",
  "mode": "normal",
  "model": "ollama",
  "temperature": 0.7,
  "maxTokens": 2000
}
```

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| userId | String | ✅ | 用户ID，用于标识用户身份 |
| sessionId | String | ❌ | 会话ID，不提供则使用默认会话 |
| message | String | ✅ | 用户消息内容（最大2000字符） |
| mode | String | ❌ | 聊天模式，默认"normal" |
| model | String | ❌ | AI模型名称，默认"ollama" |
| temperature | Double | ❌ | 温度参数（0.0-1.0） |
| maxTokens | Integer | ❌ | 最大令牌数 |

**响应示例**:

```json
{
  "success": true,
  "message": "你好！我是银月，很高兴为你服务～有什么可以帮助你的吗？",
  "userId": "test_user",
  "model": "ollama",
  "historyCount": 2,
  "timestamp": 1725184567890,
  "processingTime": 1250,
  "responseLength": 28
}
```

#### 1.2 流式聊天模式

**接口地址**: `POST /api/ai/chat/stream`

**功能描述**: 发送消息给AI并获取流式响应（Server-Sent Events）

**请求参数**: 同普通聊天模式

**响应格式**: `text/event-stream`

**SSE 事件类型**:

```
# 用户信息事件
event: user
data: {"userId": "test_user"}

# 开始处理事件
event: start
data: {"message": "AI正在思考中...", "historyCount": 2}

# 数据流事件
event: data
data: {"chunk": "你好"}

event: data
data: {"chunk": "！我是"}

event: data
data: {"chunk": "银月"}

# 完成事件
event: done
data: {"message": "响应完成", "totalLength": 28, "processingTime": 1250}

# 错误事件
event: error
data: {"error": "处理异常: 连接超时"}
```

### 2. 会话管理接口

#### 2.1 获取用户会话列表

**接口地址**: `GET /api/ai/chat/user/{userId}/sessions`

**功能描述**: 获取指定用户的所有会话列表

**路径参数**:
- `userId`: 用户ID

**响应示例**:

```json
{
  "success": true,
  "message": null,
  "userId": "test_user",
  "sessions": [
    {
      "sessionId": "session_001",
      "sessionName": "关于AI的讨论",
      "messageCount": 8,
      "lastActiveTime": 1725184567890,
      "lastMessage": "谢谢你的解答！"
    },
    {
      "sessionId": "default",
      "sessionName": "默认对话",
      "messageCount": 2,
      "lastActiveTime": 1725184500000,
      "lastMessage": "你好，银月！"
    }
  ]
}
```

#### 2.2 获取会话历史记录

**接口地址**: `GET /api/ai/chat/user/{userId}/sessions/{sessionId}/history`

**功能描述**: 获取指定会话的完整历史消息

**路径参数**:
- `userId`: 用户ID
- `sessionId`: 会话ID

**响应示例**:

```json
{
  "success": true,
  "message": null,
  "userId": "test_user",
  "sessionId": "session_001",
  "totalCount": 4,
  "messages": [
    {
      "type": "user",
      "content": "你好，银月！",
      "timestamp": 1725184500000
    },
    {
      "type": "assistant",
      "content": "你好！我是银月，很高兴为你服务～",
      "timestamp": 1725184501250
    },
    {
      "type": "user",
      "content": "请介绍一下你的功能",
      "timestamp": 1725184520000
    },
    {
      "type": "assistant",
      "content": "我可以帮你进行AI对话、管理聊天记忆、提供流式响应等功能～",
      "timestamp": 1725184522100
    }
  ]
}
```

### 3. 记忆管理接口

#### 3.1 清理用户所有会话

**接口地址**: `DELETE /api/ai/chat/user/{userId}`

**功能描述**: 清理指定用户的所有会话和消息记录

**路径参数**:
- `userId`: 用户ID

**响应示例**:

```json
{
  "success": true,
  "message": "用户记忆已清理",
  "operation": "clear_user_memory",
  "userId": "test_user",
  "timestamp": 1725184567890,
  "processingTime": 150,
  "data": null
}
```

#### 3.2 清理指定会话

**接口地址**: `DELETE /api/ai/chat/user/{userId}/sessions/{sessionId}`

**功能描述**: 清理指定用户的特定会话记录

**路径参数**:
- `userId`: 用户ID
- `sessionId`: 会话ID

**响应示例**:

```json
{
  "success": true,
  "message": "会话记忆已清理",
  "operation": "clear_session_memory",
  "userId": "test_user",
  "timestamp": 1725184567890,
  "processingTime": 80,
  "data": {
    "sessionId": "session_001",
    "deletedMessages": 8
  }
}
```

### 4. 系统信息接口

#### 4.1 健康检查

**接口地址**: `GET /api/ai/health`

**功能描述**: 检查AI服务的健康状态

**响应示例**:

```json
{
  "success": true,
  "status": "healthy",
  "message": "AI服务运行正常",
  "timestamp": 1725184567890,
  "responseTime": 120,
  "version": "1.0.0",
  "model": "ollama"
}
```

#### 4.2 服务信息

**接口地址**: `GET /api/ai/info`

**功能描述**: 获取AI服务的基本信息

**响应示例**:

```json
{
  "service": "LiuTech AI Service",
  "version": "1.0.0",
  "author": "刘鑫",
  "description": "基于Spring AI + Ollama的智能助手",
  "startTime": 1725184400000,
  "currentTime": 1725184567890,
  "features": [
    "AI聊天对话",
    "流式响应",
    "用户记忆管理",
    "多模型支持",
    "健康检查",
    "统计信息"
  ],
  "apiDocs": "/api/ai/docs"
}
```

#### 4.3 统计信息

**接口地址**: `GET /api/ai/chat/stats`

**功能描述**: 获取AI服务的统计数据

**响应示例**:

```json
{
  "success": true,
  "message": null,
  "activeUserCount": 15,
  "activeSessionCount": 42,
  "serverTime": 1725184567890,
  "uptime": 3600000,
  "totalRequests": 1250,
  "avgResponseTime": 850.5
}
```

---

## 错误响应格式

所有接口在发生错误时都会返回统一的错误格式：

```json
{
  "success": false,
  "message": "具体错误信息",
  "timestamp": 1725184567890,
  "error": {
    "code": "ERROR_CODE",
    "details": "详细错误描述"
  }
}
```

### 常见错误码

| 错误码 | HTTP状态码 | 说明 |
|--------|------------|------|
| INVALID_REQUEST | 400 | 请求参数无效 |
| USER_NOT_FOUND | 404 | 用户不存在 |
| SESSION_NOT_FOUND | 404 | 会话不存在 |
| AI_SERVICE_ERROR | 500 | AI服务异常 |
| DATABASE_ERROR | 500 | 数据库连接异常 |
| RATE_LIMIT_EXCEEDED | 429 | 请求频率超限 |

---

## 使用示例

### PowerShell 示例

```powershell
# 1. 发送聊天消息
$chatRequest = @{
    userId = "test_user"
    sessionId = "my_session"
    message = "你好，银月！请介绍一下你的功能"
} | ConvertTo-Json

$response = Invoke-RestMethod -Uri "http://localhost:8081/api/ai/chat" `
    -Method POST `
    -ContentType "application/json" `
    -Body $chatRequest

Write-Output $response

# 2. 获取会话列表
$sessions = Invoke-RestMethod -Uri "http://localhost:8081/api/ai/chat/user/test_user/sessions" `
    -Method GET

Write-Output $sessions

# 3. 健康检查
$health = Invoke-RestMethod -Uri "http://localhost:8081/api/ai/health" `
    -Method GET

Write-Output $health
```

### JavaScript 示例

```javascript
// 1. 普通聊天
async function sendMessage(userId, message, sessionId = 'default') {
    const response = await fetch('http://localhost:8081/api/ai/chat', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            userId,
            sessionId,
            message
        })
    });
    
    return await response.json();
}

// 2. 流式聊天
function streamChat(userId, message, sessionId = 'default') {
    const eventSource = new EventSource('http://localhost:8081/api/ai/chat/stream', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            userId,
            sessionId,
            message
        })
    });
    
    eventSource.onmessage = function(event) {
        const data = JSON.parse(event.data);
        console.log('收到数据:', data);
    };
    
    eventSource.onerror = function(error) {
        console.error('连接错误:', error);
        eventSource.close();
    };
}

// 3. 获取会话历史
async function getSessionHistory(userId, sessionId) {
    const response = await fetch(`http://localhost:8081/api/ai/chat/user/${userId}/sessions/${sessionId}/history`);
    return await response.json();
}
```

---

## 数据库设计

### 会话表 (chat_sessions)

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 主键ID |
| session_id | VARCHAR(64) | 会话标识符 |
| user_id | BIGINT | 用户ID |
| title | VARCHAR(255) | 会话标题 |
| message_count | INT | 消息数量 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |
| deleted_at | TIMESTAMP | 软删除时间 |

### 消息表 (chat_messages)

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 主键ID |
| session_id | VARCHAR(64) | 会话标识符 |
| user_id | BIGINT | 用户ID |
| role | ENUM | 消息角色(user/assistant/system) |
| content | TEXT | 消息内容 |
| tokens_used | INT | 使用的token数量 |
| created_at | TIMESTAMP | 创建时间 |

---

## 注意事项

1. **用户ID处理**: 系统会自动将字符串类型的userId转换为Long类型存储
2. **会话管理**: 每个用户可以有多个并行会话，通过sessionId区分
3. **记忆机制**: 系统会保留最近10条消息作为上下文记忆
4. **流式响应**: 使用Server-Sent Events技术，需要客户端支持EventSource
5. **错误处理**: 所有接口都有统一的错误处理机制
6. **性能优化**: 建议合理使用sessionId避免单会话消息过多

---

## 更新日志

### v1.0.0 (2025-09-01)
- ✨ 初始版本发布
- 🎯 实现基础AI聊天功能
- 🧠 支持会话记忆管理
- 🔄 支持流式响应
- 📊 提供统计和健康检查接口
- 💾 完整的数据库持久化方案

---

> **银月小贴士**: 这份文档会随着功能更新而持续完善，有问题随时找银月哦～ 🐺✨