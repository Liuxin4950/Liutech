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
        var required = java.util.Map.of(
                "ai_chat_message", java.util.Set.of("conversation_id", "user_id", "seq_no"),
                "ai_user_state", java.util.Set.of("user_id", "purged", "updated_at"));
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
