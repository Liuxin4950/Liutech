package chat.liuxin.ai.service;

import chat.liuxin.ai.mapper.CommunityMapper;
import chat.liuxin.ai.service.community.CommunityStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="LIUTECH_TEST_MYSQL_URL",matches=".+")
class CommunityCancellationMysqlTest {
    @Test void cancelledTaskCannotBeRevivedByAnyLateWorkerWriteAndQueuesIncludeOlderActiveTasks() throws Exception {
        try(var db=new MysqlFixture()) {
            db.session.getConfiguration().addMapper(CommunityMapper.class);
            var mapper=db.session.getMapper(CommunityMapper.class);
            var store=db.transactional(new CommunityStore(mapper,new ObjectMapper()));
            String task=UUID.randomUUID().toString();
            db.jdbc.update("INSERT INTO ai_community_task(id,event_id,bot_id,post_id,root_event_id,status,available_at,created_at) VALUES(?,1,1,2,'root','RUNNING',NOW(),'2020-01-01')",task);
            for(int i=2;i<8;i++) db.jdbc.update("INSERT INTO ai_community_task(id,event_id,bot_id,post_id,root_event_id,status,available_at) VALUES(?,?,1,3,'root','SUCCEEDED',NOW())",UUID.randomUUID().toString(),i);
            String failed=UUID.randomUUID().toString();
            db.jdbc.update("INSERT INTO ai_community_task(id,event_id,bot_id,post_id,root_event_id,status,available_at,created_at) VALUES(?,99,1,2,'root','FAILED',NOW(),'2019-01-01')",failed);
            assertEquals(task,mapper.tasks(null,null,null,1).getFirst().getId());
            assertEquals(3,mapper.tasks(null,null,null,1).size());
            assertTrue(mapper.tasks(null,null,null,1).stream().anyMatch(row->failed.equals(row.getId())));
            assertEquals(2,mapper.tasks(null,2L,null,1).size());
            store.cancel(task);
            assertEquals(0,mapper.decide(task,"{}","v1"));
            assertEquals(0,mapper.attempt(task,0));
            assertEquals(0,mapper.finish(task,"SUCCEEDED",null));
            assertEquals(0,mapper.retry(task,"late failure",false,0));
            assertEquals(0,mapper.fail(task,"FAILED","late failure",false));
            assertEquals(0,mapper.retryFailed(task,true));
            assertEquals(0,mapper.lease(task,600));
            assertTrue(mapper.cancelled(task));
            assertEquals("CANCELLED",mapper.taskById(task).getStatus());
            assertEquals(task,mapper.tasks(null,2L,"CANCELLED",1).getFirst().getId());
            assertTrue(mapper.runs(null,2L,10).isEmpty());
        }
    }
}
