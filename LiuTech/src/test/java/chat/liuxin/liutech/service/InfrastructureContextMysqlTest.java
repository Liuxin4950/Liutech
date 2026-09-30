package chat.liuxin.liutech.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.profiles.active=dev", "spring.flyway.enabled=false", "management.health.mail.enabled=false", "liutech.internal-token=local-test-only", "jwt.secret=local-test-signing-material-local-test-signing-material-local-test-signing-material",
        "spring.mail.host=localhost", "spring.mail.port=1025", "spring.mail.username=test",
        "spring.mail.password=test", "spring.mail.from=test@example.local", "spring.mail.display-name=test",
        "cos.enabled=false", "ai.user-data.cleanup.initial-delay-ms=600000"
})
@EnabledIfEnvironmentVariable(named = "LIUTECH_TEST_MYSQL_URL", matches = ".+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InfrastructureContextMysqlTest {
    private static MysqlFixture db;
    @Autowired ApplicationContext context;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        db = new MysqlFixture();
        registry.add("spring.datasource.url", () -> db.dataSource.getUrl());
        registry.add("spring.datasource.username", () -> db.username);
        registry.add("spring.datasource.password", () -> db.password);
    }
    @AfterAll static void cleanup() throws Exception { if (db != null) db.close(); }
    @Test void completeApplicationContextBuildsWithCurrentSchema() {
        assertNotNull(context.getBean(javax.sql.DataSource.class));
        assertNotNull(context.getBean(UserPurgeTaskService.class));
    }

    @Test void mockHttpHealthEndpointIsUp() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(
                (org.springframework.web.context.WebApplicationContext) context).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("UP"));
    }
    @Test void clearingHistoryRemovesOnlyCurrentReadersViewActivities() {
        db.jdbc.update("INSERT INTO users(id,username,email,password_hash) VALUES(901,'reader','reader@test.local','hash'),(902,'other','other@test.local','hash')");
        db.jdbc.update("INSERT INTO categories(id,name) VALUES(901,'reader-test')");
        db.jdbc.update("INSERT INTO posts(id,title,content,category_id,author_id,status) VALUES(901,'visible','body',901,901,'published')");
        var history = context.getBean(ViewHistoryService.class);
        var activities = context.getBean(UserActivityService.class);
        history.recordView(901L, 901L);
        history.recordView(901L, 902L);
        db.jdbc.update("INSERT INTO post_favorites(user_id,post_id,is_favorite) VALUES(901,901,1)");
        assertTrue(activities.list(901L, 1, 20).getRecords().stream().anyMatch(item -> item.getType().equals("view")));
        assertEquals(1, history.clearViewHistory(901L));
        var remaining = activities.list(901L, 1, 20).getRecords();
        assertFalse(remaining.stream().anyMatch(item -> item.getType().equals("view")));
        assertTrue(remaining.stream().anyMatch(item -> item.getType().equals("favorite")));
        assertEquals(0, history.getViewHistory(1, 10, 901L).getTotal());
        assertEquals(1, history.getViewHistory(1, 10, 902L).getTotal());
    }

    @Test void purchasedResourcesAreUserScopedPagedAndLinkOnlyVisiblePosts() {
        db.jdbc.update("INSERT INTO users(id,username,email,password_hash) VALUES(911,'buyer','buyer@test.local','hash'),(912,'other-buyer','other-buyer@test.local','hash')");
        db.jdbc.update("INSERT INTO categories(id,name) VALUES(911,'resource-test')");
        db.jdbc.update("INSERT INTO posts(id,title,content,category_id,author_id,status) VALUES(911,'visible','body',911,911,'published'),(912,'draft-secret','body',911,911,'draft')");
        db.jdbc.update("INSERT INTO resources(id,name,uploader_id,resource_type,download_type,points_needed) VALUES(911,'one.zip',912,'file',1,10),(912,'removed.zip',912,'file',1,20),(913,'private.zip',912,'file',1,10)");
        db.jdbc.update("INSERT INTO post_attachments(post_id,resource_id) VALUES(911,911),(911,911),(912,911)");
        db.jdbc.update("INSERT INTO download_logs(user_id,resource_id,points_used,downloaded_at) VALUES(911,911,10,'2026-09-01 10:00:00'),(911,912,20,'2026-09-02 10:00:00'),(912,913,10,'2026-09-03 10:00:00')");
        db.jdbc.update("UPDATE resources SET deleted_at=NOW() WHERE id=912");
        var service = context.getBean(ResourceDownloadService.class);
        var first = service.getUserPurchases(911L, 1, 1);
        var second = service.getUserPurchases(911L, 2, 1);
        assertEquals(2, first.getTotal());
        assertEquals(1, first.getRecords().size());
        assertFalse(first.getRecords().getFirst().getAvailable());
        assertNull(first.getRecords().getFirst().getPostId());
        assertEquals(911, second.getRecords().getFirst().getResourceId());
        assertEquals(911, second.getRecords().getFirst().getPostId());
        assertEquals(1, service.getUserPurchases(912L, 1, 10).getTotal());
        assertThrows(chat.liuxin.liutech.common.BusinessException.class, () -> service.getUserPurchases(null, 1, 10));
    }

    @Test void pointsPaginationUsesTheConfiguredInterceptorAndStableLedgerOrder() {
        db.jdbc.update("INSERT INTO users(id,username,email,password_hash) VALUES(931,'ledger-reader','ledger-reader@test.local','hash')");
        var points = context.getBean(PointsService.class);
        for (int i = 0; i < 3; i++) points.addPoints(931L, java.math.BigDecimal.ONE, "test", "test", null, "page-test");
        var admin = context.getBean(PointsAdminService.class);
        var first = admin.getTransactionsByUserId(931L, 1, 2);
        var second = admin.getTransactionsByUserId(931L, 2, 2);
        assertEquals(3, first.getTotal());
        assertEquals(2, first.getRecords().size());
        assertEquals(1, second.getRecords().size());
        assertNotEquals(first.getRecords().getFirst().getId(), second.getRecords().getFirst().getId());
        assertEquals(0, admin.getTransactionsByUserId(931L, Integer.MAX_VALUE, 500).getRecords().size());
    }

    private void seedChainPost(long id) {
        db.jdbc.update("INSERT INTO users(id,username,email,password_hash) VALUES(?,?,?,'hash')", id, "chain"+id, "chain"+id+"@test.local");
        db.jdbc.update("INSERT INTO categories(id,name) VALUES(?,?)", id, "chain-category"+id);
        db.jdbc.update("INSERT INTO posts(id,title,content,category_id,author_id,status) VALUES(?,'chain-post','body',?,?,'published')", id,id,id);
    }

    @Test void articleAndTagTrashKeepAssociationsAndHistoryShowsCurrentTagsAndFavorites() {
        seedChainPost(2001);
        db.jdbc.update("INSERT INTO tags(id,name) VALUES(2001,'chain-tag')");
        db.jdbc.update("INSERT INTO post_tags(post_id,tag_id) VALUES(2001,2001)");
        var history = context.getBean(ViewHistoryService.class);
        history.recordView(2001L,2001L);
        var favorites = context.getBean(PostInteractionService.class);
        var cache = context.getBean(org.springframework.cache.CacheManager.class).getCache("postList");
        cache.put("chain-test-marker", "old-value");
        favorites.toggleFavorite(2001L,2001L);
        assertNull(cache.get("chain-test-marker"));
        var row = history.getViewHistory(1,10,2001L).getRecords().getFirst();
        assertEquals(1,row.getFavoriteStatus());
        assertEquals("chain-tag", row.getTags().getFirst().getName());
        var posts = context.getBean(PostsService.class);
        posts.deletePost(2001L,2001L);
        assertEquals(1,db.jdbc.queryForObject("SELECT COUNT(*) FROM post_tags WHERE post_id=2001",Integer.class));
        context.getBean(PostsAdminService.class).restorePost(2001L);
        var tags = context.getBean(TagsService.class);
        tags.removeByIds(java.util.List.of(2001L));
        assertEquals(1,db.jdbc.queryForObject("SELECT COUNT(*) FROM post_tags WHERE post_id=2001",Integer.class));
        tags.restoreTag(2001L);
        assertEquals(1,tags.getTagByIdWithPostCount(2001L).getPostCount());
        assertEquals(1,context.getBean(CategoriesService.class).getById(2001L).getPostCount());
        db.jdbc.update("INSERT INTO posts(title,content,category_id,author_id,status) VALUES('draft','body',2001,2001,'draft')");
        long draftId = db.jdbc.queryForObject("SELECT MAX(id) FROM posts",Long.class);
        db.jdbc.update("INSERT INTO post_tags(post_id,tag_id) VALUES(?,2001)",draftId);
        assertEquals(1,tags.getAllTagsWithPostCount().stream().filter(tag -> tag.getId()==2001).findFirst().orElseThrow().getPostCount());
        assertEquals(1,context.getBean(CategoriesService.class).getById(2001L).getPostCount());
    }

    @Test void messageTrashAndPermanentCommentDeletionUseExplicitPhysicalSemantics() {
        seedChainPost(2011);
        db.jdbc.update("INSERT INTO messages(nickname,email,content,deleted_at) VALUES('chain-message','msg@test.local','active',NULL),('chain-message','msg@test.local','deleted',NOW())");
        var messages = context.getBean(MessagesService.class);
        assertEquals(1,messages.getMessagesForAdmin(1,10,"chain-message",null,false).getTotal());
        assertEquals(2,messages.getMessagesForAdmin(1,10,"chain-message",null,true).getTotal());
        db.jdbc.update("INSERT INTO comments(id,post_id,user_id,content,deleted_at) VALUES(2011,2011,2011,'root',NOW())");
        db.jdbc.update("INSERT INTO comments(id,post_id,user_id,parent_id,content,deleted_at) VALUES(2012,2011,2011,2011,'child',NOW())");
        assertTrue(context.getBean(CommentsAdminService.class).permanentDeleteComment(2011L));
        assertEquals(0,db.jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE id IN(2011,2012)",Integer.class));
    }

    @Test void resourceSoftDeleteCanBeRestoredAndPermanentDeleteCleansDependentRows() {
        seedChainPost(2021);
        db.jdbc.update("INSERT INTO resources(id,name,uploader_id,resource_type,download_type,points_needed) VALUES(2021,'chain-resource',2021,'file',1,10)");
        db.jdbc.update("INSERT INTO post_attachments(post_id,resource_id) VALUES(2021,2021)");
        db.jdbc.update("INSERT INTO download_logs(user_id,resource_id,points_used) VALUES(2021,2021,10)");
        context.getBean(FileUploadService.class).deleteAttachment(2021L,2021L);
        assertEquals(1,db.jdbc.queryForObject("SELECT COUNT(*) FROM post_attachments WHERE resource_id=2021",Integer.class));
        var admin = context.getBean(ResourcesAdminService.class);
        assertTrue(admin.restoreResource(2021L));
        context.getBean(FileUploadService.class).deleteAttachment(2021L,2021L);
        assertTrue(admin.permanentDeleteResource(2021L));
        assertEquals(0,db.jdbc.queryForObject("SELECT COUNT(*) FROM resources WHERE id=2021",Integer.class));
        assertEquals(0,db.jdbc.queryForObject("SELECT COUNT(*) FROM download_logs WHERE resource_id=2021",Integer.class));
        assertEquals(0,db.jdbc.queryForObject("SELECT COUNT(*) FROM post_attachments WHERE resource_id=2021",Integer.class));
    }

    @Test void activeLookupExcludesDeletedAccountsButNamesRemainReserved() {
        seedChainPost(2031);
        db.jdbc.update("UPDATE users SET deleted_at=NOW() WHERE id=2031");
        var users = context.getBean(chat.liuxin.liutech.mapper.UserMapper.class);
        assertTrue(users.findByUserName("chain2031").isEmpty());
        assertTrue(users.findByEmail("chain2031@test.local").isEmpty());
        assertEquals(1,users.countUsernameIncludingDeleted("chain2031"));
        assertEquals(1,users.countEmailIncludingDeleted("chain2031@test.local"));
    }

    @Test void signBasedPointTotalsIncludeAchievementsAndNegativeAdminAdjustments() {
        seedChainPost(2041);
        var points = context.getBean(PointsService.class);
        var admin = context.getBean(PointsAdminService.class);
        var before = admin.getPointsStats();
        points.addPoints(2041L,java.math.BigDecimal.TEN,PointsService.TYPE_ACHIEVEMENT,PointsService.SOURCE_ACHIEVEMENT,null,"chain-test");
        admin.adjustPoints(2041L,new java.math.BigDecimal("-2"),"chain-test");
        var after = admin.getPointsStats();
        assertEquals(0,java.math.BigDecimal.TEN.compareTo(after.get("totalIssued").subtract(before.get("totalIssued"))));
        assertEquals(0,new java.math.BigDecimal("2").compareTo(after.get("totalConsumed").subtract(before.get("totalConsumed"))));
    }

    @Test void simultaneousCheckinsAwardOnceAndReturnADomainErrorForTheDuplicate() throws Exception {
        seedChainPost(2051);
        var checkin = context.getBean(CheckinService.class);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> call = () -> {
                start.await();
                try { checkin.checkin(2051L); return true; }
                catch (chat.liuxin.liutech.common.BusinessException duplicate) {
                    assertEquals("今日已签到",duplicate.getMessage()); return false;
                }
            };
            var first = pool.submit(call); var second = pool.submit(call); start.countDown();
            assertNotEquals(first.get(15,java.util.concurrent.TimeUnit.SECONDS),second.get(15,java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1,db.jdbc.queryForObject("SELECT COUNT(*) FROM user_checkins WHERE user_id=2051",Integer.class));
        assertEquals(1,db.jdbc.queryForObject("SELECT COUNT(*) FROM points_transactions WHERE user_id=2051",Integer.class));
    }

    @Test void profileEmptyValuesClearFieldsAndHomeCardKeepsTheConfiguredAuthor() {
        seedChainPost(2061);
        db.jdbc.update("UPDATE users SET nickname='old',bio='private-reader-bio',avatar_url='/uploads/chain-test-avatar.png' WHERE id=2061");
        var profile = context.getBean(UserProfileService.class);
        var author = profile.getProfile();
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("chain2061",null,java.util.List.of());
        auth.setDetails(2061L);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            var request = new chat.liuxin.liutech.req.UpdateProfileReq();
            request.setNickname("");request.setBio("");request.setAvatarUrl("");
            var result = profile.updateProfile(request);
            assertEquals("",result.getNickname()); assertEquals("",result.getBio()); assertEquals("",result.getAvatarUrl());
            assertEquals(author.getName(),profile.getProfile().getName());
            assertEquals(author.getBio(),profile.getProfile().getBio());
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }

    @Test void fileUploadDedupDoesNotReuseAnExternalLinkWithTheSameName() {
        seedChainPost(2071);
        db.jdbc.update("INSERT INTO resources(id,name,uploader_id,resource_type,external_link) VALUES(2071,'same-name',2071,'link','https://example.invalid/resource')");
        var resources = context.getBean(chat.liuxin.liutech.mapper.ResourcesMapper.class);
        assertNull(resources.selectRecentDuplicate(2071L,"same-name"));
        db.jdbc.update("INSERT INTO resources(id,name,uploader_id,resource_type,file_url) VALUES(2072,'same-name',2071,'file','/uploads/chain-test.zip')");
        assertEquals(2072,resources.selectRecentDuplicate(2071L,"same-name").getId());
    }

    @Test void softDeletedImagesCanBePermanentlyRemoved() {
        seedChainPost(2091);
        db.jdbc.update("INSERT INTO images(id,file_name,file_url,file_path,mime_type,file_size,file_hash,extension,uploader_id,usage_count,deleted_at) VALUES(2091,'chain.png','https://example.invalid/chain.png','chain.png','image/png',1,?,'png',2091,0,NOW())",
                java.util.UUID.randomUUID().toString().replace("-","").repeat(2));
        var images = context.getBean(chat.liuxin.liutech.mapper.ImagesMapper.class);
        assertNull(images.selectById(2091L));
        assertNotNull(images.selectIncludingDeletedById(2091L));
        assertTrue(context.getBean(ImagesAdminService.class).permanentDeleteImage(2091L));
        assertEquals(0,db.jdbc.queryForObject("SELECT COUNT(*) FROM images WHERE id=2091",Integer.class));
    }

    @Test void logFilterTypesFollowPersistedActionsAndTargets() {
        db.jdbc.update("INSERT INTO system_logs(operator,action,target_type,status) VALUES('chain-test','chain_action','chain_target',1)");
        var logs = context.getBean(LogService.class);
        assertTrue(logs.getActionTypes().contains("chain_action"));
        assertTrue(logs.getTargetTypes().contains("chain_target"));
    }

    @Test void seriesListsUseTheSavedOrderAcrossPublicAndAdminPagination() {
        seedChainPost(2101);
        db.jdbc.update("INSERT INTO post_series(id,name) VALUES(2101,'chain-ordered-series')");
        db.jdbc.update("UPDATE posts SET series_id=2101,series_sort=2 WHERE id=2101");
        db.jdbc.update("INSERT INTO posts(id,title,content,category_id,author_id,status,series_id,series_sort) VALUES(2102,'first','body',2101,2101,'published',2101,1)");
        var request = new chat.liuxin.liutech.req.PostQueryReq();
        request.setSeriesId(2101L); request.setSize(1);
        var posts = context.getBean(PostsService.class);
        assertEquals(2102,posts.getPostList(request,null).getRecords().getFirst().getId());
        request.setPage(2);
        assertEquals(2101,posts.getPostList(request,null).getRecords().getFirst().getId());
        var admin = context.getBean(PostsAdminService.class);
        assertEquals(2102,admin.getPostListForAdmin(1,1,null,null,null,null,2101L,false).getRecords().getFirst().getId());
        assertEquals(2101,admin.getPostListForAdmin(2,1,null,null,null,null,2101L,false).getRecords().getFirst().getId());
    }

}
