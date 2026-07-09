#!/bin/bash
# 创建非 root 应用账户（最小权限），由 MySQL docker-entrypoint-initdb.d 首次初始化时执行。
# 仅授予各自库的 SELECT/INSERT/UPDATE/DELETE，不授 DDL（表结构变更由 root 手动执行）。
# 现有 mysql_data 卷已初始化时不会重跑此脚本，需按 doc/团队反馈/04-运维工程师.md 的迁移命令手动创建。

APP_PASS="${DB_APP_PASSWORD:-$DB_PASSWORD}"
AI_PASS="${DB_AI_APP_PASSWORD:-$DB_PASSWORD}"

mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<EOSQL
  CREATE USER IF NOT EXISTS 'liutech_app'@'%' IDENTIFIED BY '${APP_PASS}';
  GRANT SELECT, INSERT, UPDATE, DELETE ON liutech.* TO 'liutech_app'@'%';
  CREATE USER IF NOT EXISTS 'liutech_ai_app'@'%' IDENTIFIED BY '${AI_PASS}';
  GRANT SELECT, INSERT, UPDATE, DELETE ON liutech_ai.* TO 'liutech_ai_app'@'%';
  FLUSH PRIVILEGES;
EOSQL
