package chat.liuxin.liutech.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 执行整份权威 SQL，包括两库切换和种子数据；只使用本机随机测试库。 */
@EnabledIfEnvironmentVariable(named = "LIUTECH_TEST_MYSQL_URL", matches = ".+")
class SqlInitializationMysqlTest {
    @Test
    void fullSnapshotCreatesSafeDefaultsAndPreservesCustomizedDataOnRepeat() throws Exception {
        try (var snapshot = Snapshot.create()) {
            var db = snapshot.db();
            String main = snapshot.main();
            String ai = snapshot.ai();
            assertEquals(33, snapshot.tableCount(main));
            assertEquals(14, snapshot.tableCount(ai));
            for (String table : new String[]{"users", "posts", "comments", "resources", "music", "images"}) {
                assertEquals(0, snapshot.count(main, table), table);
            }
            for (String table : new String[]{"ai_conversation", "ai_chat_message", "ai_community_task", "ai_community_memory"}) {
                assertEquals(0, snapshot.count(ai, table), table);
            }
            assertEquals(1, db.jdbc.queryForObject("SELECT COUNT(*) FROM " + main + ".tags WHERE name='Flutter'", Integer.class));
            assertEquals(1, db.jdbc.queryForObject("SELECT COUNT(*) FROM " + main + ".post_series WHERE name='Flutter学习'", Integer.class));
            assertEquals(1, db.jdbc.queryForObject("SELECT JSON_VALID(setting_value) FROM " + main
                    + ".system_settings WHERE setting_key='about.content'", Integer.class));
            assertTrue(db.jdbc.queryForObject("SELECT content FROM " + main + ".announcements LIMIT 1", String.class).contains("🤣"));
            assertEquals(0, db.jdbc.queryForObject("SELECT enabled FROM " + main + ".community_settings WHERE id=1", Integer.class));
            assertEquals(1, db.jdbc.queryForObject("SELECT bot_relay_enabled FROM " + main + ".community_settings WHERE id=1", Integer.class));
            assertEquals(0, db.jdbc.queryForObject("SELECT enabled FROM " + ai + ".ai_tts_config WHERE id=1", Integer.class));
            assertEquals("SILICONFLOW", db.jdbc.queryForObject("SELECT provider FROM " + ai + ".ai_tts_config WHERE id=1", String.class));
            assertNull(db.jdbc.queryForObject("SELECT siliconflow_voice_uri FROM " + ai + ".ai_tts_config WHERE id=1", String.class));
            assertEquals(2, snapshot.count(ai, "ai_model_config"));
            assertEquals("deepseek-ai/DeepSeek-V3.2", db.jdbc.queryForObject("SELECT model_name FROM " + ai
                    + ".ai_model_config WHERE is_default=1 AND is_enabled=1", String.class));
            assertEquals(1, db.jdbc.queryForObject("SELECT COUNT(*) FROM " + ai + ".ai_model_config WHERE is_enabled=1", Integer.class));

            db.jdbc.update("UPDATE " + main + ".system_settings SET setting_value=? WHERE setting_key='author.name'", "自定义作者");
            db.jdbc.update("UPDATE " + main + ".system_settings SET setting_value=? WHERE setting_key='about.content'", "{\"custom\":true}");
            db.jdbc.update("UPDATE " + main + ".announcements SET content='自定义公告',status=0,view_count=29");
            db.jdbc.update("UPDATE " + main + ".community_settings SET enabled=1,bot_relay_enabled=0,site_daily_task_limit=321,version=17 WHERE id=1");
            db.jdbc.update("UPDATE " + ai + ".ai_tts_config SET enabled=1,siliconflow_voice_uri='test-custom-voice' WHERE id=1");
            db.jdbc.update("UPDATE " + ai + ".ai_community_worker SET lease_token='active-test-lease' WHERE id=1");
            db.jdbc.update("UPDATE " + ai + ".ai_model_config SET is_default=0,is_enabled=0");
            db.jdbc.update("UPDATE " + ai + ".ai_model_config SET is_default=1,is_enabled=1,max_tokens=1234 WHERE model_name='Qwen/Qwen2.5-7B-Instruct'");
            var counts = snapshot.rowCounts();

            snapshot.execute(1);
            assertEquals(counts, snapshot.rowCounts(), "重复初始化不得复制基础数据");
            assertEquals("自定义作者", db.jdbc.queryForObject("SELECT setting_value FROM " + main + ".system_settings WHERE setting_key='author.name'", String.class));
            assertEquals("{\"custom\":true}", db.jdbc.queryForObject("SELECT setting_value FROM " + main + ".system_settings WHERE setting_key='about.content'", String.class));
            assertEquals(29, db.jdbc.queryForObject("SELECT view_count FROM " + main + ".announcements LIMIT 1", Integer.class));
            assertEquals(0, db.jdbc.queryForObject("SELECT status FROM " + main + ".announcements LIMIT 1", Integer.class));
            assertEquals(321, db.jdbc.queryForObject("SELECT site_daily_task_limit FROM " + main + ".community_settings WHERE id=1", Integer.class));
            assertEquals(0, db.jdbc.queryForObject("SELECT bot_relay_enabled FROM " + main + ".community_settings WHERE id=1", Integer.class));
            assertEquals(17L, db.jdbc.queryForObject("SELECT version FROM " + main + ".community_settings WHERE id=1", Long.class));
            assertEquals("test-custom-voice", db.jdbc.queryForObject("SELECT siliconflow_voice_uri FROM " + ai + ".ai_tts_config WHERE id=1", String.class));
            assertEquals(1, db.jdbc.queryForObject("SELECT enabled FROM " + ai + ".ai_tts_config WHERE id=1", Integer.class));
            assertEquals("active-test-lease", db.jdbc.queryForObject("SELECT lease_token FROM " + ai + ".ai_community_worker WHERE id=1", String.class));
            assertEquals(1234, db.jdbc.queryForObject("SELECT max_tokens FROM " + ai + ".ai_model_config WHERE is_default=1 AND is_enabled=1", Integer.class));
            snapshot.execute(0);
            assertEquals(counts, snapshot.rowCounts());
        }
    }

