# 腾讯云 COS 对象存储对接教程（零基础 · 结合源码逐行讲解）

> 本教程基于 LiuTech 博客真实改造过程写成。目标读者：没有系统性学过对象存储/网络/DNS 的开发者。
> 读完你将理解：对象存储是什么、为什么博客要接、代码每一行在干什么、控制台每一步在配什么、
> 数据是怎么从浏览器一路走到重庆的 COS 机房的、以及这次踩过的每一个坑怎么解。
>
> 建议顺序阅读，每部分 10~20 分钟。代码引用可点击跳转。

---

## 目录

- [第一部分：先补概念](#第一部分先补概念)
- [第二部分：改造后的整体架构](#第二部分改造后的整体架构)
- [第三部分：源码逐层讲解](#第三部分源码逐层讲解)
- [第四部分：网络与域名（DNS / CNAME / HTTPS / 证书）](#第四部分网络与域名)
- [第五部分：腾讯云控制台操作全流程](#第五部分腾讯云控制台操作全流程)
- [第六部分：部署流程（本地到生产）](#第六部分部署流程)
- [第七部分：错误解决大全（真实踩坑记录）](#第七部分错误解决大全)
- [第八部分：日常运维 Q&A](#第八部分日常运维-qa)

---

## 第一部分：先补概念

### 1.1 你原来是怎么存图片的

改造之前，博客的图片存在**服务器自己的磁盘**上：

```
浏览器 → nginx → 后端 → 服务器磁盘（/liuxin/uploads）
```

这个架构有一个关键限制：**浏览器不能直接访问服务器磁盘**。所以图片的 URL 是
`/uploads/images/...` 这种"相对路径"，浏览器请求它时，nginx 再把请求转发给后端，
后端从磁盘读出文件返回给浏览器。图片流量一直占着你自己服务器的带宽。

可以把服务器磁盘想成**家里的硬盘**——只有你自己能读。要给客人看照片，你得
自己把照片拿出来递给客人（服务器转发）。

### 1.2 对象存储（COS）是什么

COS = 腾讯云的对象存储（阿里云叫 OSS，亚马逊叫 S3，概念完全一样）。
它是一个**云上的"大网盘"**，特点是：

1. **有独立域名，浏览器能直接访问**：文件传到 COS 后，得到一个 URL
   `https://liutech-1341692466.cos.ap-chongqing.myqcloud.com/images/xxx.jpg`，
   任何人用浏览器直接打开这个 URL 就能看到图片——**不再经过你的服务器**。
2. **容量和带宽都是云厂商的**：你只管用，服务器磁盘和带宽的压力都消失。
3. **按量付费**：小博客一个月几毛钱到几块钱。

类比：服务器磁盘是"自己家的硬盘"，COS 是"租了云厂商的一个公共网盘"——
别人能直接从这个网盘拿文件，不需要你中转。

### 1.3 术语表（先混个脸熟，后面都会用到）

| 术语 | 一句话解释 | 类比 |
| --- | --- | --- |
| 桶（Bucket） | 一个存储空间，所有文件都装在桶里。名字格式 `名称-APPID`，如 `liutech-1341692466` | 网盘里的一个文件夹 |
| 地域（Region） | 桶建在哪个机房，如 `ap-chongqing`（重庆） | 网盘数据存在哪个城市 |
| 对象键（Key） | 文件在桶里的路径，如 `images/2026/08/05/xxx.jpg` | 网盘里的文件路径 |
| SecretId / SecretKey | 一对钥匙，代码用它证明"我是这个桶的主人"，写入/删除文件必须带 | 网盘账号密码 |
| 公有读 / 私有写 | 读不需要钥匙（任何人都能看），写必须要钥匙 | 网盘文件公开分享，但只有你能改 |
| 默认域名 | 桶自带的访问域名 `bucket.cos.region.myqcloud.com` | 网盘的默认分享链接 |
| 自定义域名 | 你自己的域名（`static.liuxin.chat`）指向桶 | 给网盘起个自己的门牌号 |
| CNAME | DNS 记录类型：把 A 域名指向 B 域名，访问 A 时自动去 B | 门牌号"挂靠"到另一个门牌号 |
| SSL 证书 | 证明 `https://static.liuxin.chat` 是可信的、且数据加密传输 | 门牌号的"官方认证" |
| 边缘节点 | 云厂商在全球各地的缓存服务器，域名绑定后逐批生效 | 快递分拣站，分批铺开 |
| 回源 | 边缘节点没有缓存时，去桶所在机房取文件 | 分拣站没货时去总仓取 |

---

## 第二部分：改造后的整体架构

### 2.1 改造前后对比

```
改造前：
浏览器 → https://liuxin.chat/uploads/images/xxx.jpg → nginx → 后端 → 服务器磁盘

改造后：
浏览器 → https://static.liuxin.chat/images/xxx.jpg → COS 边缘节点 → 重庆机房
        （图片流量不再经过你的服务器）
```

注意 URL 变化：

| | 改造前 | 改造后 |
| --- | --- | --- |
| URL 形态 | `/uploads/images/2026/08/05/xxx.jpg`（相对路径） | `https://static.liuxin.chat/images/2026/08/05/xxx.jpg`（完整 URL） |
| 谁在提供 | nginx + 后端 + 本地磁盘 | COS 直接提供 |
| 数据库存什么 | `/uploads/...` | `https://static.liuxin.chat/...` |

### 2.2 数据库到底存什么（最容易混淆的点）

`images` 表有两列容易混：

| 列 | 存什么 | 例子 |
| --- | --- | --- |
| `file_url` | **generateUrl 的输出**，也就是"浏览器用的访问 URL" | 本地=`/uploads/images/...`，COS=`https://static.liuxin.chat/images/...` |
| `file_path` | **逻辑路径**，与存储后端无关，永远不变 | `images/2026/08/05/xxx.jpg` |

关键认知：**切换存储后端时，`file_path` 一个字都不用改**（它是"文件叫什么名字"），
变的只有 `file_url`（它是"浏览器怎么访问"）。这就是为什么切换 COS 数据库零迁移。

### 2.3 一图看懂整个系统的存储设计

```
                ┌─────────────────────────────────────────┐
                │            业务代码（上传/删除/下载）        │
                │        只认识逻辑路径，不碰文件系统          │
                └──────────────────┬──────────────────────┘
                                   │ 调用
                ┌──────────────────▼──────────────────────┐
                │        FileStorage 接口（抽象层）          │
                │   save / delete / exists / generateUrl / open │
                └──────────────┬──────────────┬───────────┘
                               │              │
                 ┌─────────────▼───┐   ┌──────▼─────────────┐
                 │ LocalFileStorage │   │  CosFileStorage    │
                 │  （本地磁盘）      │   │  （腾讯云 COS）      │
                 └─────────────────┘   └────────────────────┘
                       条件：               条件：
                  cos.enabled=false    cos.enabled=true
                  （默认，不配就是它）   （.env 里配了才启用）
```

两个实现**二选一**，靠一个配置项 `cos.enabled`（环境变量 `COS_ENABLED`）切换。
这就是"后端配置是否使用本地存储"的答案——**已经实现了**。

---

## 第三部分：源码逐层讲解

### 3.1 存储接口 FileStorage（抽象层）

[`FileStorage.java`](../../LiuTech/src/main/java/chat/liuxin/liutech/storage/FileStorage.java)
是整个设计的核心。它规定了一个文件从"保存"到"删除"的所有操作：

| 方法 | 干什么 | 谁在调用 |
| --- | --- | --- |
| `save(data, subPath, filename)` | 存文件，返回逻辑路径 | 图片/文档/音乐上传 |
| `delete(relativePath)` | 删文件 | 删除附件、清理孤立图 |
| `exists(relativePath)` | 文件在不在 | 备用 |
| `generateUrl(relativePath)` | 生成访问 URL | 上传后返回给前端 |
| `open(relativePath)` | 打开文件内容（返回流） | 付费资源下载 |

**为什么需要这个接口？** 因为业务代码只跟接口打交道，不关心文件到底存在磁盘
还是 COS。以后想换阿里云 OSS，再写一个实现类就行，业务代码一行不改。

这就是设计模式里的**策略模式/接口隔离**——"存哪"这件事被隔离在一个地方。

### 3.2 本地实现 LocalFileStorage

[`LocalFileStorage.java`](../../LiuTech/src/main/java/chat/liuxin/liutech/storage/LocalFileStorage.java)
是默认实现，关键两行：

```java
@ConditionalOnProperty(prefix = "cos", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {
```

含义：**当 `cos.enabled=false`（或者根本没配）时，这个类生效**。
`matchIfMissing = true` 表示"配置不存在时也匹配"——所以本地开发什么都不用配。

`save` 方法核心就一行路径生成 + 写磁盘：

```java
String relativePath = StoragePathUtil.generateRelativePath(subPath, originalFilename);
Path fullPath = Paths.get(base).resolve(relativePath);
Files.write(fullPath, data);
```

### 3.3 COS 实现 CosFileStorage（逐行讲解）

[`CosFileStorage.java`](../../LiuTech/src/main/java/chat/liuxin/liutech/storage/CosFileStorage.java)

**类声明**（这个注解是"开关"的机关）：

```java
@ConditionalOnProperty(prefix = "cos", name = "enabled", havingValue = "true")
public class CosFileStorage implements FileStorage {
```

含义：**当 `cos.enabled=true` 时这个类生效**。配合 LocalFileStorage 的注解，
两个类天然互斥——配了 true 就只有一个 COS 实现，不配就只有本地实现。

**初始化**（`@PostConstruct` = 类创建后自动执行一次）：

```java
COSCredentials cred = new BasicCOSCredentials(cosProperties.getSecretId(), cosProperties.getSecretKey());
ClientConfig clientConfig = new ClientConfig(new Region(cosProperties.getRegion()));
cosClient = new COSClient(cred, clientConfig);
```

这三行就是"拿钥匙开门"：腾讯云 SDK 需要一个凭证（SecretId+SecretKey）和
一个地域，然后就能跟 COS 通信了。SDK 的 COSClient 是**线程安全**的，整个
应用只创建一次，所以放在 init 里。

**save**（上传）：

```java
String relativePath = StoragePathUtil.generateRelativePath(subPath, originalFilename);
ObjectMetadata metadata = new ObjectMetadata();
metadata.setContentLength(data.length);
cosClient.putObject(cosProperties.getBucket(), relativePath, new ByteArrayInputStream(data), metadata);
return relativePath;
```

- `generateRelativePath` 生成逻辑路径（和本地实现**完全一样**的规则，这是关键，见 3.6）
- `putObject(桶名, 对象键, 内容流, 元数据)` 就是"把文件放进桶里"
- 返回的逻辑路径存进数据库 `file_path`

**generateUrl**（生成浏览器能用的 URL）：

```java
return cosProperties.getBaseUrl() + "/" + relativePath;
```

`getBaseUrl()` 就是 `https://static.liuxin.chat`（或没配自定义域名时
`https://liutech-1341692466.cos.ap-chongqing.myqcloud.com`）。
**URL 长什么样，只有这一处决定**——以后想换 CDN 域名，改一个配置就行。

**open**（下载用，返回文件内容流）：

```java
COSObject object = cosClient.getObject(cosProperties.getBucket(), relativePath);
return object.getObjectContent();
```

### 3.4 开关原理：@ConditionalOnProperty

这是 Spring Boot 的"条件注册"机制。一句话解释：

> **类上标注条件，条件满足才把这个类注册成 Bean（可被注入的对象）。**

`COS_ENABLED=true` 这个环境变量，Spring 会自动映射成属性 `cos.enabled`，
条件注解检测到这个属性为 true → 注册 CosFileStorage。环境变量名到属性名的
映射规则叫**宽松绑定**（relaxed binding）：`COS_ENABLED` → `cos.enabled`、
`COS_SECRET_ID` → `cos.secret-id`，大小写和连字符都能自动对上。

### 3.5 配置类 CosStorageProperties

[`CosStorageProperties.java`](../../LiuTech/src/main/java/chat/liuxin/liutech/config/CosStorageProperties.java)

```java
@Configuration
@ConfigurationProperties(prefix = "cos")
public class CosStorageProperties {
    private boolean enabled = false;   // COS_ENABLED
    private String secretId;           // COS_SECRET_ID
    private String secretKey;          // COS_SECRET_KEY
    private String region;             // COS_REGION
    private String bucket;             // COS_BUCKET
    private String baseUrl;            // COS_BASE_URL（自定义域名，可选）
}
```

`@ConfigurationProperties(prefix = "cos")` 的意思是：把配置文件中所有
`cos.*` 开头的属性，自动填进这个类的字段里。环境变量 `COS_XXX` 自动对应
`cos.xxx`。

`getBaseUrl()` 的优先级逻辑（核心）：

```java
if (baseUrl != null && !baseUrl.isEmpty()) {
    return baseUrl;   // 配了自定义域名 → 用自定义域名
}
return "https://" + bucket + ".cos." + region + ".myqcloud.com";  // 否则用默认域名
```

### 3.6 StoragePathUtil：为什么数据库零迁移

[`StoragePathUtil.java`](../../LiuTech/src/main/java/chat/liuxin/liutech/storage/StoragePathUtil.java)

```java
public static String generateRelativePath(String subPath, String originalFilename) {
    String fileName = generateFileName(originalFilename);
    String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
    return subPath + "/" + datePath + "/" + fileName;   // images/2026/08/05/1234_uuid.png
}
```

两个存储实现**共用**这个工具类，保证：
- 本地磁盘：`/liuxin/uploads/images/2026/08/05/xxx.png`
- COS 对象键：`images/2026/08/05/xxx.png`

路径结构一模一样。所以迁移时（本地 → COS），对象键直接等于相对路径，
数据库里的 `file_path` 原样可用。**这就是"零迁移"的全部秘密：命名规则统一。**

### 3.7 URL 解析适配：FileUtil

[`FileUtil.java`](../../LiuTech/src/main/java/chat/liuxin/liutech/utils/FileUtil.java)

业务里有很多地方需要"把一个 URL 还原成逻辑路径"（比如删除图片、统计引用次数）。
现在 URL 有三种形态，[`extractRelativePath`](../../LiuTech/src/main/java/chat/liuxin/liutech/utils/FileUtil.java)（第 124 行）全部兼容：

```java
// 形态 1：站内完整 URL  https://www.liuxin.chat/uploads/images/a.jpg
if (fileUrl.startsWith(prefix)) { ... }

// 形态 2：COS 直出 URL  https://static.liuxin.chat/images/a.jpg
String cosBaseUrl = cosStorageProperties.getBaseUrl();
if (cosBaseUrl != null && fileUrl.startsWith(cosBaseUrl + "/")) { ... }

// 形态 3：相对路径 /uploads/images/a.jpg
if (fileUrl.startsWith("/uploads/")) { ... }
```

引用计数、每日对账、图片溯源全都走这一个方法，保证口径一致。

### 3.8 付费资源下载：ResourceDownloadService

[`ResourceDownloadService.java`](../../LiuTech/src/main/java/chat/liuxin/liutech/service/ResourceDownloadService.java)

**为什么资源下载不能直出 COS？** 因为资源是要"买"的（积分制）。如果 URL 直出，
用户绕过购买直接下载。所以下载必须走后端：登录 → 校验是否购买 → 后端从
存储层读流 → 返回给浏览器。

```java
InputStream inputStream = fileStorage.open(relativePath);   // 本地读文件 / COS 拉对象
Resource fileResource = new InputStreamResource(inputStream);
```

`fileStorage.open` 就是前面接口里的第 5 个方法——存储层"读回内容"的能力。

### 3.9 迁移工具：CosMigrateRunner

[`CosMigrateRunner.java`](../../LiuTech/src/main/java/chat/liuxin/liutech/storage/CosMigrateRunner.java)

一次性工具：把本地 uploads 目录**全量上传**到 COS。

```java
@ConditionalOnProperty(prefix = "cos", name = "migrate", havingValue = "true")
public class CosMigrateRunner implements ApplicationRunner {
    // run()：扫描 basePath 下所有文件 → 逐个 putObject
    // 对象键 = 相对路径（与数据库一致）
    // 已存在的对象跳过（幂等，重跑不重复传）
}
```

使用方式：`.env` 设 `COS_MIGRATE=true` → 重启 → 看日志「COS 迁移完成」→ 移除。

为什么不做成页面？因为它是**一次性动作**——把存量搬过去就完成了。正常使用中
新文件直接写 COS，不需要再同步。

### 3.10 pom 依赖

[`LiuTech/pom.xml`](../../LiuTech/pom.xml) 加了一行腾讯云官方 SDK：

```xml
<dependency>
    <groupId>com.qcloud</groupId>
    <artifactId>cos_api</artifactId>
    <version>5.6.227</version>
</dependency>
```

SDK 把 HTTP 请求、签名、错误处理都封装好了，我们只调高级接口（putObject 等）。

---

## 第四部分：网络与域名

> 这是最容易"没学过"的部分，但理解后整个链路就通了。

### 4.1 一次完整的图片访问，经历了什么

你在浏览器打开文章，文章里有一张图 `https://static.liuxin.chat/images/xxx.png`：

```
① 浏览器问 DNS："static.liuxin.chat 在哪？"
② DNS 回答："去 liutech-1341692466.cos.ap-chongqing.myqcloud.com（CNAME）"
③ 浏览器再问："那这个域名在哪？"
④ DNS 回答："IP 183.66.100.45（重庆 COS 机房边缘节点）"
⑤ 浏览器向该 IP 发起 HTTPS 请求，验证证书（static.liuxin.chat）
⑥ 边缘节点有缓存 → 直接返回图片；没有 → 回源到重庆机房取
⑦ 图片显示在页面上
```

**改造前**的路径是：浏览器 → nginx → 后端 → 磁盘。**改造后**浏览器直接连 COS，
你的服务器完全不参与图片请求。这就是"省带宽"的本质。

### 4.2 DNS 与 CNAME

- **DNS**（域名系统）：全世界的"电话簿"，把域名翻译成 IP。
- **A 记录**：域名 → IP 地址（如 `admin.liuxin.chat` → 服务器 IP）。
- **CNAME 记录**：域名 → 另一个域名（如 `static.liuxin.chat` → `liutech-1341692466.cos.ap-chongqing.myqcloud.com`）。

为什么用 CNAME 而不是 A 记录？因为 COS 的 IP 是会变的（腾讯云自己维护节点），
用 CNAME 后，COS 换 IP 时 DNS 自动跟随，你什么都不用改。

本次配置的 CNAME：

```
static.liuxin.chat  CNAME  liutech-1341692466.cos.ap-chongqing.myqcloud.com
```

验证命令：`nslookup static.liuxin.chat`，看到 `Aliases: static.liuxin.chat /
liutech-1341692466.cos.ap-chongqing.myqcloud.com` 就是生效了。

### 4.3 为什么必须 HTTPS 证书

浏览器有个**混合内容（Mixed Content）拦截**规则：HTTPS 页面里不允许加载
HTTP 资源。你的博客是 `https://liuxin.chat`，如果图片是 `http://static...`，
浏览器直接拦截不显示。

所以要给 `static.liuxin.chat` 配 HTTPS 证书，让图片 URL 也是 https。

**证书是什么？** 一份"官方认证"，由证书机构（CA）签发，证明：
1. 这个域名确实属于你（防止冒充）
2. 传输的数据加密（防止被偷看）

**SAN 是什么？** 证书上写的"这个证书覆盖哪些域名"。你的主站证书
`liuxin.chat_bundle.crt` 的 SAN 只有 `liuxin.chat` 和 `www.liuxin.chat`，
**不含 `static.liuxin.chat`**，所以必须给 static 单独申请一张新证书。
（`admin.liuxin.chat` 当年也踩过同样的坑——证书 SAN 不含它，浏览器报警告。）

### 4.4 为什么绑定域名后要等 30 分钟（边缘节点）

COS 在全球有很多**边缘节点**（缓存服务器）。你绑定域名 + 证书后，腾讯云要
把"新配置"同步到所有节点——这是**逐批进行的**。

本次实测过程（18:20 绑定 → 18:34 全量生效，14 分钟）：

```
18:28  节点 A 已是新证书，节点 B 还是旧证书   ← 混着来
18:31  节点 A 返回图片 200，节点 B 返回 400
18:34  两个节点全部 200                      ← 全量生效
```

这就是为什么绑定后"有的电脑能访问、有的不能"——不同节点进度不同，等全量
生效就好。腾讯云文档说约 30 分钟，实际看运气。

---

## 第五部分：腾讯云控制台操作全流程

### 5.1 创建桶

1. 腾讯云控制台 → 搜索"对象存储 COS" → 存储桶列表 → 创建存储桶
2. 名称填 `liutech`，**地域选重庆**（离你的服务器近，上传快）
3. 创建后桶名自动变成 `liutech-1341692466`（名称-APPID 格式，APPID 是账号 ID）
4. 权限先选"私有读写"（后面再改公有读）

### 5.2 创建密钥（SecretId / SecretKey）

1. 控制台 → 访问管理 CAM → 访问密钥 → API 密钥管理
2. 点"新建密钥"，生成一对 SecretId / SecretKey
3. ⚠️ **SecretKey 只在创建时完整显示一次**，一定要当场保存！丢了只能重新建

> 安全提示：正式项目建议建**子账号**密钥（CAM 用户），只授权这个桶的读写权限。
> 密钥只放 `.env`（已 gitignore），**不要写进代码或文档**。

### 5.3 设置公有读（私有写）

存储桶 → 权限管理 → 存储桶访问权限 → 改为"公有读私有写" → 保存。

- 公有读：任何人能用 URL 看图片（博客图片本来就是公开的）
- 私有写：只有带密钥的代码能上传/删除
- 忘保存或没生效的验证：访问一个文件 URL，200 = 生效，403 = 还没生效

### 5.4 申请证书 + 绑定自定义域名

**第一步：申请证书**
1. 控制台 → SSL 证书 → 免费证书 → 申请（单域名）
2. 域名填 `static.liuxin.chat`
3. 按提示做 DNS 验证（加一条 TXT 记录），等签发（几分钟到几小时）
4. 签发后下载，格式选 **Nginx**，得到 `.crt`（证书）和 `.key`（私钥）两个文件

**第二步：COS 绑定域名**
1. 存储桶 → 域名管理 → 自定义源站域名 → 添加域名 `static.liuxin.chat`
2. 保存后点"绑定证书"，上传刚才的 `.crt` 和 `.key`
3. 强制 HTTPS 打开

**第三步：DNS 加 CNAME**
1. 域名解析商（DNSPod 等）→ 添加记录
2. 类型 CNAME，主机记录 `static`，记录值 `liutech-1341692466.cos.ap-chongqing.myqcloud.com`
3. 生效时间一般几分钟

**第四步：验证**
```bash
nslookup static.liuxin.chat        # 能看到 CNAME 目标 = DNS 生效
curl -I https://static.liuxin.chat # 200 = 域名+证书全部就绪
```

---

## 第六部分：部署流程

> 核心顺序：**先迁移，再切换**。顺序不能反——先切开关的话，旧文件还在本地，
> 但系统已经开始读 COS，旧图会 404。

### 6.1 阶段一：迁移（把本地文件搬上 COS）

1. 服务器 `.env` 加（注意 `COS_ENABLED=false` 保持业务不变，只开迁移）：

```bash
COS_ENABLED=false
COS_SECRET_ID=xxx
COS_SECRET_KEY=xxx
COS_REGION=ap-chongqing
COS_BUCKET=liutech-1341692466
COS_MIGRATE=true
```

2. 部署新代码（backend 镜像）+ 重启，看日志：

```
COS 迁移完成: 共 28 个文件，上传 28，跳过（已存在）0，失败 0
```

3. 抽查 COS 上文件可访问（curl 一个对象的 URL 返回 200）

### 6.2 阶段二：切换（业务正式走 COS）

1. `.env` 改成：

```bash
COS_ENABLED=true
COS_MIGRATE=false
COS_BASE_URL=https://static.liuxin.chat
```

2. 重启 backend，日志出现 `COS 存储已启用: bucket=..., region=...`
3. 上传一张图验证：返回 URL 应以 `https://static.liuxin.chat/` 开头，且能访问

### 6.3 验证清单

| 项目 | 怎么验证 | 期望 |
| --- | --- | --- |
| 新图上传 | 后台传图 | URL 是 static 域名 |
| 旧图显示 | 打开文章页 | 图片正常加载（URL 迁移后走 COS） |
| 付费资源 | 购买后下载 | 正常下载（后端转发） |
| 删除图片 | 后台彻底删除 | URL 变 404（COS 对象已删） |
| 健康检查 | `docker compose ps` | 全部 healthy |

---

## 第七部分：错误解决大全

> 全部是本次真实踩过的坑，按"症状 → 原因 → 解决"排列。

### 7.1 上传报 "Bogus input colorspace"

**场景**：从 Word 复制图片粘贴到 TinyMCE 富文本，上传失败。

**原因**：docx 里的图片是带透明通道（RGBA）的 PNG。压缩管线把图片转成 JPEG，
但 JPEG 格式**不支持透明通道**，编码器直接抛错。与 COS 无关，是图片处理的问题。

**解决**：编码前把带透明通道的图片先合成到白色背景（转成 RGB）再转 JPEG。
详见 [`ImageCompressService.encodeJpeg`](../../LiuTech/src/main/java/chat/liuxin/liutech/service/ImageCompressService.java)（第 108 行）。

### 7.2 访问图片返回 403

**场景**：图片 URL 能打开 COS 页面但返回 403（AccessDenied）。

**原因**：桶还是**私有读写**。403 = 对象存在但没授权读。

**解决**：控制台把桶改成"公有读私有写"，然后重试。注意**要点保存**。

### 7.3 curl 报 CRYPT_E_REVOCATION_OFFLINE

**场景**：Windows 本机 curl 访问腾讯云 HTTPS 失败，exit code 35。

**原因**：Windows 的 curl 用 schannel 做 TLS，它会去检查证书吊销列表，
连不上吊销服务器就拒绝。**是 Windows curl 的环境问题，不是你的代码问题。**

**解决**：加 `--ssl-no-revoke` 跳过吊销检查。浏览器不受影响。

### 7.4 curl 报 SEC_E_WRONG_PRINCIPAL（exit 60）

**场景**：绑定证书后访问 `https://static.liuxin.chat` 报主机名与证书不匹配。

**原因**：**边缘节点还没全部生效**——你连到的节点还在用 COS 默认域名的旧证书
（SAN 不包含 static.liuxin.chat）。

**解决**：用 `openssl s_client -connect static.liuxin.chat:443` 看当前证书的
CN 是不是 `static.liuxin.chat`。混着新旧证书 = 正在滚动生效，等 30 分钟内全量
完成。也可以分别 `curl --resolve static.liuxin.chat:443:183.66.100.45` 测不同节点。

### 7.5 同一时刻有的节点 200、有的节点 400

**原因**：和 7.4 一样，边缘节点分批更新，中间态一部分好一部分没好。

**解决**：等。判断"是否全好"的标准：连续两次访问（可强制不同 IP）都 200。

### 7.6 docker compose 报 "no configuration file provided"

**场景**：SSH 到服务器执行 `docker compose ps` 报错。

**原因**：compose 需要在项目目录（有 docker-compose.yml 的地方）执行。
SSH 新会话的工作目录不是 /opt/liutech。

**解决**：`cd /opt/liutech && docker compose ps`。

### 7.7 删除文件 Permission denied

**场景**：ubuntu 用户删 /liuxin/uploads 下的文件失败。

**原因**：文件是容器以 root 身份写入的，owner 是 root。

**解决**：`sudo` 执行删除。

### 7.8 pip 装 coscmd 报 externally-managed-environment

**场景**：`pip3 install coscmd` 被 PEP 668 拦截。

**原因**：新版 Python 保护系统环境，不允许全局 pip 安装。

**解决**：`pip3 install --break-system-packages coscmd`（服务器专用环境可接受），
或建 venv。装完注意脚本在 `~/.local/bin`，可能不在 PATH，用全路径调用。

### 7.9 coscmd delete 卡在 y/N 确认

**原因**：`coscmd delete -r` 删除目录时要求交互确认。

**解决**：加 `-f` 强制：`coscmd delete -f -r images/2026/01/`。

### 7.10 docker logs 看不到应用日志

**场景**：`docker logs liutech-backend` 只有几行，应用日志"消失"。

**原因**：日志配置写文件（`/app/logs/liutech-backend.log`），不写 stdout。

**解决**：`docker exec liutech-backend sh -c "grep COS /app/logs/*.log"`。

### 7.11 中文乱码（curl / python / mysql）

**场景**：curl 输出中文乱码、python 解析 JSON 报 UTF-8 错误、mysql 输出 ???。

**原因**：Windows 控制台默认 GBK 编码。

**解决**：
- python：`PYTHONIOENCODING=utf-8`
- mysql：连接参数加 `--default-character-set=utf8mb4`
- grep 匹配中文：确保终端 UTF-8

### 7.12 备份后确认无碍再删

**原则**：所有破坏性操作（删文件、删记录、改 URL）之前，先备份。
本次做法：`mysqldump` 备份相关表到 `/tmp/cos-migration-backup.sql`，确认稳定后再清理。

---

## 第八部分：日常运维 Q&A

**Q：怎么切回本地存储？**
`.env` 里 `COS_ENABLED=false`，重启。新文件回本地磁盘；COS 上的旧文件
URL 不变，照常访问（存在 COS 里又不会消失）。

**Q：切换后数据库要迁移吗？**
不用。`file_path` 是逻辑路径，与存储无关。`file_url` 是 generateUrl 的输出，
切换后新数据自然用新形态。

**Q：图片的引用计数怎么办？**
引用计数按 URL 归一化后的逻辑路径统计，`FileUtil.normalizeToRelativePath`
兼容三种 URL 形态，COS 不影响。每日 02:55 对账兜底修正。

**Q：孤立图片清理会误删吗？**
不会。管理端孤立清理排除最近 24 小时上传的图（保护"粘贴了还没保存"的场景）。

**Q：COS 费用怎么看？**
控制台 → 费用中心。图片量小的话一个月几块钱。

**Q：服务器本地那 5 个文件还要吗？**
不需要了（COS 有副本，文章 URL 已指向 COS）。保留它们只是"兜底"——
万一 COS 域名出问题，还能用 nginx 代理本地看。建议保留一阵子，稳定后删。

**Q：以后要加 CDN 加速怎么办？**
`CosStorageProperties.getBaseUrl()` 是 URL 形态的唯一决定点：
配 CDN 域名 → 填 `COS_BASE_URL=https://cdn.liuxin.chat` → 重启。数据库零改动。

---

## 附：本次改动文件清单

| 文件 | 作用 |
| --- | --- |
| `LiuTech/pom.xml` | 加 cos_api SDK 依赖 |
| `storage/FileStorage.java` | 存储接口（新增 open 方法） |
| `storage/LocalFileStorage.java` | 本地实现（条件注册） |
| `storage/CosFileStorage.java` | **COS 实现（新增）** |
| `storage/StoragePathUtil.java` | 路径生成（两实现共用，新增） |
| `storage/CosMigrateRunner.java` | 一次性迁移工具（新增） |
| `config/CosStorageProperties.java` | COS 配置类（新增） |
| `utils/FileUtil.java` | URL 解析兼容 COS 形态 |
| `service/ResourceDownloadService.java` | 下载改走 FileStorage.open |
| `.env.example` / `docker-compose.yml` | COS 环境变量透传 |
| `Docs/架构/后端/图片管理/存储抽象.md` | 架构文档更新 |
