package chat.liuxin.ai.service;

import chat.liuxin.ai.entity.AiChatMessage;
import chat.liuxin.ai.entity.AiConversation;
import chat.liuxin.ai.mapper.AiChatMessageMapper;
import chat.liuxin.ai.mapper.AiConversationMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 用会话锁替身验证 Spring 事务边界和竞争时序；不代替真实 MySQL 的锁/隔离集成测试。
 */
class MemoryServiceConcurrencyTest {
    private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final Map<Long, ArrayList<AiChatMessage>> saved = new ConcurrentHashMap<>();
    private final Map<Long, AtomicInteger> counts = new ConcurrentHashMap<>();
    private final AtomicBoolean pauseFirstSequence = new AtomicBoolean();
    private final CountDownLatch firstSequenceRead = new CountDownLatch(1);
    private final CountDownLatch releaseFirstWrite = new CountDownLatch(1);
    private final CountDownLatch contendedLock = new CountDownLatch(1);
    private final TestTransactionManager transactions = new TestTransactionManager();
    private MemoryService memory;

    @BeforeEach
    void setUp() {
        var builder = new MapperBuilderAssistant(new MybatisConfiguration(), "memory-concurrency-test");
        TableInfoHelper.initTableInfo(builder, AiChatMessage.class);
        TableInfoHelper.initTableInfo(builder, AiConversation.class);
        AiChatMessageMapper messages = mock(AiChatMessageMapper.class);
        AiConversationMapper conversations = mock(AiConversationMapper.class);
        when(conversations.lockById(anyLong())).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            Long id = invocation.getArgument(0);
            ReentrantLock lock = locks.computeIfAbsent(id, ignored -> new ReentrantLock());
            if (!lock.tryLock()) {
                contendedLock.countDown();
                lock.lock();
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) { lock.unlock(); }
            });
            AiConversation conversation = new AiConversation();
            conversation.setId(id);
            conversation.setUserId("42");
            return conversation;
        });
        when(messages.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<AiChatMessage> query = invocation.getArgument(0);
            assertTrue(query.getSqlSegment().endsWith("LIMIT 1 FOR UPDATE"));
            Long id = (Long) query.getParamNameValuePairs().values().iterator().next();
            if (id == 99L && pauseFirstSequence.compareAndSet(true, false)) {
                firstSequenceRead.countDown();
                assertTrue(releaseFirstWrite.await(5, TimeUnit.SECONDS));
            }
            ArrayList<AiChatMessage> rows = saved.computeIfAbsent(id, ignored -> new ArrayList<>());
            return rows.isEmpty() ? null : rows.getLast();
        });
        when(messages.insert(any(AiChatMessage.class))).thenAnswer(invocation -> {
            AiChatMessage row = invocation.getArgument(0);
            if ("插入失败".equals(row.getContent())) throw new IllegalStateException("插入失败");
            saved.computeIfAbsent(row.getConversationId(), ignored -> new ArrayList<>()).add(row);
            return 1;
        });
        when(conversations.incrementMessageCount(anyLong(), any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            counts.computeIfAbsent(id, ignored -> new AtomicInteger()).incrementAndGet();
            return 1;
        });
        var proxy = new ProxyFactory(new MemoryService(messages, conversations));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        memory = (MemoryService) proxy.getProxy();
    }

    @Test
    void sameConversationWaitsForCommitWhileOtherConversationCanWrite() throws Exception {
        pauseFirstSequence.set(true);
        var executor = Executors.newFixedThreadPool(3);
        try {
            var first = executor.submit(() -> memory.saveUserMessage("42", 99L, "问题", "model", null));
            assertTrue(firstSequenceRead.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> memory.saveAssistantMessage("42", 99L, "回答", "model", 1, null));
            assertTrue(contendedLock.await(5, TimeUnit.SECONDS));
            var independent = executor.submit(() -> memory.saveUserMessage("42", 100L, "另一会话", "model", null));
            independent.get(5, TimeUnit.SECONDS);
            assertFalse(second.isDone());
            assertEquals(1, transactions.commits.get());

            releaseFirstWrite.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertEquals(java.util.List.of(1, 2), saved.get(99L).stream().map(AiChatMessage::getSeqNo).toList());
            assertEquals(java.util.List.of("user", "assistant"), saved.get(99L).stream().map(AiChatMessage::getRole).toList());
            assertEquals(2, counts.get(99L).get());
            assertEquals(1, counts.get(100L).get());
            assertEquals(3, transactions.commits.get());
            assertEquals(0, transactions.rollbacks.get());
        } finally {
            releaseFirstWrite.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void failedInsertRollsBackAndReleasesLockWithoutIncrementingCount() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> memory.saveUserMessage("42", 99L, "插入失败", "model", null));
        assertEquals(1, transactions.rollbacks.get());
        assertFalse(locks.get(99L).isLocked());
        assertFalse(counts.containsKey(99L));
        try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> memory.saveUserMessage("42", 99L, "重试", "model", null)).get(5, TimeUnit.SECONDS);
        }
        assertEquals(1, saved.get(99L).getFirst().getSeqNo());
        assertEquals(1, counts.get(99L).get());
        assertEquals(1, transactions.commits.get());
    }

    private static class TestTransactionManager extends AbstractPlatformTransactionManager {
        private final AtomicInteger commits = new AtomicInteger();
        private final AtomicInteger rollbacks = new AtomicInteger();

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) { commits.incrementAndGet(); }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks.incrementAndGet(); }
    }
}
