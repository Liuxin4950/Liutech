# PRD：上传文件迁移到腾讯云 COS 对象存储

> 状态：设计待确认 · 计划实现日期：2026-08-04
> 关联架构：[部署运维/总览.md](../架构/运维/部署运维/总览.md)、[当前架构.md](../记录/当前架构.md)「文件上传与静态资源 URL」章节

## 一、背景与目标

当前所有上传文件落在服务器本地磁盘 `/liuxin/uploads`（bind mount 进 backend 容器 `/app/uploads`），由 Spring Boot 静态资源映射直接对外提供。2026-07 曾发生整个 7 月上传文件丢失事故（目录残留、文件不在，DB 有引用但磁盘无文件），根因是 uploads **无任何备份**，服务器磁盘事故即丢数据。

目标：把**公开静态上传文件**的存储从服务器本地盘迁到腾讯云 COS（多副本对象存储），消除单点丢数据风险；CDN 在 COS 前加速。私有付费资源和临时缓存不迁移。

非目标：不重构上传业务逻辑、不做图片处理/缩略图服务、不引入多云抽象。

## 二、现状盘点（改之前必须知道的全部入口）

uploads 下有 5 类文件，处理方式不同：

| 子目录 | 用途 | 公开性 | 写入入口 | 对外服务方式 | 是否迁 COS |
| --- | --- | --- | --- | --- | --- |
| `images/` | TinyMCE 文章图片、封面、头像、轮播图 | 公开 | `ImagesService.uploadImage`（去重+压缩） | `WebConfig` 映射 `/uploads/images/**` | **是（首期）** |
| `documents/` | 公告导入附件等文档 | 公开 | `FileUploadService.uploadDocument` | `/uploads/documents/**` | 二期 |
| `music/` | 博客背景音乐（原唱+伴奏双轨） | 公开 | `MusicService` | `/uploads/music/**` | 二期 |
| `resources/` | 付费/积分资源附件 | **私有** | `FileUploadService.uploadResource` | **不做静态暴露**，走 `/resource/download/{id}` 购买校验 | **否，永远留本地** |
| `tts-cache/` | TTS 合成音频缓存 | 公开但临时 | `TtsSpeechService`（LRU 淘汰） | `/uploads/tts-cache/**` | **否**（高 churn、无备份价值） |

