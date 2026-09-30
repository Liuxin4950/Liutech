package chat.liuxin.liutech.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;

/** 应用不执行 DDL；缺关键字段时启动失败，避免升级后定时任务持续报错。 */
@Component
@RequiredArgsConstructor
public class DatabaseSchemaVerifier {
    private final DataSource dataSource;

    @PostConstruct
    public void verify() throws java.sql.SQLException {
        var required = java.util.Map.of(
                "users", java.util.Set.of("id", "points", "version", "deleted_at"),
                "user_purge_tasks", java.util.Set.of("user_id", "attempts", "next_attempt_at", "lease_token", "completed_at", "last_error"));
        try (var connection = dataSource.getConnection()) {
            for (var entry : required.entrySet()) {
                var columns = new java.util.HashSet<String>();
                try (var rows = connection.getMetaData().getColumns(connection.getCatalog(), null, entry.getKey(), null)) {
                    while (rows.next()) {
                        if (entry.getKey().equalsIgnoreCase(rows.getString("TABLE_NAME"))) {
                            columns.add(rows.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ROOT));
                        }
                    }
                }
                if (!columns.containsAll(entry.getValue())) {
                    throw new IllegalStateException("数据库结构未就绪：" + entry.getKey() + "，请先按 Docs/SQL/migrations/README.md 完成升级");
                }
            }
        }
    }
}
