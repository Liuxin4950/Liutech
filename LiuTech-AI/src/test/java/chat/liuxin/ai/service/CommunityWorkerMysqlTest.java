package chat.liuxin.ai.service;

import chat.liuxin.ai.dto.community.CommunityTask;
import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.mapper.CommunityMapper;
import chat.liuxin.ai.service.community.CommunityStore;
import chat.liuxin.ai.service.community.CommunityWorkerMonitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="LIUTECH_TEST_MYSQL_URL",matches=".+")
class CommunityWorkerMysqlTest {
    @Test void runCreationDatesUseTheDatabaseEpochInsteadOfAmbiguousJdbcDateTime() throws Exception {
        try(var db=new MysqlFixture()) {
            db.dataSource.setUrl(db.dataSource.getUrl()+"&serverTimezone=Asia/Shanghai&sessionVariables=time_zone='%2B00:00'");
            db.session.getConfiguration().addMapper(CommunityMapper.class);
            var mapper=db.session.getMapper(CommunityMapper.class);
            String id=UUID.randomUUID().toString(),task=UUID.randomUUID().toString();
            mapper.run(id,task,1,2,"PREVIEW","{\"postTitle\":\"snapshot\"}",null);
            var props=new AiChatProperties();
            var service=new chat.liuxin.ai.service.community.CommunityService(
                org.mockito.Mockito.mock(chat.liuxin.ai.common.client.BackendApiTransport.class),
                org.mockito.Mockito.mock(SiliconFlowChatClient.class),org.mockito.Mockito.mock(chat.liuxin.ai.infra.security.AiModelPolicy.class),
                new chat.liuxin.ai.infra.security.PromptBudget(props),mapper,
                new CommunityStore(mapper,new ObjectMapper()),new ObjectMapper(),
                org.mockito.Mockito.mock(chat.liuxin.ai.common.monitor.AiMetrics.class),new CommunityWorkerMonitor(mapper,props,5000,15000,60000));
            var run=service.runs(1L,2L,10).getFirst();
            long actual=((Number)run.get("createdAtEpochMs")).longValue();
            assertTrue(Math.abs(actual-System.currentTimeMillis())<3000);
            assertEquals(actual,java.time.Instant.parse((String)run.get("createdAt")).toEpochMilli());
            assertTrue(((String)run.get("createdAt")).endsWith("Z"));
        }
    }
    @Test void schedulerCountdownsStayCorrectWithUtcSessionAndShanghaiJdbcDates() throws Exception {
        try(var db=new MysqlFixture()) {
            db.dataSource.setUrl(db.dataSource.getUrl()+"&serverTimezone=Asia/Shanghai&sessionVariables=time_zone='%2B00:00'");
            db.session.getConfiguration().addMapper(CommunityMapper.class);
            var mapper=db.session.getMapper(CommunityMapper.class);
            var store=db.transactional(new CommunityStore(mapper,new ObjectMapper()));
            var monitor=new CommunityWorkerMonitor(mapper,new AiChatProperties(),5000,15000,60000);
            db.jdbc.update("INSERT INTO ai_community_worker(id) VALUES(1)");
            var task=new CommunityTask();task.setId(UUID.randomUUID().toString());task.setEventId(1L);task.setBotId(1L);
            task.setPostId(2L);task.setRootEventId(UUID.randomUUID().toString());task.setMemoryEpoch(0L);
            mapper.task(task);mapper.retry(task.getId(),"scheduled retry",false,60);
            var delayed=mapper.tasks(1L,2L,null,10).getFirst();
            assertTrue(delayed.getDueSeconds()>=55 && delayed.getDueSeconds()<=60);
            assertTrue(delayed.getAvailableAtEpochMs()-System.currentTimeMillis()>=55000);
            assertTrue(delayed.getAvailableAtEpochMs()-System.currentTimeMillis()<=60000);
            assertNull(delayed.getLeaseRemainingSeconds());assertNull(store.claim(600));
            monitor.heartbeat();var status=monitor.status();
            assertEquals("+00:00",status.database().sessionTimeZone());assertEquals("WAITING_TASK",status.state());
            assertEquals(1,status.queue().delayedCount());assertEquals(0,status.queue().readyCount());
            assertTrue(status.queue().nextDueSeconds()>=55 && status.queue().nextDueSeconds()<=60);
            assertEquals(1,mapper.acquireWorker("another-instance",600));
            assertEquals("OWNED_ELSEWHERE",monitor.status().leaseState());
            assertTrue(monitor.status().leaseRemainingSeconds()>=595 && monitor.status().leaseRemainingSeconds()<=600);
            db.jdbc.update("UPDATE ai_community_task SET available_at=TIMESTAMPADD(SECOND,-1,NOW()) WHERE id=?",task.getId());
            assertNotNull(store.claim(600));
            var claimed=mapper.tasks(1L,2L,null,10).getFirst();
            assertTrue(claimed.getDueSeconds()<=0);assertTrue(claimed.getLeaseRemainingSeconds()>=595);
            assertEquals(1,monitor.status().queue().leasedCount());
            mapper.cancel(task.getId());assertEquals(0,monitor.status().queue().leasedCount());
        }
    }
}