关键代码位置：
- 保存：[FileUtil.java:58](../../LiuTech/src/main/java/chat/liuxin/liutech/utils/FileUtil.java#L58) `saveFile`（本地落盘）、`:92` 字节数组版
- URL 生成：[FileUtil.java:188](../../LiuTech/src/main/java/chat/liuxin/liutech/utils/FileUtil.java#L188) `generateFileUrl`（返回相对路径 `/uploads/...`）
- 路径解析：[FileUtil.java:227](../../LiuTech/src/main/java/chat/liuxin/liutech/utils/FileUtil.java#L227) `extractRelativePath`（兼容绝对/相对 URL）
- 删除：[FileUtil.java:202](../../LiuTech/src/main/java/chat/liuxin/liutech/utils/FileUtil.java#L202)、`:250` `deleteFileByUrl`
- 图片去重：[ImagesService.java:45](../../LiuTech/src/main/java/chat/liuxin/liutech/service/ImagesService.java#L45)（按 `file_hash` 查重，命中则复用、不重复存文件）
- 静态映射：[WebConfig.java:26](../../LiuTech/src/main/java/chat/liuxin/liutech/config/WebConfig.java#L26)
- 调用 `saveFile` 的地方：`ImagesService`、`FileUploadService.uploadDocument/uploadResource`、`MusicService:135,139`

## 三、目标架构

```
访客浏览器
  │
  ├─ https://liuxin.chat/uploads/images/xxx.jpg   (相对URL，主域名经CDN)
  │     └─ 腾讯云CDN ──路径回源──▶ COS桶(images/xxx.jpg)
  │
  ├─ https://admin.liuxin.chat/uploads/images/...  (后台直连源站)
  │     └─ nginx 301跳转到 https://liuxin.chat/uploads/... (再走CDN→COS)
  │
  └─ /resource/download/{id} (付费资源，经购买校验)
        └─ nginx → backend → 读本地盘 /app/uploads/resources/
```

- 新图片由 backend 通过 **COS Java SDK** 写入 COS，不再写本地盘。
- 对外 URL **仍为相对路径** `/uploads/images/...`，DB 存储格式不变（见下方关键决策①）。
- 主域名 CDN 配置「路径回源」：`/uploads/*` 回源到 COS 桶，其余路径仍回源到服务器。
- 付费资源、TTS 缓存继续用本地盘，backend 容器保留 `/app/uploads` 挂载。

## 四、关键决策（动手前必须先定，别做到一半返工）

### ① URL 策略：坚持相对路径，不回退到绝对 URL（强烈建议）

我们刚把 DB 里的绝对 URL（`https://www.liuxin.chat/...`）全部迁成相对路径，原因是绝对 URL 绑死域名、环境不通用、证书/域名一变全裂。COS 迁移**不能重新引入绝对 URL**，否则等于白迁。

做法：
- `file_path` 存对象 key：`images/2026/08/03/xxx.jpg`（不变）
- `file_url` 存相对 URL：`/uploads/images/2026/08/03/xxx.jpg`（不变，`generateFileUrl` 不改返回格式）
- 浏览器请求当前域名 + `/uploads/...`，由 CDN 路径回源到 COS。
- 开发环境 vite proxy 仍把 `/uploads` 转发到本地 backend（本地用 local 存储），数据格式与生产完全一致。

代价：CDN 要配路径回源规则，admin 子域名要加跳转（见实施步骤）。这是值得的——保住 DB 环境无关性。

> 备选（不推荐）：`generateFileUrl` 返回 `https://img.liuxin.chat/uploads/...` 绝对地址。实现简单但 DB 重新绑死域名，重蹈 www 事故覆辙；且文章正文 HTML 里的老 `<img src="/uploads/...">` 仍需单独处理，没省事。

### ② 存储抽象：StorageService 接口 + 双实现，开关切换

不要把 COS SDK 调用散落在各 Service。抽一层：

```
interface StorageService {
    String store(byte[] data, String subPath, String originalFilename); // 返回 object key
    String store(MultipartFile file, String subPath);
    boolean delete(String key);
    boolean exists(String key);
}
class LocalStorageService implements StorageService { ... }   // 包现有 FileUtil.saveFile 逻辑
class CosStorageService implements StorageService { ... }     // 调 COS SDK
```

- 通过配置 `STORAGE_TYPE=local|cos`（默认 local）选择实现。
- `FileUtil.saveFile` 内部委托给 `StorageService`，调用方（ImagesService/MusicService 等）**零改动**。
- `FileUtil.deleteFile` 同理委托。
- 上线后若 COS 出问题，改环境变量 `STORAGE_TYPE=local` 重启 backend 即回退（本地旧文件还在）。

这层抽象是这次唯一值得加的间接层，符合「替换存储介质」这个真实变化点，不算过度设计。

### ③ 迁移范围：首期只迁 images

images 是出问题的、也是量最大、访问最频繁的。documents/music 列为二期，首期不动。理由：
- 缩小明天改动面，降低风险。
- music 是双轨文件、documents 有导入逻辑，各自有边角，等 images 跑稳再迁。
- resources 和 tts-cache 永不迁（见现状表）。

### ④ COS 桶与权限

- 新建一个桶（如 `liutech-uploads`，地域选离服务器近的广州/上海）。
- 桶权限：**公有读私有写**（图片要公开访问，写入必须鉴权）。
- 不用 CDN 签名 URL（图片公开，签名增加复杂度且缓存不友好）。
- 访问身份：新建腾讯云子账号/API 密钥，只授予该桶的读写权限（最小权限），`secretId/secretKey` 走 `.env` 注入，**不进 git、不进镜像**。
- 对象 key 规则与本地完全一致：`images/yyyy/MM/dd/timestamp_uuid.ext`，保证存量迁移后 key 对得上 DB。

### ⑤ CDN 是复用主域名还是新建 img 子域名

推荐：**复用主域名 `liuxin.chat`，靠 CDN 路径回源区分**。这样 URL 全是 `/uploads/...` 相对路径，零额外域名、零额外证书（主域名证书已在 CDN 配好）。
- 腾讯云 CDN → 域名管理 → `liuxin.chat` → 回源配置 → 添加「路径回源」规则：`/uploads/*` 回源到 COS 桶（源站类型选 COS 源，选桶），回源 HOST 按 COS 要求填（通常是桶默认域名）。
- 默认源（服务器 `132.232.149.184`）保持不变，处理网页/API。

不推荐现在新开 `img.liuxin.chat`：又要申请证书（现有证书不含该子域名）、又要改 URL 策略，多一套维护。除非将来主域名 CDN 路径回源不好使，再考虑。

## 五、实施计划（按顺序，每步可独立验证）

### 阶段 0：准备（控制台操作，不改代码）
1. 腾讯云创建 COS 桶 `liutech-uploads`，公有读私有写，地域确认。
2. 创建子账号 API 密钥，仅授该桶读写；记下 `secretId`/`secretSecret`/`region`/`bucket`。
3. `.env` 新增：`STORAGE_TYPE=local`（先不开）、`COS_SECRET_ID`、`COS_SECRET_KEY`、`COS_REGION`、`COS_BUCKET`。同步更新 `.env.example`（留空占位）。
4. 本地 `nginx/certs/` 和服务器证书不动。

### 阶段 1：代码（后端）
1. `pom.xml` 加腾讯云 COS SDK 依赖（`com.qcloud:cos_api`，版本锁定）。
2. 新增 `CosStorageProperties`（`@ConfigurationProperties(prefix="cos")`）绑定上述配置。
3. 新增 `StorageService` 接口 + `LocalStorageService`（把现有 `FileUtil.saveFile/deleteFile` 本地逻辑搬进来）+ `CosStorageService`（SDK 上传/删除/查询）。
4. `FileUtil` 注入 `StorageService`，`saveFile/deleteFile` 改为委托调用，**保持方法签名和返回值不变**，外部调用方零改动。
5. `CosStorageService.store` 注意：
   - 上传前先按 `file_hash` 查重的逻辑仍在 `ImagesService`，COS 侧不用管去重，但要保证同 key 不覆盖（文件名含 uuid，天然不冲突）。
   - 压缩后的字节数组走 SDK `putObject`，`ContentType` 要正确设置（`file.getContentType()`），否则浏览器下载而非显示。
   - 异常包装成 `BusinessException`，按现有日志规范 `log.error`。
6. `application.yml` 或 `@ConditionalOnProperty` 控制：`STORAGE_TYPE=cos` 时启用 `CosStorageService`，否则 `LocalStorageService`。
7. **现有 `WebConfig` 静态资源映射保留**（本地兜底 + dev + resources/music/tts），不删。

### 阶段 2：存量迁移
1. 服务器上用 COS 官方工具 `coscmd`（或写个一次性 Java/脚本）把 `/liuxin/uploads/images/` 整个目录同步到桶根，key 保持 `images/...`（即桶内路径与本地 `file_path` 一致）。
2. 迁移后抽样核对：随机挑 5 张图，用 COS 控制台/直链确认能访问、字节数一致。
3. **不要删本地文件**，保留作为回退兜底。

### 阶段 3：CDN 路径回源
1. CDN 控制台 `liuxin.chat` → 回源配置 → 添加路径回源：`/uploads/*` → COS 源（选桶）。其余路径默认回源到服务器。
2. 缓存配置保持现状（`/uploads/` 缓存 30 天）。
3. 验证：用 `curl -I https://liuxin.chat/uploads/images/...` 看响应头是否来自 COS/CDN 缓存（`X-Cache-Lookup` Hit），且 `Content-Type` 正确、能出图。

### 阶段 4：切换 + 验证
1. 服务器 `.env` 改 `STORAGE_TYPE=cos`，重新部署 backend（按 `deploy.md` 流程：build → save → scp → load → force-recreate → restart nginx）。
2. admin 后台上传一张新图，确认：COS 桶里出现该文件、DB 记录相对路径、文章里能显示（经 CDN）。
3. 验证删除：admin 删除该图，确认 COS 对象被删、DB 记录处理正确。
4. admin 子域名图片：admin.liuxin.chat 是直连服务器，`/uploads/` 目前 proxy 到 backend（本地盘，新图不在本地）。需在 nginx 的 admin server 块把 `/uploads/` 改成 `return 301 https://liuxin.chat$request_uri`（跳主域走 CDN→COS）。验证后台图片管理页缩略图正常。
5. 观察 1-2 天，确认上传、显示、删除、去重（传重复图返回已有记录）都正常。

### 阶段 5：清理（可选，跑稳后）
- 确认 COS 数据完整、CDN 命中率正常后，可考虑二期迁 documents/music（同一套 StorageService，放开 subPath 即可）。
- 本地 `images/` 旧文件先保留至少一个月再考虑清理。

## 六、风险与陷阱（明天重点盯）

1. **COS SDK 传文件没设 ContentType**：默认 `application/octet-stream`，浏览器会触发下载而不是显示图片。`putObject` 时必须 setContentType。
2. **CDN 路径回源的 HOST 与回源协议**：COS 源要求回源 HOST 是桶域名，不能填 `liuxin.chat`，否则 COS 不认。回源协议用 HTTPS。配错会全部 404/403。
3. **CORS**：`<img>` 标签加载跨域图片不需要 CORS，但 TinyMCE 若有 canvas 操作（截图、图片编辑）可能需要。COS/CDN 上给 `img` 相关路径加 `Access-Control-Allow-Origin`（主域名同源基本不会触发，但 admin 子域名跳转过来到主域也不算跨域）。先不加，出问题再补。
4. **密钥泄露**：`COS_SECRET_KEY` 只走 `.env`（已 gitignore），`.env.example` 留空。绝不写进 `application.yml` 提交。镜像里不能含密钥。
5. **去重逻辑别破坏**：`ImagesService` 按 hash 查重在上传前发生，COS 只负责存新文件。不要因为换了存储就动这套逻辑。
6. **存量文件丢失的那部分**：2026-07 丢失的图片 COS 里也不会有（本地就没有），迁移前先列清单（DB 有引用但本地无文件），这些是补图问题，不是迁移能解决的，别期待迁移修好它们。
7. **回退预案**：上线后任何异常，`.env` 改回 `STORAGE_TYPE=local` 重启 backend 即回退；本地旧文件都还在。CDN 路径回源规则可独立关闭（删规则即恢复回源服务器）。两条回退路径独立。
8. **resources 付费文件绝对不能进 COS 公有桶**：一旦进公有读桶，付费资源可被直接访问 URL 绕过购买校验。阶段 1 代码里只让 images subPath 走 COS，resources/music/tts 明确走 local。用 subPath 白名单约束，不要图省事让所有上传都走 COS。
9. **大文件/超时**：COS 上传走公网，100MB 资源文件可能慢/超时。首期只迁图片（≤10MB），正好避开；二期迁 resources 前要评估超时和分片上传。
10. **nginx admin 块的 /uploads 跳转**：别忘了改，否则后台图片管理页在 admin.liuxin.chat 下会 404（新图只在 COS）。

## 七、验收标准

- [ ] admin 上传新图，COS 桶有文件、DB `file_url` 为相对路径、前台文章正常显示。
- [ ] 上传重复图片，返回已有记录（去重生效），不产生重复 COS 对象。
- [ ] 删除图片，COS 对象同步删除。
- [ ] 存量图片经 `https://liuxin.chat/uploads/...` 全部可访问，Content-Type 正确。
- [ ] `admin.liuxin.chat/uploads/...` 跳转到主域名并能显示。
- [ ] 付费资源下载（`/resource/download/{id}`）仍走本地、购买校验正常。
- [ ] TTS 语音播放正常（仍本地缓存）。
- [ ] 本地开发环境（`STORAGE_TYPE=local`）上传/显示不受影响。
- [ ] 回退验证：改 `STORAGE_TYPE=local` 重启后，旧本地上传逻辑正常。

## 八、待你确认的决策点

1. URL 策略用「相对路径 + CDN 路径回源」（推荐）还是「绝对 img 子域名」？
2. 首期只迁 images（推荐），还是连 documents/music 一起？
3. COS 桶名和地域用什么？
4. 腾讯云子账号密钥你自己在控制台建，还是需要我给出最小权限策略 JSON？