    @Test
    void nonEmptyModelCatalogDoesNotGainOrReenablePresetModels() throws Exception {
        try (var snapshot = Snapshot.create()) {
            String table = snapshot.ai() + ".ai_model_config";
            snapshot.db().jdbc.update("DELETE FROM " + table);
            snapshot.db().jdbc.update("INSERT INTO " + table
                    + "(model_name,display_name,provider,is_enabled,is_default,max_tokens) VALUES('custom-model','自定义模型','openai',0,0,777)");
            snapshot.execute(1);
            assertEquals(1, snapshot.count(snapshot.ai(), "ai_model_config"));
            assertEquals(0, snapshot.db().jdbc.queryForObject("SELECT is_enabled FROM " + table, Integer.class));
            assertEquals(0, snapshot.db().jdbc.queryForObject("SELECT is_default FROM " + table, Integer.class));
            assertEquals(777, snapshot.db().jdbc.queryForObject("SELECT max_tokens FROM " + table, Integer.class));
        }
    }

    private record Snapshot(MysqlFixture db, String main, String ai) implements AutoCloseable {
        static Snapshot create() throws Exception {
            var db = new MysqlFixture();
            var snapshot = new Snapshot(db, db.schema + "_main", db.schema + "_ai");
            try {
                snapshot.execute(1);
                return snapshot;
            } catch (Exception | AssertionError failure) {
                snapshot.close();
                throw failure;
            }
        }

        void execute(int foreignKeyChecks) throws Exception {
            String sql = Files.readString(db.repositoryRoot.resolve("Docs/SQL/sql.sql"))
                    .replace("CREATE DATABASE IF NOT EXISTS liutech_ai ", "CREATE DATABASE IF NOT EXISTS " + ai + " ")
                    .replace("USE liutech_ai;", "USE " + ai + ";")
                    .replace("CREATE DATABASE IF NOT EXISTS liutech ", "CREATE DATABASE IF NOT EXISTS " + main + " ")
                    .replace("USE liutech;", "USE " + main + ";");
            assertFalse(sql.matches("(?s).*\\b(?:USE|CREATE DATABASE IF NOT EXISTS) liutech(?:_ai)?(?:[ ;]).*"));
            try (var connection = db.dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS=" + foreignKeyChecks);
                ScriptUtils.executeSqlScript(connection, new EncodedResource(
                        new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
                try (var rows = statement.executeQuery("SELECT @@FOREIGN_KEY_CHECKS")) {
                    assertTrue(rows.next());
                    assertEquals(foreignKeyChecks, rows.getInt(1), "初始化不得改变调用方的外键检查设置");
                }
            }
        }

        int tableCount(String schema) {
            return db.jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=?", Integer.class, schema);
        }

        int count(String schema, String table) {
            return db.jdbc.queryForObject("SELECT COUNT(*) FROM " + schema + "." + table, Integer.class);
        }

        Map<String, Integer> rowCounts() {
            var counts = new LinkedHashMap<String, Integer>();
            for (String schema : new String[]{main, ai}) {
                for (String table : db.jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=? ORDER BY table_name", String.class, schema)) {
                    counts.put(schema + "." + table, count(schema, table));
                }
            }
            return counts;
        }

        @Override
        public void close() throws Exception {
            try {
                db.jdbc.execute("DROP DATABASE IF EXISTS " + main);
                db.jdbc.execute("DROP DATABASE IF EXISTS " + ai);
            } finally {
                db.close();
            }
        }
    }
}
