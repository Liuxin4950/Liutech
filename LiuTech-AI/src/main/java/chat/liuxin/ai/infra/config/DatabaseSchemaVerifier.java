package chat.liuxin.ai.infra.config;

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
                java.util.Map.entry("ai_chat_message", java.util.Set.of("conversation_id", "user_id", "seq_no")),
                java.util.Map.entry("ai_user_state", java.util.Set.of("user_id", "purged", "updated_at")),
                java.util.Map.entry("ai_community_inbox", java.util.Set.of("event_id", "event_json")),
                java.util.Map.entry("ai_community_task", java.util.Set.of("id", "event_id", "status", "memory_epoch", "decision_json", "context_version", "lease_until")),
                java.util.Map.entry("ai_community_worker", java.util.Set.of("id", "lease_token", "lease_until")),
                java.util.Map.entry("ai_community_run", java.util.Set.of("id", "task_id", "status", "result_json")),
                java.util.Map.entry("ai_community_role_state", java.util.Set.of("bot_id", "memory_epoch")),
                java.util.Map.entry("ai_community_memory", java.util.Set.of("id", "task_id", "bot_id", "source_post_id", "source_comment_id", "summary")),
                java.util.Map.entry("ai_community_memory_participant", java.util.Set.of("memory_id", "user_id")),
                java.util.Map.entry("ai_community_user_state", java.util.Set.of("user_id", "purged")),
                java.util.Map.entry("ai_community_memory_source", java.util.Set.of("memory_id", "source_comment_id")));
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
            var indexes = new java.util.HashMap<String, java.util.SortedMap<Integer, String>>();
            try (var rows = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, "ai_chat_message", true, false)) {
                while (rows.next()) {
                    String name = rows.getString("INDEX_NAME");
                    String column = rows.getString("COLUMN_NAME");
                    if (name != null && column != null) {
                        indexes.computeIfAbsent(name, ignored -> new java.util.TreeMap<>())
                                .put(rows.getInt("ORDINAL_POSITION"), column.toLowerCase(java.util.Locale.ROOT));
                    }
                }
            }
            if (indexes.values().stream().noneMatch(columns -> new java.util.ArrayList<>(columns.values())
                    .equals(java.util.List.of("conversation_id", "seq_no")))) {
                throw new IllegalStateException("数据库结构未就绪：缺会话序号唯一索引，请先执行 AI V2 迁移");
            }
        }
    }
}
