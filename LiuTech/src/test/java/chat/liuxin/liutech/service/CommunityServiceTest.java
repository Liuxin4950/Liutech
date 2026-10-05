package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.*;
import chat.liuxin.liutech.model.*;
import chat.liuxin.liutech.req.CommunityReq;
import chat.liuxin.liutech.resp.CommunityResp;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.mockito.quality.Strictness;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 业务边界测试；SQL租约和真实并发另由隔离数据库验收。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.LENIENT)
class CommunityServiceTest {
    @Mock CommunityMapper mapper;
    @Mock CommentsMapper comments;
    @InjectMocks CommunityService service;
    CommunityBot bot;
    CommunitySettings settings;
    Posts post;
    CommunityPostState state;
    static final String TASK="2c8dc80c-b09c-4327-9a67-9ba53d4a6800";
    static final String ROOT="848ca02c-0eaa-4bf8-97c1-fb5fd3cab0e2";

    @BeforeEach void setup() {
        bot=new CommunityBot();bot.setId(1L);bot.setName("角色");bot.setEnabled(true);bot.setParticipation(50);bot.setVersion(1L);
        settings=new CommunitySettings();settings.setEnabled(true);settings.setVersion(1L);
        settings.setBotDailyCommentLimit(20);settings.setSiteDailyCommentLimit(100);settings.setPostDailyCommentLimit(20);
        settings.setBotDailyTaskLimit(40);settings.setSiteDailyTaskLimit(200);settings.setCooldownSeconds(30);
        settings.setMinDelaySeconds(20);settings.setMaxDelaySeconds(90);settings.setMaxChainComments(4);
        post=new Posts();post.setId(2L);post.setStatus("published");post.setTitle("Java");post.setContent("文章正文");post.setUpdatedAt(new Date());
        state=new CommunityPostState();state.setPostId(2L);state.setEnabled(true);state.setVersion(1L);
        when(mapper.bot(1L)).thenReturn(bot);when(mapper.lockBot(1L)).thenReturn(bot);
        when(mapper.settings()).thenReturn(settings);when(mapper.lockSettings()).thenReturn(settings);
        when(mapper.publicPost(2L)).thenReturn(post);when(mapper.lockPost(2L)).thenReturn(post);
        when(mapper.postState(2L)).thenReturn(state);when(comments.selectRecentForCommunity(2L)).thenReturn(List.of());
        when(mapper.knowledge(1L)).thenReturn(List.of());when(mapper.bots()).thenReturn(List.of(bot));
        when(mapper.chainCount(ROOT,2L)).thenReturn(0);
        doAnswer(invocation->{invocation.<Comments>getArgument(0).setId(99L);return 1;}).when(comments).insertCommunityComment(any());
    }
    private CommunityReq.Publish request(String version) {
        return new CommunityReq.Publish(TASK,1L,2L,null,null,ROOT,version,"观点");
    }
    @Test void readPreviewWithDisabledSettingsAndBotDoesNotMutateOrExposeOtherRoles() {
        settings.setEnabled(false);bot.setEnabled(false);
        var context=service.context(1L,2L,null);
        assertEquals(bot,context.bot());assertFalse(context.settings().getEnabled());
        verify(mapper).knowledge(1L);verify(mapper,never()).knowledge(2L);
        verify(mapper,never()).lockSettings();verify(mapper,never()).recordPublication(any(),any(),any(),any(),any());
    }
    @Test void realPublishRejectedWhenGlobalOrRolePaused() {
        String version=service.context(1L,2L,null).contextVersion();settings.setEnabled(false);
        var error=assertThrows(BusinessException.class,()->service.publish(request(version)));
        assertEquals(ErrorCode.COMMUNITY_PAUSED.getCode(),error.getCode());
        verify(comments,never()).insertCommunityComment(any());
    }
    @Test void staleArticleOrKnowledgeVersionCannotPublish() {
        String version=service.context(1L,2L,null).contextVersion();bot.setVersion(2L);
        var error=assertThrows(BusinessException.class,()->service.publish(request(version)));
        assertEquals(ErrorCode.COMMUNITY_STALE_CONTEXT.getCode(),error.getCode());
        verify(comments,never()).insertCommunityComment(any());
    }
    @Test void browsingCountersAndDatabaseUpdatedTimestampDoNotInvalidateContext() {
        String before=service.context(1L,2L,null).contextVersion();
        post.setUpdatedAt(Date.from(Instant.now().plusSeconds(20)));post.setViewCount(200);
        assertEquals(before,service.context(1L,2L,null).contextVersion());
    }
    @Test void duplicateReceiptSurvivesCommentDeletionAndNeedsNoFreshModelOrVisibility() {
        when(mapper.publication(TASK)).thenReturn(new CommunityResp.Published(99L,new Date(),true));
        var result=service.publish(request("obsolete"));assertTrue(result.duplicate());assertEquals(99L,result.commentId());
        verify(mapper,never()).lockPost(any());verify(comments,never()).insertCommunityComment(any());
    }
    @Test void sharedChainBudgetStopsParallelBranchAfterFourthComment() {
        when(mapper.chainCount(ROOT,2L)).thenReturn(4);
        var error=assertThrows(BusinessException.class,()->service.publish(request(service.context(1L,2L,null).contextVersion())));
        assertEquals(ErrorCode.COMMUNITY_CHAIN_LIMIT.getCode(),error.getCode());verify(comments,never()).insertCommunityComment(any());
    }
    @Test void quotaAndCooldownCannotBeBypassedByDeletingPublishedComment() {
        when(mapper.publicationCount(isNull(),isNull(),any(),any())).thenReturn(100);
        var error=assertThrows(BusinessException.class,()->service.publish(request(service.context(1L,2L,null).contextVersion())));
        assertEquals(ErrorCode.COMMUNITY_QUOTA_EXCEEDED.getCode(),error.getCode());verify(comments,never()).insertCommunityComment(any());
    }
    @Test void botPublicationDoesNotUseHumanIdentityAndDoesNotInviteItself() {
        var result=service.publish(request(service.context(1L,2L,null).contextVersion()));
        assertEquals(99L,result.commentId());
        ArgumentCaptor<Comments> saved=ArgumentCaptor.forClass(Comments.class);verify(comments).insertCommunityComment(saved.capture());
        assertNull(saved.getValue().getUserId());assertEquals(1L,saved.getValue().getBotId());
        assertNull(saved.getValue().getCreatedBy());verify(mapper,never()).insertEvent(any(),any());
        verify(mapper).incrementChain(ROOT);
    }
    @Test void attemptsPreviewStillConsumesQuotaAndRetryOfSameAttemptIsIdempotent() {
        bot.setEnabled(false);settings.setEnabled(false);
        var req=new CommunityReq.Attempt(TASK,1,1L,2L,true);assertTrue(service.authorizeAttempt(req).allowed());
        verify(mapper).recordAttempt(TASK,1,1L,2L,true,"");
        when(mapper.attempt(TASK,1)).thenReturn(new CommunityResp.Attempt(true,""));
        service.authorizeAttempt(req);verify(mapper,times(1)).recordAttempt(TASK,1,1L,2L,true,"");
    }
    @Test void firstPublicationWhileDisabledIsSeenAndNotReplayedWhenEnabled() {
        settings.setEnabled(false);when(mapper.markPublished(2L)).thenReturn(1,0);
        service.articleSaved(2L);settings.setEnabled(true);service.articleSaved(2L);
        verify(mapper,times(2)).markPublished(2L);verify(mapper,never()).insertChain(any(),any());
    }
    @Test void leaseUsesFreshOpaqueTokenForEachClaimAndAckUsesSameToken() {
        var event=new CommunityEvent();event.setId(7L);when(mapper.claimable(1)).thenReturn(List.of(event));
        service.claim(new CommunityReq.Claim(1,120));String token=event.getLeaseToken();assertNotNull(UUID.fromString(token));
        verify(mapper).lease(7L,token,120);service.ack(7L,token);verify(mapper).ack(7L,token);
    }
    @Test void dailyLimitsUseBeijingDateBoundaries() {
        Date[] range=CommunityService.dayRange(Instant.parse("2026-10-04T16:01:00Z"));
        assertEquals(Instant.parse("2026-10-04T16:00:00Z"),range[0].toInstant());
        assertEquals(Instant.parse("2026-10-05T16:00:00Z"),range[1].toInstant());
    }
}
