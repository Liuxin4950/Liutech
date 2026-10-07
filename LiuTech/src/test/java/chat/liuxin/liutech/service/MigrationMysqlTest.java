package chat.liuxin.liutech.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="LIUTECH_TEST_MYSQL_URL", matches=".+")
class MigrationMysqlTest {
    @Test void upgradeFromFrozenVersionZeroMatchesCurrentSnapshotAndIsRepeatable() throws Exception {
        try (var upgraded = new MysqlFixture(); var fresh = new MysqlFixture()) {
            upgraded.useBaseline("main");
            var flyway = Flyway.configure().dataSource(upgraded.dataSource)
                    .locations("filesystem:" + upgraded.repositoryRoot.resolve("Docs/SQL/migrations/main").toString().replace('\\','/'))
                    .baselineVersion("0").baselineOnMigrate(false).cleanDisabled(true).load();
            flyway.baseline();
            assertTrue(flyway.migrate().migrationsExecuted > 0);
            flyway.validate();
            new chat.liuxin.liutech.config.DatabaseSchemaVerifier(upgraded.dataSource).verify();
            assertEquals(fresh.structure("main"), upgraded.structure("main"));
            assertEquals(0, flyway.migrate().migrationsExecuted);
            fresh.jdbc.update("INSERT INTO community_settings(id,enabled,site_daily_task_limit) VALUES(1,TRUE,321)");
            var freshFlyway = Flyway.configure().dataSource(fresh.dataSource)
                    .locations("filesystem:" + fresh.repositoryRoot.resolve("Docs/SQL/migrations/main").toString().replace('\\','/'))
                    .baselineVersion("0").baselineOnMigrate(false).cleanDisabled(true).load();
            freshFlyway.baseline();
            freshFlyway.migrate();
            freshFlyway.validate();
            assertEquals(321, fresh.jdbc.queryForObject("SELECT site_daily_task_limit FROM community_settings WHERE id=1", Integer.class));
            assertEquals(true, fresh.jdbc.queryForObject("SELECT enabled FROM community_settings WHERE id=1", Boolean.class));
            assertEquals(upgraded.structure("main"), fresh.structure("main"));
        }
    }
}
