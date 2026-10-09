package chat.liuxin.ai.service;

import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.infra.exception.AIServiceException;
import chat.liuxin.ai.infra.security.PromptBudget;
import chat.liuxin.ai.mapper.AiChatMessageMapper;
import chat.liuxin.ai.mapper.AiModelConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(named="LIUTECH_TEST_MYSQL_URL",matches=".+")
class AiModelConfigMysqlTest {
    AiModelConfigService service(MysqlFixture db) {
        db.session.getConfiguration().addMapper(AiModelConfigMapper.class);
        var props=new AiChatProperties();
        db.jdbc.update("INSERT INTO ai_model_config(id,model_name,display_name,is_default,is_enabled,max_tokens,context_window) " +
            "VALUES(1,'initial','initial',1,1,1000,8192),(2,'configured-model','target',0,1,1000,8192)");
        return db.transactional(new AiModelConfigService(db.session.getMapper(AiModelConfigMapper.class),
            mock(AiChatMessageMapper.class),new PromptBudget(props),props));
    }
    @Test void concurrentDefaultSwitchAndEditorDisableCannotLeaveADisabledOrMissingDefault() throws Exception {
        try(var db=new MysqlFixture();var pool=Executors.newFixedThreadPool(2)) {
            var service=service(db);var start=new CountDownLatch(1);
            var disable=pool.submit(()->{start.await();try{service.updateModel(2L,AiModelConfigServiceTest.request(false));return true;}
                catch(AIServiceException.RequestException rejected){return false;}});
            var makeDefault=pool.submit(()->{start.await();try{service.setDefaultModel(2L);return true;}
                catch(AIServiceException.RequestException rejected){return false;}});
            start.countDown();
            assertNotEquals(disable.get(15,TimeUnit.SECONDS),makeDefault.get(15,TimeUnit.SECONDS));
            assertEquals(1,db.jdbc.queryForObject("SELECT COUNT(*) FROM ai_model_config WHERE is_default=1",Integer.class));
            assertEquals(1,db.jdbc.queryForObject("SELECT COUNT(*) FROM ai_model_config WHERE is_default=1 AND is_enabled=1",Integer.class));
        }
    }
    @Test void concurrentDefaultSwitchAndDeletionHaveOneWinnerAndRetainOneEnabledDefault() throws Exception {
        try(var db=new MysqlFixture();var pool=Executors.newFixedThreadPool(2)) {
            var service=service(db);var start=new CountDownLatch(1);
            var delete=pool.submit(()->{start.await();try{service.deleteModel(2L);return true;}
                catch(AIServiceException.RequestException rejected){return false;}});
            var makeDefault=pool.submit(()->{start.await();try{service.setDefaultModel(2L);return true;}
                catch(AIServiceException.RequestException rejected){return false;}});
            start.countDown();
            assertNotEquals(delete.get(15,TimeUnit.SECONDS),makeDefault.get(15,TimeUnit.SECONDS));
            assertEquals(1,db.jdbc.queryForObject("SELECT COUNT(*) FROM ai_model_config WHERE is_default=1 AND is_enabled=1",Integer.class));
        }
    }
}
