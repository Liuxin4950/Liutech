package chat.liuxin.ai.service;

import chat.liuxin.ai.mapper.AiChatMessageMapper;
import chat.liuxin.ai.mapper.AiConversationMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.server.ResponseStatusException;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "LIUTECH_TEST_MYSQL_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemoryMysqlTest {
    private MysqlFixture db;
    private MemoryService memory;

    @BeforeAll void setup() throws Exception {
        db = new MysqlFixture();
        memory = db.transactional(new MemoryService(db.session.getMapper(AiChatMessageMapper.class),
                db.session.getMapper(AiConversationMapper.class), db.session.getMapper(chat.liuxin.ai.mapper.AiUserStateMapper.class)));
    }
    @AfterAll void cleanup() throws Exception { if (db != null) db.close(); }

    @Test void concurrentMessagesHaveUniqueSequenceAndExactCount() throws Exception {
        Long id = memory.createConversation("owner", "concurrent");
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(12)) {
            var futures = IntStream.range(0, 24).mapToObj(i -> pool.submit(() -> {
                start.await();
                memory.saveUserMessage("owner", id, "message-" + i, "test", null);
                return null;
            })).toList();
            start.countDown();
            for (var future : futures) future.get(30, TimeUnit.SECONDS);
        }
        assertEquals(24, memory.getConversationOwnedByUser("owner", id).getMessageCount());
        assertEquals(24, db.jdbc.queryForObject(
                "SELECT COUNT(DISTINCT seq_no) FROM ai_chat_message WHERE conversation_id=?", Integer.class, id));
        assertEquals(24, db.jdbc.queryForObject(
                "SELECT MAX(seq_no) FROM ai_chat_message WHERE conversation_id=?", Integer.class, id));
        var page = memory.listMessagesByConversation("owner", id, 2, 5);
        assertEquals(5, page.size());
        assertEquals(15, page.getFirst().getSeqNo());
        var tail = memory.listLastMessagesByConversation("owner", id, 3);
        assertEquals(3, tail.size());
        assertEquals(22, tail.getFirst().getSeqNo());
        memory.renameConversation("owner", id, "renamed");
        assertEquals(24, memory.getConversationOwnedByUser("owner", id).getMessageCount());
    }

    @Test void authorizationFailureCannotChangeVictimIncludingErrorPath() {
        Long id = memory.createConversation("victim", "protected");
        assertThrows(ResponseStatusException.class,
                () -> memory.listLastMessagesAsPromptMessages("attacker", id, 0));
        assertThrows(ResponseStatusException.class,
                () -> memory.saveUserMessage("attacker", id, "input", "test", null));
        new ChatServiceHelper(null, memory, null, null).saveErrorIfNeeded(false, "attacker", id, "test");
        assertEquals(0, memory.getConversationOwnedByUser("victim", id).getMessageCount());
        assertEquals(0, db.jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_chat_message WHERE conversation_id=?", Integer.class, id));
    }

    @Test void conversationsUseStableBoundedPagination() {
        for (int i = 0; i < 7; i++) memory.createConversation("pages", "title" + i);
        var first = memory.listConversations("pages", null, 1, 3);
        var second = memory.listConversations("pages", null, 2, 3);
        assertEquals(3, first.size());
        assertEquals(3, second.size());
        assertTrue(first.stream().noneMatch(a -> second.stream().anyMatch(b -> a.getId().equals(b.getId()))));
    }

    @Test void permanentPurgeIsIdempotentAndOldRequestsCannotRecreateData() {
        Long id = memory.createConversation("purged", "old");
        memory.saveUserMessage("purged", id, "body", "test", null);
        var result = memory.purgeUserData("purged");
        assertEquals(1, result.conversationsDeleted());
        assertEquals(1, result.messagesDeleted());
        assertEquals(0, memory.purgeUserData("purged").conversationsDeleted());
        assertThrows(ResponseStatusException.class, () -> memory.createConversation("purged", "old token"));
        assertThrows(ResponseStatusException.class,
                () -> memory.saveAssistantMessage("purged", id, "late reply", "test", 1, null));
        assertEquals(0, db.jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation WHERE user_id='purged'", Integer.class));
    }

    @Test void userInitiatedClearKeepsAccountActive() {
        memory.createConversation("clear", "old");
        memory.clearAllMemory("clear");
        assertNotNull(memory.createConversation("clear", "new"));
    }
}
