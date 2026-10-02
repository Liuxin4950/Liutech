package chat.liuxin.ai.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 校验 MyBatis 实际绑定的锁查询与原子更新，防止 SQL/缓存选项回退。 */
class AiConversationMapperTest {
    @Test
    void lockSelectAlwaysUsesDatabaseAndBindsConversationId() {
        MybatisConfiguration configuration = configuration();
        var statement = configuration.getMappedStatement(AiConversationMapper.class.getName() + ".lockById");
        var boundSql = statement.getBoundSql(Map.of("conversationId", 99L));
        assertEquals("SELECT * FROM ai_conversation WHERE id = ? FOR UPDATE", boundSql.getSql());
        assertEquals("conversationId", boundSql.getParameterMappings().getFirst().getProperty());
        assertFalse(statement.isUseCache());
        assertTrue(statement.isFlushCacheRequired());
    }

    @Test
    void countUpdateIsAtomicAndOnlyTouchesMessageStatistics() {
        var statement = configuration().getMappedStatement(AiConversationMapper.class.getName() + ".incrementMessageCount");
        var boundSql = statement.getBoundSql(Map.of("conversationId", 99L, "messageTime", LocalDateTime.now()));
        assertEquals("UPDATE ai_conversation SET message_count = COALESCE(message_count, 0) + 1, " +
                "last_message_at = ?, updated_at = ? WHERE id = ?", boundSql.getSql());
        assertFalse(boundSql.getSql().contains("title"));
        assertFalse(boundSql.getSql().contains("status"));
        assertEquals(java.util.List.of("messageTime", "messageTime", "conversationId"),
                boundSql.getParameterMappings().stream().map(mapping -> mapping.getProperty()).toList());
    }

    private MybatisConfiguration configuration() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(AiConversationMapper.class);
        return configuration;
    }
}
