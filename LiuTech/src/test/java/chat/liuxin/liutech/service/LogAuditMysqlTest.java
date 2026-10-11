package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.AdminLogsMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** 可选真实 MySQL 回归：只使用本机随机临时库，绝不触及现有账号或日志。 */
@EnabledIfEnvironmentVariable(named = "LIUTECH_TEST_MYSQL_URL", matches = ".+")
class LogAuditMysqlTest {
    private MysqlFixture db;
    private AdminLogsMapper mapper;
    private LogService logs;
    @BeforeEach void setup() throws Exception {
        db = new MysqlFixture("mapper/AdminLogsMapper.xml");
        mapper = db.session.getMapper(AdminLogsMapper.class);
        logs = new LogService(mapper);
        db.jdbc.update("INSERT INTO users(id,username,email,password_hash,role,status) VALUES(7,'renamed-admin','admin@invalid.local','test-hash','admin',1)");
        db.jdbc.update("INSERT INTO system_logs(id,operator,operator_id,action,target_type,status) VALUES(1,'old-admin-name',7,'update','user',1)");
        db.jdbc.update("INSERT INTO system_logs(id,operator,operator_id,action,target_type,status) VALUES(2,'removed-qa-account',88,'delete','user',1)");
        db.jdbc.update("INSERT INTO system_logs(id,operator,action,target_type,status) VALUES(3,'未认证访问','login','user',0)");
        db.jdbc.update("INSERT INTO system_logs(id,operator,operator_id,action,target_type,status,created_at) VALUES(4,'old-admin-name',7,'update','ai_model',0,TIMESTAMPADD(DAY,-1,NOW()))");
    }
    @AfterEach void cleanup() throws Exception { if (db != null) db.close(); }

    @Test void historicalActorSurvivesRenameAndMissingAccountWithoutBeingReattributed() {
        var renamed = mapper.selectLogDetail(1L);
        assertEquals("old-admin-name", renamed.getOperator());
        assertEquals("renamed-admin", renamed.getCurrentOperator());
        assertEquals("admin", renamed.getOperatorAccountStatus());
        var removed = mapper.selectLogDetail(2L);
        assertEquals("removed-qa-account", removed.getOperator());
        assertEquals(88L, removed.getOperatorId());
        assertEquals("deleted", removed.getOperatorAccountStatus());
        assertNull(removed.getCurrentOperator());
        assertEquals("unverified", mapper.selectLogDetail(3L).getOperatorAccountStatus());
        db.jdbc.update("UPDATE users SET status=0 WHERE id=7");
        assertEquals("disabled", mapper.selectLogDetail(1L).getOperatorAccountStatus());
    }

    @Test void summaryCountsRealRowsWithIdenticalActorActionStatusAndDateFilters() {
        var all = logs.getSummary(null, null, null, null, null, null);
        assertEquals(4, all.getTotal());
        assertEquals(2, all.getSuccess());
        assertEquals(2, all.getFailure());
        assertEquals(3, all.getToday());
        var filtered = logs.getSummary("old-admin", "update", "ai_model", null, null, 0);
        assertEquals(1, filtered.getTotal());
        assertEquals(0, filtered.getSuccess());
        assertEquals(1, filtered.getFailure());
        assertEquals(0, filtered.getToday());
        String today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString();
        assertEquals(0, logs.getSummary(null, null, "ai_model", today + " 00:00:00", today + " 23:59:59", null).getTotal());
    }

    @Test void shanghaiDayAndTimestampStayCorrectWithUtcDatabaseSession() throws Exception {
        try (var utc = new MysqlFixture(java.util.Map.of("serverTimezone", "Asia/Shanghai", "forceConnectionTimeZoneToSession", "false",
                "sessionVariables", "time_zone='+00:00'"), "mapper/AdminLogsMapper.xml")) {
            var utcMapper = utc.session.getMapper(AdminLogsMapper.class);
            var utcLogs = new LogService(utcMapper);
            long today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toEpochSecond();
            utc.jdbc.update("INSERT INTO system_logs(id,operator,action,target_type,status,created_at) VALUES(1,'admin','update','user',1,FROM_UNIXTIME(?))", today + 1);
            utc.jdbc.update("INSERT INTO system_logs(id,operator,action,target_type,status,created_at) VALUES(2,'admin','update','user',1,FROM_UNIXTIME(?))", today - 1);
            assertEquals(1, utcLogs.getSummary(null, null, null, null, null, null).getToday());
            String localDay = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString();
            assertEquals(1, utcLogs.getSummary(null, null, null, localDay, localDay, null).getTotal());
            assertEquals((today + 1) * 1000, utcMapper.selectLogDetail(1L).getCreatedAtEpochMs());
        }
    }
}
