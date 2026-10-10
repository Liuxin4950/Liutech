package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.CommentsMapper;
import chat.liuxin.liutech.mapper.CommunityMapper;
import chat.liuxin.liutech.model.CommunityEvent;
import chat.liuxin.liutech.req.CommunityReq;
import chat.liuxin.liutech.resp.CommunityResp;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 InnoDB 验证跨库取消的主库发布边界、邀请去重和撤回的公开事实。 */
@EnabledIfEnvironmentVariable(named="LIUTECH_TEST_MYSQL_URL",matches=".+")
class CommunityLifecycleMysqlTest {
    MysqlFixture db;
    CommunityService service;
    CommunityMapper mapper;
    static final String ROOT="848ca02c-0eaa-4bf8-97c1-fb5fd3cab0e2";
    static final String TASK="2c8dc80c-b09c-4327-9a67-9ba53d4a6800";
    @BeforeEach void setup() throws Exception {
        db=new MysqlFixture("mapper/CommentsMapper.xml");
        db.session.getConfiguration().addMapper(CommunityMapper.class);
        mapper=db.session.getMapper(CommunityMapper.class);
        service=db.transactional(new CommunityService(mapper,db.session.getMapper(CommentsMapper.class),
            org.mockito.Mockito.mock(ImageReferenceService.class)));
        db.jdbc.update("INSERT INTO users(id,username,email,password_hash) VALUES(1,'test','test@example.test','unused')");
        db.jdbc.update("INSERT INTO categories(id,name) VALUES(1,'测试分类')");
        db.jdbc.update("INSERT INTO posts(id,title,content,category_id,author_id,status) VALUES(2,'文章','正文',1,1,'published')");
        db.jdbc.update("INSERT INTO community_bots(id,name,personality,enabled,participation) VALUES(1,'测试角色','简洁',TRUE,50)");
        db.jdbc.update("INSERT INTO community_settings(id,enabled,min_delay_seconds,max_delay_seconds,cooldown_seconds) VALUES(1,TRUE,0,0,0)");
        db.jdbc.update("INSERT INTO community_post_state(post_id,enabled,first_public_seen) VALUES(2,TRUE,TRUE)");
        db.jdbc.update("INSERT INTO community_chains(root_event_id,post_id) VALUES(?,2)",ROOT);
        db.jdbc.update("INSERT INTO community_events(id,event_key,event_type,bot_id,post_id,root_event_id,available_at) VALUES(7,'ARTICLE_PUBLISHED:2:1','ARTICLE_PUBLISHED',1,2,?,NOW())",ROOT);
    }
    @AfterEach void cleanup() throws Exception {if(db!=null) db.close();}
    CommunityReq.Publish publishRequest() {
        return new CommunityReq.Publish(TASK,1L,2L,null,null,ROOT,service.context(1L,2L,null).contextVersion(),"一次发言");
    }
    @Test void simultaneousCancelAndPublishHaveExactlyOneWinner() throws Exception {
        var request=publishRequest();
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var publication=pool.submit(()->{
                start.await();
                try {return (Object)service.publish(request);} catch(BusinessException error) {return error;}
            });
            var cancellation=pool.submit(()->{start.await();return service.cancelTask(new CommunityReq.Cancel(TASK,7L,1L,2L));});
            start.countDown();
            Object result=publication.get(15,TimeUnit.SECONDS);
            var cancelled=cancellation.get(15,TimeUnit.SECONDS);
            int comments=db.jdbc.queryForObject("SELECT COUNT(*) FROM community_publications",Integer.class);
            if(cancelled.cancelled()) {
                assertEquals(0,comments);
                assertInstanceOf(BusinessException.class,result);
                assertEquals(ErrorCode.COMMUNITY_TASK_CANCELLED.getCode(),((BusinessException)result).getCode());
                assertEquals("CANCELLED",mapper.event(7L).getEventType());
            } else {
                assertEquals(1,comments);
                assertInstanceOf(CommunityResp.Published.class,result);
                assertEquals(((CommunityResp.Published)result).commentId(),cancelled.publishedCommentId());
            }
        }
    }
    @Test void cancelledSourceCannotBeRepublishedOrReinvitedAndHandoffCannotBeCancelledWithoutTaskId() {
        String version=service.context(1L,2L,null).contextVersion();
        service.setPostEnabled(2L,true);service.setPostEnabled(2L,true);
        assertEquals(version,service.context(1L,2L,null).contextVersion());
        assertEquals(1,service.pendingEvents(null,2L).size());
        assertTrue(service.cancelPendingEvent(7L).cancelled());
        assertTrue(service.pendingEvents(null,2L).isEmpty());
        var error=assertThrows(BusinessException.class,()->service.publish(publishRequest()));
        assertEquals(1706,error.getCode());
        assertEquals(0,service.invite(2L,List.of(1L)).queued());
        db.jdbc.update("UPDATE community_events SET event_type='ARTICLE_PUBLISHED',acknowledged_at=NULL,lease_token='claimed',lease_until=TIMESTAMPADD(SECOND,600,NOW()) WHERE id=7");
        assertEquals("DISPATCHING",service.pendingEvents(null,2L).getFirst().getStatus());
        assertFalse(service.cancelPendingEvent(7L).cancelled());
    }
    @Test void pendingEventsBeyondOneHundredRemainVisibleAndCancellable() {
        for(int i=0;i<105;i++) db.jdbc.update("INSERT INTO community_events(event_key,event_type,bot_id,post_id,root_event_id,available_at) VALUES(?,'HUMAN_COMMENT',1,2,?,NOW())","queue-"+i,ROOT);
        var events=service.pendingEvents(1L,2L);
        assertEquals(106,events.size());
        long last=events.getLast().getId();
        assertTrue(service.cancelPendingEvent(last).cancelled());
        assertEquals(105,service.pendingEvents(1L,2L).size());
        assertTrue(service.pendingEvents(1L,3L).isEmpty());
    }
    @Test void eventDelayUsesDatabaseClockWithUtcSessionAndShanghaiJdbc() throws Exception {
        try(var timezoneDb=new MysqlFixture(Map.of("serverTimezone","Asia/Shanghai",
                "forceConnectionTimeZoneToSession","false","sessionVariables","time_zone='+00:00'"),"mapper/CommentsMapper.xml")) {
            timezoneDb.session.getConfiguration().addMapper(CommunityMapper.class);
            var timezoneMapper=timezoneDb.session.getMapper(CommunityMapper.class);
            assertEquals("+00:00",timezoneDb.jdbc.queryForObject("SELECT @@session.time_zone",String.class));
            timezoneDb.jdbc.update("INSERT INTO community_bots(id,name,personality,enabled,participation) VALUES(1,'时区测试','简洁',TRUE,50)");
            // 重现旧写法：Java Date 按上海连接时区转换，UTC 数据库却把字面时间当成 UTC。
            timezoneDb.jdbc.update("INSERT INTO community_events(event_key,event_type,bot_id,post_id,root_event_id,available_at) "
                +"VALUES('old-java-date','ARTICLE_PUBLISHED',1,2,?,?)",ROOT,Date.from(java.time.Instant.now().plusSeconds(30)));
            long oldDelay=timezoneDb.jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,created_at,available_at) "
                +"FROM community_events WHERE event_key='old-java-date'",Long.class);
            assertTrue(oldDelay>=28829 && oldDelay<=28831,"旧日期绑定应重现多出8小时，实际="+oldDelay);
            for(int delay:List.of(20,90)) {
                CommunityEvent event=new CommunityEvent();event.setEventType("ARTICLE_PUBLISHED");
                event.setBotId(1L);event.setPostId(2L);event.setRootEventId(ROOT);
                assertEquals(1,timezoneMapper.insertEvent("db-clock-"+delay,event,delay));
                long actual=timezoneDb.jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,created_at,available_at) "
                    +"FROM community_events WHERE event_key=?",Long.class,"db-clock-"+delay);
                assertEquals(delay,actual);
            }
            var events=timezoneMapper.pendingEvents(1L,2L);
            var minimum=events.stream().filter(event -> event.getDueSeconds()<25).findFirst().orElseThrow();
            assertTrue(minimum.getDueSeconds()>=18 && minimum.getDueSeconds()<=20);
            long databaseNow=timezoneDb.jdbc.queryForObject("SELECT CAST(UNIX_TIMESTAMP(NOW(3))*1000 AS SIGNED)",Long.class);
            assertTrue(minimum.getAvailableAtEpochMs()-databaseNow>=18000 && minimum.getAvailableAtEpochMs()-databaseNow<=20000);
            timezoneDb.jdbc.update("UPDATE community_events SET available_at=TIMESTAMPADD(SECOND,-5,NOW(3)), "
                +"lease_token='expired',lease_until=TIMESTAMPADD(SECOND,-2,NOW(3)) WHERE available_at < TIMESTAMPADD(SECOND,25,NOW(3))");
            var due=timezoneMapper.pendingEvents(1L,2L).getFirst();
            assertTrue(due.getDueSeconds()<=-5);assertTrue(due.getLeaseRemainingSeconds()<=-2);
            assertEquals("WAITING",due.getStatus());
            assertTrue(timezoneMapper.claimable(20).stream().anyMatch(event -> event.getId().equals(due.getId())));
        }
    }
    @Test void crashedHandoffWithExpiredLeaseCanBeCancelledAndLateWorkerCannotPublish() {
        db.jdbc.update("UPDATE community_events SET lease_token='crashed',lease_until=TIMESTAMPADD(SECOND,-1,NOW()) WHERE id=7");
        assertEquals("WAITING",service.pendingEvents(1L,2L).getFirst().getStatus());
        assertTrue(service.cancelPendingEvent(7L).cancelled());
        assertEquals(1706,assertThrows(BusinessException.class,()->service.publish(publishRequest())).getCode());
    }
    @Test void nightModelQuotaUsesEpochBoundsWhilePublicationAndCooldownRemainCompatible() throws Exception {
        java.time.Instant fixed=java.time.Instant.parse("2026-10-09T17:00:00Z"); // 北京次日 01:00
        try(var timezoneDb=new MysqlFixture(Map.of("serverTimezone","Asia/Shanghai",
                "forceConnectionTimeZoneToSession","false","sessionVariables","time_zone='+00:00',timestamp="+fixed.getEpochSecond()),
                "mapper/CommentsMapper.xml")) {
            timezoneDb.session.getConfiguration().addMapper(CommunityMapper.class);
            var timezoneMapper=timezoneDb.session.getMapper(CommunityMapper.class);
            assertEquals(fixed.getEpochSecond(),timezoneDb.jdbc.queryForObject("SELECT UNIX_TIMESTAMP(NOW())",Long.class));
            Date[] day=CommunityService.dayRange(fixed);
            assertEquals(1,timezoneMapper.recordAttempt(TASK,1,1L,2L,true,""));
            long trueCount=timezoneDb.jdbc.queryForObject("SELECT COUNT(*) FROM community_attempts "
                +"WHERE allowed=TRUE AND created_at>=FROM_UNIXTIME(?) AND created_at<FROM_UNIXTIME(?)",Long.class,
                day[0].getTime()/1000,day[1].getTime()/1000);
            assertEquals(1,trueCount);
            // 旧 Date 参数查询仍可复现漏计，修复后的全站/角色查询应包含同一条夜间尝试。
            assertEquals(0,timezoneDb.jdbc.queryForObject("SELECT COUNT(*) FROM community_attempts "
                +"WHERE allowed=TRUE AND created_at>=? AND created_at<?",Integer.class,day[0],day[1]));
            assertEquals(1,timezoneMapper.attemptCount(null,day[0],day[1]));
            assertEquals(1,timezoneMapper.attemptCount(1L,day[0],day[1]));
            assertEquals(0,timezoneMapper.attemptCount(2L,day[0],day[1]));
            assertEquals(1,timezoneMapper.recordPublication(TASK,1L,2L,99L,Date.from(fixed)));
            assertEquals(1,timezoneMapper.publicationCount(null,null,day[0],day[1]));
            assertEquals(28800L,timezoneDb.jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,NOW(),created_at) "
                +"FROM community_publications WHERE task_id=?",Long.class,TASK));
            // 发布表写入与查询均经过同样的日期转换，现有冷却时间往返不会额外差 8 小时。
            assertEquals(fixed.toEpochMilli(),timezoneMapper.lastPublication(1L,2L).getTime());
            assertEquals(10000L,Date.from(fixed.plusSeconds(10)).getTime()-timezoneMapper.lastPublication(1L,2L).getTime());
        }
    }
    @Test void lostAcknowledgementAfterPublicationCannotPretendTheCommentWasCancelledEvenAfterWithdrawal() {
        db.jdbc.update("UPDATE community_events SET lease_token='lost-ack',lease_until=TIMESTAMPADD(SECOND,600,NOW()) WHERE id=7");
        long comment=service.publish(publishRequest()).commentId();
        db.jdbc.update("UPDATE community_events SET lease_until=TIMESTAMPADD(SECOND,-1,NOW()) WHERE id=7");
        var result=service.cancelPendingEvent(7L);
        assertFalse(result.cancelled());assertEquals(comment,result.publishedCommentId());
        assertEquals("ARTICLE_PUBLISHED",mapper.event(7L).getEventType());
        service.withdrawComment(comment);
        assertFalse(service.cancelPendingEvent(7L).cancelled());
    }
    @Test void concurrentManualInvitesCreateOnlyOneTaskAndWithdrawalHidesHumanDescendantsAndMemorySources() throws Exception {
        db.jdbc.update("DELETE FROM community_events");
        db.jdbc.update("DELETE FROM community_chains");
        try(var pool=Executors.newFixedThreadPool(8)) {
            var requests=new ArrayList<Callable<Integer>>();
            for(int i=0;i<8;i++) requests.add(()->service.invite(2L,List.of(1L)).queued());
            int total=0;
            for(var result:pool.invokeAll(requests)) total+=result.get(15,TimeUnit.SECONDS);
            assertEquals(1,total);
        }
        String root=db.jdbc.queryForObject("SELECT root_event_id FROM community_events",String.class);
        var req=new CommunityReq.Publish(TASK,1L,2L,null,null,root,service.context(1L,2L,null).contextVersion(),"AI 评论");
        long comment=service.publish(req).commentId();
        db.jdbc.update("INSERT INTO comments(post_id,user_id,parent_id,content) VALUES(2,1,?,'真人回复')",comment);
        service.withdrawComment(comment);
        assertEquals(0,db.session.getMapper(CommentsMapper.class).countCommentsByPostId(2L));
        assertEquals(2,db.jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE deleted_at IS NOT NULL",Integer.class));
        assertTrue(service.visibility(new CommunityReq.Visibility(List.of(2L),List.of(comment))).visibleCommentIds().isEmpty());
        assertEquals(comment,service.publish(req).commentId());
        assertEquals(1,db.jdbc.queryForObject("SELECT COUNT(*) FROM community_publications",Integer.class));
        assertEquals(0,service.invite(2L,List.of(1L)).queued());
    }
}
