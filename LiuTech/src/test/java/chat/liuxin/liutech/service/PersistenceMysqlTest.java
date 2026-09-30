package chat.liuxin.liutech.service;

import chat.liuxin.liutech.config.CacheConfig;
import chat.liuxin.liutech.mapper.*;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.req.UpdateProfileReq;
import chat.liuxin.liutech.utils.UserUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "LIUTECH_TEST_MYSQL_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersistenceMysqlTest {
    private MysqlFixture db;
    private UserMapper users;
    private PointsService points;

    @BeforeAll void setup() throws Exception {
        db = new MysqlFixture("mapper/UserMapper.xml", "mapper/PointsTransactionMapper.xml",
                "mapper/PostLikesMapper.xml", "mapper/PostFavoritesMapper.xml", "mapper/PostsMapper.xml",
                "mapper/ResourcesMapper.xml", "mapper/PostAttachmentsMapper.xml");
        users = db.session.getMapper(UserMapper.class);
        points = db.transactional(new PointsService(users, db.session.getMapper(PointsTransactionMapper.class)));
    }
    @AfterAll void cleanup() throws Exception { if (db != null) db.close(); }

    private void createUser(long id, String name) {
        db.jdbc.update("INSERT INTO users(id,username,email,password_hash) VALUES(?,?,?,'hash')", id, name, name + "@test.local");
    }

    @Test void concurrentAwardsAndDeductionsKeepLedgerBalancesExact() throws Exception {
        createUser(1, "points");
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(12)) {
            var futures = IntStream.range(0, 24).mapToObj(i -> pool.submit(() -> {
                start.await();
                points.addPoints(1L, BigDecimal.TEN, "test", "test", null, "award");
                return null;
            })).toList();
            start.countDown();
            for (var future : futures) future.get(30, TimeUnit.SECONDS);
        }
        assertEquals(0, new BigDecimal("240").compareTo(users.selectById(1L).getPoints()));
        var balances = db.jdbc.queryForList("SELECT balance_after FROM points_transactions WHERE user_id=1 ORDER BY id", BigDecimal.class);
        assertEquals(24, balances.size());
        for (int i = 0; i < balances.size(); i++) assertEquals(0, BigDecimal.valueOf((i + 1) * 10L).compareTo(balances.get(i)));
        points.deductPoints(1L, new BigDecimal("240"), "test", null, "consume");
        assertEquals(0, users.selectById(1L).getPoints().signum());
        assertThrows(RuntimeException.class, () -> points.deductPoints(1L, BigDecimal.ONE, "test", null, "insufficient"));
        assertEquals(25, db.jdbc.queryForObject("SELECT COUNT(*) FROM points_transactions WHERE user_id=1", Integer.class));
    }

    @Test void staleProfileCannotOverwriteAwardOrPassword() {
        createUser(2, "profile");
        var utils = new UserUtils(users, new CacheConfig().cacheManager());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("profile", null, List.of()));
        try {
            Users old = utils.getCurrentUser();
            points.addPoints(2L, BigDecimal.TEN, "test", "test", null, "award");
            var staleUtils = mock(UserUtils.class);
            when(staleUtils.getCurrentUserId()).thenReturn(2L);
            old.setPoints(BigDecimal.ZERO);
            var service = db.transactional(new UserProfileService(users, null, staleUtils,
                    null, null, null, null, null, null));
            var req = new UpdateProfileReq();
            req.setNickname("updated");
            var result = service.updateProfile(req);
            assertEquals("updated", result.getNickname());
            assertEquals(0, BigDecimal.TEN.compareTo(users.selectById(2L).getPoints()));
            assertEquals("hash", users.selectById(2L).getPasswordHash());
            assertEquals(1, users.selectById(2L).getVersion());
            assertEquals(0, BigDecimal.TEN.compareTo(utils.getCurrentUser().getPoints()));
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test void restoredInteractionsMatchStateAndCountsAndExcludeDeletedUsers() {
        createUser(3, "active");
        createUser(4, "deleted");
        db.jdbc.update("UPDATE users SET deleted_at=NOW() WHERE id=4");
        db.jdbc.update("INSERT INTO categories(id,name) VALUES(1,'test')");
        db.jdbc.update("INSERT INTO posts(id,title,content,category_id,author_id,deleted_at) VALUES(1,'post','body',1,3,NOW())");
        db.jdbc.update("INSERT INTO post_likes(user_id,post_id,is_like,deleted_at) VALUES(3,1,1,NOW()),(4,1,1,NOW())");
        db.jdbc.update("INSERT INTO post_favorites(user_id,post_id,is_favorite,deleted_at) VALUES(3,1,1,NOW())");
        var posts = db.session.getMapper(PostsMapper.class);
        var likes = db.session.getMapper(PostLikesMapper.class);
        var favorites = db.session.getMapper(PostFavoritesMapper.class);
        assertNull(likes.getLikeStatus(3L, 1L));
        var admin = db.transactional(new PostsAdminService(posts, null, likes, favorites,
                null, null, null, null, null));
        assertTrue(admin.restorePost(1L));
        assertEquals(1, likes.getLikeStatus(3L, 1L));
        assertNull(likes.getLikeStatus(4L, 1L));
        assertEquals(1, likes.countLikesByPostId(1L));
        assertEquals(1, posts.selectById(1L).getLikeCount());
        assertEquals(1, posts.selectById(1L).getFavoriteCount());
        db.jdbc.update("UPDATE post_likes SET deleted_at=NOW() WHERE user_id=3");
        likes.insertOrUpdateLike(3L, 1L, 1);
        assertEquals(1, likes.getLikeStatus(3L, 1L));
    }

    @Test void cacheEvictionWaitsForCommitAndRollbackKeepsEntry() {
        var cache = new CacheConfig().cacheManager().getCache("userIdentity");
        cache.put("name", 1L);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(db.dataSource));
        tx.executeWithoutResult(status -> {
            cache.evict("name");
            assertEquals(1L, cache.get("name", Long.class));
            status.setRollbackOnly();
        });
        assertEquals(1L, cache.get("name", Long.class));
        tx.executeWithoutResult(status -> cache.evict("name"));
        assertNull(cache.get("name"));
    }

    @Test void failedAttachmentInsertRollsBackResourceRecord() {
        createUser(5, "uploader");
        var service = db.transactional(new ResourceRecordService(db.session.getMapper(ResourcesMapper.class),
                db.session.getMapper(PostAttachmentsMapper.class)));
        var resource = new chat.liuxin.liutech.model.Resources();
        resource.setName("test.zip");
        resource.setUploaderId(5L);
        resource.setCreatedAt(new java.util.Date());
        resource.setUpdatedAt(new java.util.Date());
        assertThrows(RuntimeException.class, () -> service.save(resource, "x".repeat(100), "resource"));
        assertNotNull(resource.getId());
        assertEquals(0, db.jdbc.queryForObject("SELECT COUNT(*) FROM resources WHERE uploader_id=5", Integer.class));
    }

    @Test void userDeletionAndCleanupTaskCommitTogetherAndWorkerCanRetry() {
        createUser(6, "purge");
        var tasks = db.session.getMapper(UserPurgeTaskMapper.class);
        var usersService = db.transactional(new UserManagementService(users, mock(UserUtils.class),
                mock(org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.class), tasks));
        assertTrue(usersService.permanentDeleteUser(6L));
        assertNull(users.selectIncludingDeletedForUpdate(6L));
        assertEquals(1, db.jdbc.queryForObject("SELECT COUNT(*) FROM user_purge_tasks WHERE user_id=6", Integer.class));
        var client = mock(AiUserDataClient.class);
        doThrow(new IllegalStateException("offline")).when(client).purgeUser(6L);
        new UserPurgeTaskService(tasks, client).processPending();
        assertEquals(1, db.jdbc.queryForObject("SELECT attempts FROM user_purge_tasks WHERE user_id=6", Integer.class));
        assertNull(db.jdbc.queryForObject("SELECT completed_at FROM user_purge_tasks WHERE user_id=6", java.util.Date.class));
        db.jdbc.update("UPDATE user_purge_tasks SET next_attempt_at=NOW() WHERE user_id=6");
        new UserPurgeTaskService(tasks, mock(AiUserDataClient.class)).processPending();
        assertNotNull(db.jdbc.queryForObject("SELECT completed_at FROM user_purge_tasks WHERE user_id=6", java.util.Date.class));
    }

    @Test void foreignKeyFailureRollsBackQueuedCleanupAndKeepsUser() {
        createUser(7, "retained");
        db.jdbc.update("INSERT INTO categories(id,name) VALUES(2,'retained')");
        db.jdbc.update("INSERT INTO posts(id,title,content,category_id,author_id) VALUES(700,'retained','body',2,7)");
        var tasks = db.session.getMapper(UserPurgeTaskMapper.class);
        var usersService = db.transactional(new UserManagementService(users, mock(UserUtils.class),
                mock(org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.class), tasks));
        var failure = assertThrows(chat.liuxin.liutech.common.BusinessException.class, () -> usersService.permanentDeleteUser(7L));
        assertTrue(failure.getMessage().contains("可先禁用账户"));
        assertNotNull(users.selectIncludingDeletedForUpdate(7L));
        assertEquals(0, db.jdbc.queryForObject("SELECT COUNT(*) FROM user_purge_tasks WHERE user_id=7", Integer.class));
    }

    @Test void trendSqlUsesHalfOpenRangeAndExcludesSoftDeletedRows() {
        createUser(8, "trend");
        db.jdbc.update("UPDATE users SET created_at='2001-01-01 12:00:00' WHERE id=8");
        db.jdbc.update("INSERT INTO categories(id,name) VALUES(3,'trend')");
        db.jdbc.update("INSERT INTO posts(id,title,content,category_id,author_id,created_at) " +
                "VALUES(900,'trend','body',3,8,'2001-01-01 12:00:00'),(901,'end','body',3,8,'2001-01-04 00:00:00')");
        var range = db.session.getMapper(PostsMapper.class).countPostsByDateRange("2001-01-01", "2001-01-04");
        assertEquals(1, range.size());
        assertEquals("2001-01-01", range.getFirst().getDate());
        assertEquals(1, range.getFirst().getCount());
        assertEquals(1, users.countUsersByDateRange("2001-01-01", "2001-01-04").getFirst().getCount());
        db.jdbc.update("UPDATE posts SET deleted_at=NOW() WHERE id=900");
        assertTrue(db.session.getMapper(PostsMapper.class).countPostsByDateRange("2001-01-01", "2001-01-04").isEmpty());
    }
}
