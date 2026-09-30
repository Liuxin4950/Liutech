package chat.liuxin.ai.infra.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class DatasourceConfigurationTest {
    @Test void profileOnlyChangesEndpointAndKeepsSharedJdbcOptions() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        var loader = new YamlPropertySourceLoader();
        for (var source : loader.load("base", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }
        String dev = environment.getProperty("spring.datasource.url");
        assertTrue(dev.startsWith("jdbc:mysql://127.0.0.1:3306/liutech_ai?"));
        for (var source : loader.load("prod", new ClassPathResource("application-prod.yml"))) {
            environment.getPropertySources().addFirst(source);
        }
        String prod = environment.getProperty("spring.datasource.url");
        assertTrue(prod.startsWith("jdbc:mysql://mysql:3306/liutech_ai?"));
        assertEquals(dev.substring(dev.indexOf('?')), prod.substring(prod.indexOf('?')));
        environment.setProperty("DB_HOST", "db.internal");
        environment.setProperty("DB_PORT", "3307");
        assertTrue(environment.getProperty("spring.datasource.url").startsWith("jdbc:mysql://db.internal:3307/"));
        assertTrue(prod.contains("allowPublicKeyRetrieval=true"));
        assertFalse(prod.contains("autoReconnect"));
    }
}
