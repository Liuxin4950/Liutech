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

}
