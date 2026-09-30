# 数据库版本迁移

> `../sql.sql` 是两个数据库唯一的完整初始化快照。本目录保留发布过的增量迁移，由独立 Flyway Maven 工程显式执行；应用启动不执行 DDL。

## 当前版本

| 数据库 | 迁移目录 | 最新版本 | 内容 |
| --- | --- | --- | --- |
| liutech | main | 1 | user_purge_tasks，持久化跨服务清理任务 |
| liutech_ai | ai | 2 | ai_user_state 永久清理状态；会话内 seq_no 唯一索引 |

`baseline/main-v0.sql` 与 `ai-v0.sql` 是 4c3578d 冻结的结构测试夹具，无业务数据或建库语句；不是当前初始化入口，不由发布工具扫描执行。CI 从该旧结构升级后，与当前快照比较字段类型/默认值、索引、外键，防止只改新库快照而遗漏旧库升级。

工具版本通过根 POM 的 Spring Boot BOM 统一管理。`pom.xml` 不在业务 Maven 模块列表中，也不绑定任何默认生命周期；普通构建不连接生产数据库。

## 规则

- 新库先执行 [sql.sql](../sql.sql)，再接入迁移台账。旧库只执行相应目录的增量，不能重放完整初始化脚本。
- 每个库拥有独立 flyway_schema_history，记录版本、校验和、执行结果。两个目录不能指向同一个数据库。
- 发布过的 V*.sql 永久保留且不可修改；后续改动新增版本，同时更新初始化快照。未发布的本地草稿可以调整。
- baselineVersion 固定为 0，显式 baseline 后仍会执行 V1 及以后版本。baselineOnMigrate=false，禁止把任意非空库自动当作已核验旧库；cleanDisabled=true。
- MySQL DDL 可能隐式提交，不能依靠外围事务回滚迁移。先备份、核验，失败后检查实际结构，不自动 repair 或清库。
- 应用账号只保留各自库的 SELECT/INSERT/UPDATE/DELETE；发布使用独立迁移账号。凭据通过环境变量注入，不写 SQL、POM、仓库或命令行。

Flyway baseline 只标记已有结构，不校验整份历史业务结构；首次接入前必须核对旧库。[官方 baseline 说明](https://documentation.red-gate.com/flyway/reference/commands/baseline)。

## 执行

先将 JAVA_HOME 指向 JDK 21，通过会话环境设置 FLYWAY_URL、FLYWAY_USER、FLYWAY_PASSWORD。JDBC URL 必须包含项目要求的 allowPublicKeyRetrieval=true，指向本次要升级的一个库。

生产数据库不暴露公网端口。可在发布机运行工具，或使用经确认的 SSH 隧道：先查看 mysql 容器的当前私网 IP，再将本机端口转发到该 IP 的 3306；不要为迁移开放公网 3306，也不要假设容器 IP 永久固定。

仓库根目录执行：

```powershell
# 仅首次接入、已核验且没有台账的库使用 -Baseline
./scripts/migrate.ps1 -Database main -Baseline
# 改会话中的 FLYWAY_URL，指向 liutech_ai
./scripts/migrate.ps1 -Database ai -Baseline

# 已接入台账的后续发布，分别指定各库 URL，无需再次 baseline
./scripts/migrate.ps1 -Database main
./scripts/migrate.ps1 -Database ai
```

脚本读取环境凭据并依次执行 migrate、validate、info；任一步失败就以错误退出，不能继续启动新版应用。窗口包含空格或 JDBC URL 含 `&` 时也不需要把 URL 拼入 Maven 命令参数。

## 本轮发布顺序

1. 备份两库，核验现有业务字段与初始化快照，保留已有备份表，不自动删除。
2. 核验 AI 序号无重复：按 conversation_id、seq_no 分组并筛选 COUNT(*)>1。V2 会在重复存在时失败，不能通过删除消息来绕过校验。
3. 分别运行主库 V1、AI 库 V1/V2，validate 通过后再启动新版应用。初始化快照已包含同样结构，V2 可识别已有唯一索引并空操作。
4. 优先发布 AI，再发布 backend。旧 `/data`、`/purge` 接口保留普通清空语义；新 backend 只调用 `/permanent-data` 并校验 `permanentlyPurged=true` 与 userId。新版 AI 未就绪时任务保留并重试。
5. 验证应用健康、单条/批量删除、user_purge_tasks 的待处理数量与失败次数，以及 AI 无法用旧身份重新建立已清理用户的数据。
6. 新分片目录按 userId 隔离，在途旧上传需要重新开始；旧 tmp/{uploadId} 不作为无属主兼容入口读取。

DatabaseSchemaVerifier 在启动时检查关键表/字段，AI 还检查序号唯一索引。缺迁移时明确失败，避免业务运行一段时间才发现底层 SQL 不兼容。

回退应用时保留新表、索引和任务台账；不能删除待处理清理任务。需要回退 backend 时，应先处理或保留能继续消费任务的兼容版本，不能把“接口返回成功”当作关联数据已经全部清完。

## 清理任务

主库删除与 user_purge_tasks 入队在同一个短事务中提交。主库失败时任务回滚，AI 尚未收到清理请求。成功时主库用户已删除，接口文案明确关联清理任务已提交。

UserPurgeTaskService 每批最多处理 20 个任务，使用独立调度线程。60 秒租约在 HTTP 之外的原子 SQL 中领取，成功按租约 token 确认；失败指数退避，最长一小时后重试。进程退出后租约到期可再次处理，不能用内存回调代替持久化任务。

AI 永久清理先锁定 ai_user_state，再删除该用户会话。createConversation 和消息保存争用相同用户状态锁；永久清理后保留 ID 状态，旧认证缓存与在途回复不能重新写入。用户主动“清空记忆”不标记永久清理。

运营核验可查询任务的 completed_at、attempts、next_attempt_at、last_error；last_error 只存错误类别，不存响应正文或凭据。已完成任务保留作为执行台账，ID 不得在两库之间手工复用。

## 验证基线

本轮在独立本地 MySQL 8.0.41 中验证了两库 baseline/migrate/validate、重复执行、从当前初始化快照接入台账，以及校验和漂移拒绝。代码回归覆盖真实 Mapper/事务代理和完整 Spring 上下文；CI 的两个后端测试任务各使用独立 MySQL 服务运行同一组回归。

2026-09-30 对生产仅进行了只读核验：应用账号权限为各自库的 CRUD，现有业务表字段名与快照一致，AI 重复序号组和消息归属不匹配数均为 0。生产还未执行本轮迁移，未发布本轮镜像。
