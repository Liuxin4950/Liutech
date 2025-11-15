## 目标

* 最小改动接入 AI 服务：仅在 Compose 增加 AI 容器、MySQL 初始化 AI 表、前端生产走 Nginx `/ai` 代理、后端 AI 服务内部调用主服务走容器内地址。

## Compose 改动（极简）

* 在 `mysql` 的初始化卷中再挂载一份 AI 表结构：

```yaml
services:
  mysql:
    volumes:
      - mysql_data:/var/lib/mysql
      - ./sql.sql:/docker-entrypoint-initdb.d/init.sql:ro
      - ./ai_chat_tables.sql:/docker-entrypoint-initdb.d/ai_chat_tables.sql:ro
```

* 说明：MySQL 官方镜像仅在“首次初始化数据库（数据目录为空）”时执行 `docker-entrypoint-initdb.d`
  。若你已启动过数据库，此方式不会再次执行；已有环境可手动运行 `ai_chat_tables.sql` 一次（更改最少）。

* 在 `docker-compose.yml` 新增 AI 服务（不对外暴露端口，交由 Nginx 反代）：

```yaml
services:
  ai:
    image: liutech-ai:latest
    container_name: liutech-ai
    restart: unless-stopped
    depends_on:
      mysql:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/liutech_ai?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: 123456
      # 云端大模型密钥，走环境变量注入
      SILICONFLOW_API_KEY: ${SILICONFLOW_API_KEY}
      OPENAI_API_KEY: ${OPENAI_API_KEY}
    # 仅容器内访问，省略 ports 映射；Nginx 通过容器网络访问 ai:8081
```

* Nginx 已有 `/ai/` 反代到 `ai:8081` 的配置，无需改动（若缺失再补齐）。

## 前端生产访问（极简）

* 生产环境统一走主域名下的 `/ai` 代理，避免跨域与端口暴露：

    * 在 `Web/.env.production` 增加：

```
VITE_AI_BASE_URL=/ai
```

* 代码侧让 AI 服务地址读取环境变量（替换当前硬编码）：

    * 文件：`Web/src/config/services.ts`

    * 将 `AI` 的 `baseURL` 从 `isDevelopment ? 'http://127.0.0.1:8081' : 'https://ai.liutech.com'` 改为：

```ts
const getAiURL = (): string => {
  const envUrl = import.meta.env.VITE_AI_BASE_URL as string | undefined
  if (envUrl && envUrl.trim().length > 0) return envUrl
  return isDevelopment ? 'http://127.0.0.1:8081' : '/ai'
}
...
[ServiceType.AI]: {
  baseURL: getAiURL(),
  timeout: 60000,
  name: 'AI服务'
}
```

* 开发环境可保留默认直连 `http://127.0.0.1:8081`，或在本地也走 `/ai`（取决于是否启用本地 Nginx）。

## 后端 AI 服务内访问主服务（更正）

* 位置：`LiuTech-AI/src/main/java/chat/liuxin/ai/service/impl/AiChatServiceImpl.java:49-50`

    * 属性 `server.base-url` 当前默认 `http://localhost:8080`，部署到容器后应改为容器内主服务地址。

* 简化做法（推荐）：

    * 在 AI 的 `application-prod.yml` 设：`server.base-url=http://backend:8080`

    * 并将文章内容接口改为主服务统一前缀 `/api`：

        * 位置：`LiuTech-AI/src/main/java/chat/liuxin/ai/service/impl/AiChatServiceImpl.java:271-273`

        * 原：

```java
String apiUrl = serverBaseUrl + "/posts/" + articleId;
```

```
- 改：
```

```java
String apiUrl = serverBaseUrl + "/api/posts/" + articleId;
```

* 这样 AI 容器内通过 `backend:8080` 直连主服务，避免走外部 Nginx 与跨域；前端依旧通过 Nginx `/api`/`/ai` 访问。

## 最少变更清单

* `docker-compose.yml`：为 `mysql` 增加一行 AI 表初始化卷；新增一个 `ai` 服务块（环境变量走 `.env`）。

* `Web/.env.production`：新增 `VITE_AI_BASE_URL=/ai`。

* `Web/src/config/services.ts`：让 AI 基础地址读取 `VITE_AI_BASE_URL`，生产默认 `/ai`。

* `LiuTech-AI`：`application-prod.yml` 写入 `server.base-url=http://backend:8080`；`AiChatServiceImpl`
  的文章接口前缀改为 `/api`。

## 验证

* 启动后访问：`http://localhost:80/ai/status` 与前端页面 AI 聊天，确认能通；文章详情页触发时能正确取主服务文章内容。

## 备注

* 已有数据库不会自动执行 `ai_chat_tables.sql`，如非首次初始化请手动执行一次该脚本；之后应用自己维护表结构即可。

