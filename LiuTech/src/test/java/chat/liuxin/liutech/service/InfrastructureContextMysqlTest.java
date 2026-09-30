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
}
