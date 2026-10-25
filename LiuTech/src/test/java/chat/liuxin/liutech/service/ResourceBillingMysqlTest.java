package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.mapper.*;
import chat.liuxin.liutech.storage.FileStorage;
import chat.liuxin.liutech.utils.FileUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 在临时 MySQL 库验证资源购买记录、余额和流水的真实提交及回滚。 */
@EnabledIfEnvironmentVariable(named = "LIUTECH_TEST_MYSQL_URL", matches = ".+")
class ResourceBillingMysqlTest {
    private MysqlFixture db;
    private ResourceDownloadService service;

    @BeforeEach void setup() throws Exception {
        db = new MysqlFixture("mapper/UserMapper.xml", "mapper/PointsTransactionMapper.xml",
                "mapper/ResourcesMapper.xml", "mapper/ResourceDownloadsMapper.xml");
        // 与应用一致地启用公共字段填充，避免临时 Mapper 环境绕过创建时间约束。
        var fill = new chat.liuxin.liutech.config.MyMetaObjectHandler();
        org.springframework.test.util.ReflectionTestUtils.setField(fill, "userUtils",
                mock(chat.liuxin.liutech.utils.UserUtils.class));
        com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.getGlobalConfig(db.session.getConfiguration())
                .setMetaObjectHandler(fill);
        var points = db.transactional(new PointsService(db.session.getMapper(UserMapper.class),
                db.session.getMapper(PointsTransactionMapper.class)));
        service = db.transactional(new ResourceDownloadService(db.session.getMapper(ResourcesMapper.class),
                db.session.getMapper(ResourceDownloadsMapper.class), points, mock(FileStorage.class), mock(FileUtil.class)));
        db.jdbc.update("INSERT INTO users(id,username,email,password_hash,points) VALUES"
                + "(1,'buyer','buyer@example.test','unused',10),(2,'owner','owner@example.test','unused',0)");
    }

    @AfterEach void cleanup() throws Exception { if (db != null) db.close(); }

    @Test void zeroPriceLegacyResourceCanBeAcquiredOnceWithoutChangingBalance() {
        resource(1, 1, "0");
        service.purchaseResource(1L, 1L);
        assertTrue(service.hasUserPurchased(1L, 1L));
        assertEquals(0, new BigDecimal("10").compareTo(balance()));
        assertEquals(0, count("points_transactions"));
        assertEquals(1, count("download_logs"));
        assertThrows(BusinessException.class, () -> service.purchaseResource(1L, 1L));
        assertEquals(1, count("download_logs"));
        assertEquals(0, new BigDecimal("10").compareTo(balance()));
    }

    @Test void legacyFreeTypeWithPositivePriceUsesTheSamePaidRuleAsDownload() {
        resource(1, 0, "2.50");
        assertFalse(service.hasUserPurchased(1L, 1L));
        service.purchaseResource(1L, 1L);
        assertTrue(service.hasUserPurchased(1L, 1L));
        assertEquals(0, new BigDecimal("7.50").compareTo(balance()));
        assertEquals(1, count("points_transactions"));
        assertEquals(1, count("download_logs"));
    }

    @Test void insufficientBalanceRollsBackPurchaseRecordAndKeepsPointsUntouched() {
        resource(1, 1, "11");
        assertThrows(BusinessException.class, () -> service.purchaseResource(1L, 1L));
        assertEquals(0, count("download_logs"));
        assertEquals(0, count("points_transactions"));
        assertEquals(0, new BigDecimal("10").compareTo(balance()));
    }

    @Test void negativeLegacyPriceIsRejectedWithoutGrantingPointsOrWritingPurchase() {
        resource(1, 1, "-1");
        assertThrows(BusinessException.class, () -> service.purchaseResource(1L, 1L));
        assertEquals(0, count("download_logs"));
        assertEquals(0, count("points_transactions"));
        assertEquals(0, new BigDecimal("10").compareTo(balance()));
    }

    private void resource(long id, int type, String price) {
        db.jdbc.update("INSERT INTO resources(id,name,uploader_id,download_type,points_needed) VALUES(?, '测试资源',2,?,?)",
                id, type, new BigDecimal(price));
    }

    private BigDecimal balance() {
        return db.jdbc.queryForObject("SELECT points FROM users WHERE id=1", BigDecimal.class);
    }

    private long count(String table) {
        // 表名仅由本测试的固定常量提供，不接受外部输入。
        return db.jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
