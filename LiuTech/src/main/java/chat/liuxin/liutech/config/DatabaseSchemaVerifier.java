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
        var required = java.util.Map.ofEntries(
                java.util.Map.entry("users", java.util.Set.of("id", "points", "version", "deleted_at")),
                java.util.Map.entry("user_purge_tasks", java.util.Set.of("user_id", "attempts", "next_attempt_at", "lease_token", "completed_at", "last_error")),
                java.util.Map.entry("comments", java.util.Set.of("id", "user_id", "bot_id", "community_task_id", "root_event_id")),
                java.util.Map.entry("community_bots", java.util.Set.of("id", "enabled", "system_prompt", "version")),
                java.util.Map.entry("community_knowledge", java.util.Set.of("id", "bot_id", "version")),
                java.util.Map.entry("community_settings", java.util.Set.of("id", "enabled", "site_daily_task_limit", "max_chain_comments", "version")),
                java.util.Map.entry("community_post_state", java.util.Set.of("post_id", "enabled", "first_public_seen", "version")),
                java.util.Map.entry("community_chains", java.util.Set.of("root_event_id", "post_id", "emitted")),
                java.util.Map.entry("community_events", java.util.Set.of("id", "event_key", "lease_token", "lease_until", "acknowledged_at")),
                java.util.Map.entry("community_publications", java.util.Set.of("task_id", "bot_id", "post_id", "comment_id")),
                java.util.Map.entry("community_attempts", java.util.Set.of("task_id", "attempt", "allowed", "reason")));
        try (var connection = dataSource.getConnection()) {
            for (var entry : required.entrySet()) {
                var columns = new java.util.HashSet<String>();
                try (var rows = connection.getMetaData().getColumns(connection.getCatalog(), null, entry.getKey(), null)) {
                    while (rows.next()) {
                        if (entry.getKey().equalsIgnoreCase(rows.getString("TABLE_NAME"))) {
                            columns.add(rows.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ROOT));
                            if ("comments".equals(entry.getKey()) && "user_id".equalsIgnoreCase(rows.getString("COLUMN_NAME"))
                                    && rows.getInt("NULLABLE") == java.sql.DatabaseMetaData.columnNoNulls) {
                                throw new IllegalStateException("数据库结构未就绪：机器人评论需要 comments.user_id 可空，请先执行主库 V2 迁移");
                            }
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
