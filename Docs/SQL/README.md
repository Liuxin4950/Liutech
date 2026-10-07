# 数据库 SQL 入口

[sql.sql](sql.sql) 是唯一维护的、当前最新的完整初始化脚本。新环境执行这一份文件即可创建 `liutech` 与 `liutech_ai` 两库，包含全部表、索引、外键和默认基础数据，也包含评论区 AI 与用户永久清理结构。

完整脚本随代码和数据库结构变更同步更新，不拆成需要手工拼接的初始化片段，不复制生产账号、文章、评论、会话或密钥。

| 路径 | 用途 |
| --- | --- |
| `sql.sql` | 当前完整的新环境初始化；Docker MySQL 首次创建数据卷时也加载此文件 |
| `migrations/main/`、`migrations/ai/` | 已有数据库的增量升级；发布过的脚本长期保留 |
| `migrations/baseline/` | 冻结的旧结构测试夹具，用于迁移回归 |

已有环境升级使用[数据库版本迁移说明](migrations/README.md)。`CREATE TABLE IF NOT EXISTS` 不会修改已有表，不能用重放完整初始化脚本代替增量迁移。
