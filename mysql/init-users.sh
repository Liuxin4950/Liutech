#!/bin/sh
set -eu

if [ -z "${DB_APP_PASSWORD:-}" ] || [ -z "${DB_AI_APP_PASSWORD:-}" ]; then
  echo "DB_APP_PASSWORD and DB_AI_APP_PASSWORD are required" >&2
  exit 1
fi

escape_sql() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e "s/'/''/g"
}

blog_password=$(escape_sql "$DB_APP_PASSWORD")
ai_password=$(escape_sql "$DB_AI_APP_PASSWORD")

MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot <<EOSQL
CREATE USER IF NOT EXISTS 'liutech_app'@'%' IDENTIFIED BY '${blog_password}';
ALTER USER 'liutech_app'@'%' IDENTIFIED BY '${blog_password}';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'liutech_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON liutech.* TO 'liutech_app'@'%';

CREATE USER IF NOT EXISTS 'liutech_ai_app'@'%' IDENTIFIED BY '${ai_password}';
ALTER USER 'liutech_ai_app'@'%' IDENTIFIED BY '${ai_password}';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'liutech_ai_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON liutech_ai.* TO 'liutech_ai_app'@'%';
FLUSH PRIVILEGES;
EOSQL

echo "LiuTech application database users provisioned"
