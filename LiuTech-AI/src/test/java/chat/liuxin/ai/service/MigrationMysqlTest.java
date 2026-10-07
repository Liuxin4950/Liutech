package chat.liuxin.ai.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="LIUTECH_TEST_MYSQL_URL", matches=".+")
class MigrationMysqlTest {
    @Test void upgradeFromFrozenVersionZeroMatchesCurrentSnapshotAndIsRepeatable() throws Exception {
        try (var upgraded = new MysqlFixture(); var fresh = new MysqlFixture()) {
            upgraded.useBaseline("ai");
            var flyway = Flyway.configure().dataSource(upgraded.dataSource)
                    .locations("filesystem:" + upgraded.repositoryRoot.resolve("Docs/SQL/migrations/ai").toString().replace('\\','/'))
                    .baselineVersion("0").baselineOnMigrate(false).cleanDisabled(true).load();
            flyway.baseline();
            assertTrue(flyway.migrate().migrationsExecuted > 0);
            flyway.validate();
            new chat.liuxin.ai.infra.config.DatabaseSchemaVerifier(upgraded.dataSource).verify();
            assertEquals(fresh.structure("ai"), upgraded.structure("ai"));
            assertEquals(0, flyway.migrate().migrationsExecuted);
            fresh.jdbc.update("INSERT INTO ai_community_worker(id,lease_token) VALUES(1,'existing-worker-lease')");
            var freshFlyway = Flyway.configure().dataSource(fresh.dataSource)
                    .locations("filesystem:" + fresh.repositoryRoot.resolve("Docs/SQL/migrations/ai").toString().replace('\\','/'))
                    .baselineVersion("0").baselineOnMigrate(false).cleanDisabled(true).load();
            freshFlyway.baseline();
            freshFlyway.migrate();
            freshFlyway.validate();
            assertEquals("existing-worker-lease", fresh.jdbc.queryForObject("SELECT lease_token FROM ai_community_worker WHERE id=1", String.class));
            assertEquals(upgraded.structure("ai"), fresh.structure("ai"));
        }
    }
}
