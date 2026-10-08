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
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),""),Comments.class);
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
        when(mapper.insertEvent(any(),any())).thenReturn(1);
        when(mapper.lockArticleInvitation(anyLong(),anyLong())).thenReturn(null);
        when(mapper.recentPublicPostIds(10)).thenReturn(List.of(2L));
        CommunityEvent event=new CommunityEvent();event.setId(7L);event.setBotId(1L);event.setPostId(2L);event.setEventType("ARTICLE_PUBLISHED");
        when(mapper.publicationEvent(anyLong(),anyLong(),anyString(),nullable(Long.class))).thenReturn(event);
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
    @Test void replyingToRetiredBotCommentRemainsLegalWithoutInvitingAnotherBotButInvalidExplicitMentionStillFails() {
        Comments parent=new Comments();parent.setId(77L);parent.setPostId(2L);parent.setBotId(1L);
        Comments reply=new Comments();reply.setId(88L);reply.setPostId(2L);reply.setParentId(77L);reply.setContent("真人回复");
        when(comments.selectPublicCommentById(77L)).thenReturn(parent);
        when(mapper.bot(1L)).thenReturn(null);
        assertDoesNotThrow(()->service.humanCommentCreated(reply,null));
        bot.setEnabled(false);when(mapper.bot(1L)).thenReturn(bot);
        assertDoesNotThrow(()->service.humanCommentCreated(reply,List.of()));
        verify(mapper,never()).bots();
        verify(mapper,never()).insertChain(any(),any());
        verify(mapper,never()).insertEvent(any(),any());
        when(mapper.bot(1L)).thenReturn(null);
        var error=assertThrows(BusinessException.class,()->service.humanCommentCreated(reply,List.of(1L)));
        assertEquals(ErrorCode.COMMUNITY_BOT_NOT_FOUND.getCode(),error.getCode());
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
    @Test void publicationCommittedDuringLockWaitReturnsBeforeFreshQuotaAndContextChecks() {
        when(mapper.publication(TASK)).thenReturn(null,new CommunityResp.Published(99L,new Date(),true));
        var result=service.publish(request("obsolete"));
        assertTrue(result.duplicate());assertEquals(99L,result.commentId());
        verify(mapper,times(2)).publication(TASK);
        verify(mapper,never()).publicationCount(any(),any(),any(),any());
        verify(comments,never()).insertCommunityComment(any());
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
    @Test void articleInvitationCanChooseToReplyToAnExistingVisibleComment() {
        Comments target=new Comments();target.setId(88L);target.setPostId(2L);target.setContent("当前公开评论");
        when(comments.selectRecentForCommunity(2L)).thenReturn(List.of(target));
        when(mapper.lockComment(88L)).thenReturn(target);
        String version=service.context(1L,2L,null).contextVersion();
        var req=new CommunityReq.Publish(TASK,1L,2L,88L,null,ROOT,version,"文章观点");
        service.publish(req);
        ArgumentCaptor<Comments> saved=ArgumentCaptor.forClass(Comments.class);
        verify(comments).insertCommunityComment(saved.capture());
        assertEquals(88L,saved.getValue().getParentId());
        verify(mapper).lockComment(88L);
    }
    @Test void modelMayChooseAnotherVisibleThreadCommentAsItsReplyTarget() {
        Comments trigger=new Comments();trigger.setId(77L);trigger.setPostId(2L);trigger.setContent("触发评论");
        Comments other=new Comments();other.setId(88L);other.setPostId(2L);other.setContent("线程其他评论");
        when(comments.selectPublicCommentById(77L)).thenReturn(trigger);
        when(mapper.threadComments(2L,77L)).thenReturn(List.of(trigger,other));
        when(mapper.lockComment(88L)).thenReturn(other);
        String version=service.context(1L,2L,77L).contextVersion();
        var req=new CommunityReq.Publish(TASK,1L,2L,88L,77L,ROOT,version,"直接回复");
        service.publish(req);
        ArgumentCaptor<Comments> saved=ArgumentCaptor.forClass(Comments.class);
        verify(comments).insertCommunityComment(saved.capture());
        assertEquals(88L,saved.getValue().getParentId());
        verify(mapper,never()).lockComment(77L);
    }
    @Test void commentContextKeepsOldTriggerAndDirectAiParentAlongsideRecentReplies() {
        Comments parent=new Comments();parent.setId(10L);parent.setPostId(2L);parent.setBotId(1L);parent.setContent("AI 原话");
        Comments trigger=new Comments();trigger.setId(11L);trigger.setPostId(2L);trigger.setParentId(10L);trigger.setContent("你在说什么");
        List<Comments> thread=new ArrayList<>(List.of(parent,trigger));
        for (long id=100;id<200;id++) {
            Comments recent=new Comments();recent.setId(id);recent.setPostId(2L);recent.setParentId(10L);thread.add(recent);
        }
        when(comments.selectPublicCommentById(11L)).thenReturn(trigger);
        when(mapper.threadComments(2L,11L)).thenReturn(thread);
        var context=service.context(1L,2L,11L);
        assertEquals(102,context.comments().size());
        assertEquals("AI 原话",context.comments().getFirst().getContent());
        assertEquals(10L,context.comments().get(1).getParentId());
        assertEquals("文章正文",context.post().content());
        verify(comments,never()).selectRecentForCommunity(2L);
    }
    @Test void threadWithoutTriggerOrItsDirectParentCannotGenerateFromUnrelatedRecentReplies() {
        Comments trigger=new Comments();trigger.setId(11L);trigger.setPostId(2L);trigger.setParentId(10L);
        Comments recent=new Comments();recent.setId(99L);recent.setPostId(2L);
        when(comments.selectPublicCommentById(11L)).thenReturn(trigger);
        when(mapper.threadComments(2L,11L)).thenReturn(List.of(recent),List.of(trigger,recent),List.of());
        for (int i=0;i<3;i++) {
            var error=assertThrows(BusinessException.class,()->service.context(1L,2L,11L));
            assertEquals(ErrorCode.PARENT_COMMENT_NOT_FOUND.getCode(),error.getCode());
        }
        verify(mapper,never()).knowledge(any());
    }
    @Test void hiddenOrCrossPostTriggerIsRejectedBeforeReadingThreadOrRoleKnowledge() {
        Comments otherPost=new Comments();otherPost.setId(11L);otherPost.setPostId(3L);
        when(comments.selectPublicCommentById(11L)).thenReturn(null,otherPost);
        for (int i=0;i<2;i++) {
            var error=assertThrows(BusinessException.class,()->service.context(1L,2L,11L));
            assertEquals(ErrorCode.PARENT_COMMENT_NOT_FOUND.getCode(),error.getCode());
        }
        verify(mapper,never()).threadComments(any(),any());
        verify(mapper,never()).knowledge(any());
    }
    @Test void modelCannotReplyToItselfEvenWhenOwnCommentIsVisibleInContext() {
        Comments own=new Comments();own.setId(88L);own.setPostId(2L);own.setBotId(1L);own.setContent("我的旧评论");
        when(comments.selectRecentForCommunity(2L)).thenReturn(List.of(own));
        when(mapper.lockComment(88L)).thenReturn(own);
        String version=service.context(1L,2L,null).contextVersion();
        var req=new CommunityReq.Publish(TASK,1L,2L,88L,null,ROOT,version,"自我回复");
        var error=assertThrows(BusinessException.class,()->service.publish(req));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(),error.getCode());
        assertEquals("角色不能回复自己的评论",error.getMessage());
        verify(comments,never()).insertCommunityComment(any());
    }
    @Test void visibleReplyTargetStillMustBelongToTheCurrentReadableContext() {
        Comments outside=new Comments();outside.setId(88L);outside.setPostId(2L);outside.setContent("本轮未读取");
        when(mapper.lockComment(88L)).thenReturn(outside);
        String version=service.context(1L,2L,null).contextVersion();
        var req=new CommunityReq.Publish(TASK,1L,2L,88L,null,ROOT,version,"越界回复");
        var error=assertThrows(BusinessException.class,()->service.publish(req));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(),error.getCode());
        verify(comments,never()).insertCommunityComment(any());
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
    @Test void backfillDefaultsToTenAndFallsBackWhenNoInterestMatches() {
        bot.setInterests("天文,摄影");
        var result=service.backfill(new CommunityReq.Backfill(null,null));
        assertEquals(new CommunityResp.Backfill(1,0,1),result);
        verify(mapper).recentPublicPostIds(10);
        verify(mapper).markPublished(2L);
        ArgumentCaptor<CommunityEvent> event=ArgumentCaptor.forClass(CommunityEvent.class);
        verify(mapper).insertEvent(eq("ARTICLE_PUBLISHED:2:1"),event.capture());
        assertEquals("ARTICLE_PUBLISHED",event.getValue().getEventType());
        assertNull(event.getValue().getCommentId());
    }
    @Test void automaticPublicationFallsBackButStillExcludesDisabledAndZeroParticipationRoles() {
        bot.setInterests("摄影");
        CommunityBot paused=new CommunityBot();paused.setId(3L);paused.setEnabled(false);paused.setParticipation(100);
        CommunityBot silent=new CommunityBot();silent.setId(4L);silent.setEnabled(true);silent.setParticipation(0);
        when(mapper.bots()).thenReturn(List.of(paused,silent,bot));
        when(mapper.markPublished(2L)).thenReturn(1);
        service.articleSaved(2L);
        verify(mapper).insertEvent(eq("ARTICLE_PUBLISHED:2:1"),any());
        verify(mapper,times(1)).insertEvent(any(),any());
    }
    @Test void matchingRolesRankAheadAndEachArticleSharesOneRootForAtMostTwoBots() {
        bot.setInterests("Java");bot.setParticipation(1);
        CommunityBot other=new CommunityBot();other.setId(3L);other.setEnabled(true);other.setParticipation(100);other.setInterests("摄影");
        CommunityBot third=new CommunityBot();third.setId(4L);third.setEnabled(true);third.setParticipation(50);
        when(mapper.bots()).thenReturn(List.of(other,third,bot));
        var result=service.backfill(new CommunityReq.Backfill(10,null));
        assertEquals(2,result.queued());
        ArgumentCaptor<CommunityEvent> events=ArgumentCaptor.forClass(CommunityEvent.class);
        verify(mapper,times(2)).insertEvent(any(),events.capture());
        assertEquals(List.of(1L,3L),events.getAllValues().stream().map(CommunityEvent::getBotId).toList());
        assertEquals(events.getAllValues().get(0).getRootEventId(),events.getAllValues().get(1).getRootEventId());
        verify(mapper,times(1)).insertChain(any(),eq(2L));
    }
    @Test void initialOrManualInvitationReceiptsPreventBackfillReplayEvenAfterAcknowledgementOrDeletion() {
        when(mapper.lockArticleInvitation(2L,1L)).thenReturn(null,7L);
        assertEquals(new CommunityResp.Backfill(1,0,1),service.backfill(new CommunityReq.Backfill(10,null)));
        assertEquals(new CommunityResp.Backfill(0,1,1),service.backfill(new CommunityReq.Backfill(10,null)));
        verify(mapper,times(1)).insertEvent(any(),any());
        when(mapper.lockArticleInvitation(2L,1L)).thenReturn(null);
        when(mapper.lockArticlePublication(1L,2L)).thenReturn(TASK);
        assertEquals(new CommunityResp.Backfill(0,1,1),service.backfill(new CommunityReq.Backfill(10,null)));
        verify(mapper,times(1)).insertEvent(any(),any());
    }
    @Test void newBackfillBotReusesExistingArticleRootAndRepeatedInviteCannotCreateAnotherTask() {
        when(mapper.initialArticleRoot(2L)).thenReturn(ROOT);
        assertEquals(1,service.backfill(new CommunityReq.Backfill(10,List.of(1L))).queued());
        ArgumentCaptor<CommunityEvent> events=ArgumentCaptor.forClass(CommunityEvent.class);
        verify(mapper).insertEvent(eq("ARTICLE_PUBLISHED:2:1"),events.capture());
        assertEquals(ROOT,events.getValue().getRootEventId());
        verify(mapper,never()).insertChain(any(),any());
        when(mapper.lockArticleInvitation(2L,1L)).thenReturn(7L);
        assertEquals(0,service.invite(2L,List.of(1L)).queued());
        assertEquals(0,service.invite(2L,List.of(1L)).queued());
        verify(mapper,never()).insertChain(any(),eq(2L));
        verify(mapper,never()).insertEvent(startsWith("MANUAL_INVITE:"),any());
    }
    @Test void cancelledEventRejectsEvenACachedDecisionAndReceiptStillWinsForAnAlreadyPublishedTask() {
        CommunityEvent event=new CommunityEvent();event.setEventType("CANCELLED");
        when(mapper.publicationEvent(1L,2L,ROOT,null)).thenReturn(event);
        var error=assertThrows(BusinessException.class,()->service.publish(request("cached-version")));
        assertEquals(ErrorCode.COMMUNITY_TASK_CANCELLED.getCode(),error.getCode());
        verify(comments,never()).insertCommunityComment(any());
        when(mapper.publication(TASK)).thenReturn(new CommunityResp.Published(99L,new Date(),true));
        assertEquals(99L,service.publish(request("cached-version")).commentId());
    }
    @Test void cancellationSerializesWithPublicationAndAnExistingReceiptRequiresWithdrawal() {
        var event=new CommunityEvent();event.setId(7L);event.setBotId(1L);event.setPostId(2L);
        when(mapper.event(7L)).thenReturn(event);
        var req=new CommunityReq.Cancel(TASK,7L,1L,2L);
        assertTrue(service.cancelTask(req).cancelled());
        InOrder order=inOrder(mapper);
        order.verify(mapper).lockPost(2L);order.verify(mapper).lockSettings();order.verify(mapper).lockBot(1L);
        order.verify(mapper).publication(TASK);order.verify(mapper).lockEvent(7L);order.verify(mapper).cancelEvent(7L);
        when(mapper.publication(TASK)).thenReturn(new CommunityResp.Published(99L,new Date(),true));
        var published=service.cancelTask(req);
        assertFalse(published.cancelled());assertEquals(99L,published.publishedCommentId());
        verify(mapper,times(1)).cancelEvent(7L);
    }
    @Test void pendingEventCancellationRejectsTheHandoffWindowAndValidatesTaskBinding() {
        var event=new CommunityEvent();event.setId(7L);event.setBotId(1L);event.setPostId(2L);event.setEventType("ARTICLE_PUBLISHED");
        when(mapper.event(7L)).thenReturn(event);when(mapper.lockEvent(7L)).thenReturn(event);
        assertTrue(service.cancelPendingEvent(7L).cancelled());
        event.setLeaseToken("already-claimed");
        event.setLeaseUntil(new Date(System.currentTimeMillis()+600000));
        assertFalse(service.cancelPendingEvent(7L).cancelled());
        verify(mapper,times(1)).cancelEvent(7L);
        assertThrows(BusinessException.class,()->service.cancelTask(new CommunityReq.Cancel(TASK,7L,9L,2L)));
    }
    @Test void withdrawalKeepsPublicationReceiptsAndSoftDeletesTheEntireReplyBranchAfterArticleLock() {
        Comments root=new Comments();root.setId(99L);root.setBotId(1L);root.setPostId(2L);
        when(comments.selectCommentsForAdminById(99L)).thenReturn(root);
        when(comments.selectAllDescendantIds(List.of(99L))).thenReturn(List.of(100L,101L));
        service.withdrawComment(99L);
        InOrder order=inOrder(mapper,comments);
        order.verify(mapper).lockPost(2L);order.verify(mapper).lockSettings();order.verify(mapper).lockPostComments(2L);
        order.verify(comments).selectAllDescendantIds(List.of(99L));
        verify(comments).update(isNull(),argThat((com.baomidou.mybatisplus.core.conditions.Wrapper<Comments> wrapper)->{
            wrapper.getSqlSegment();
            var values=((com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Comments>)wrapper).getParamNameValuePairs().values();
            return values.containsAll(List.of(99L,100L,101L,2L));
        }));
        verify(mapper,never()).recordPublication(any(),any(),any(),any(),any());
        root.setBotId(null);
        assertThrows(BusinessException.class,()->service.withdrawComment(99L));
    }
    @Test void completedSharedArticleChainDoesNotSchedulePaidGenerationAgain() {
        when(mapper.initialArticleRoot(2L)).thenReturn(ROOT);
        when(mapper.chainCount(ROOT,2L)).thenReturn(4);
        assertEquals(new CommunityResp.Backfill(0,1,1),service.backfill(new CommunityReq.Backfill(10,null)));
        verify(mapper,never()).insertEvent(any(),any());
    }
    @Test void backfillHonorsGlobalAndArticlePauseAndRejectsOversizedBatches() {
        assertThrows(BusinessException.class,()->service.backfill(new CommunityReq.Backfill(21,null)));
        verify(mapper,never()).recentPublicPostIds(21);
        settings.setEnabled(false);
        var paused=assertThrows(BusinessException.class,()->service.backfill(new CommunityReq.Backfill(10,null)));
        assertEquals(ErrorCode.COMMUNITY_PAUSED.getCode(),paused.getCode());
        verify(mapper,never()).markPublished(any());
        settings.setEnabled(true);state.setEnabled(false);
        assertEquals(new CommunityResp.Backfill(0,1,1),service.backfill(new CommunityReq.Backfill(10,null)));
        verify(mapper,never()).insertEvent(any(),any());
    }
    @Test void batchBackfillLocksAllArticlesInAscendingOrderBeforeSettingsAndIgnoresNoLongerPublicPosts() {
        when(mapper.recentPublicPostIds(10)).thenReturn(List.of(8L,2L,5L));
        Posts older=new Posts();older.setId(5L);older.setStatus("published");
        when(mapper.lockPost(5L)).thenReturn(older);when(mapper.postState(5L)).thenReturn(state);
        Posts removed=new Posts();removed.setId(8L);removed.setStatus("draft");
        when(mapper.lockPost(8L)).thenReturn(removed);
        var result=service.backfill(new CommunityReq.Backfill(10,null));
        assertEquals(new CommunityResp.Backfill(2,0,2),result);
        InOrder locks=inOrder(mapper);
        locks.verify(mapper).lockPost(2L);locks.verify(mapper).lockPost(5L);locks.verify(mapper).lockPost(8L);
        locks.verify(mapper).lockSettings();
        verify(mapper,never()).ensurePost(8L);
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
    @Test void metadataUsesBatchedVisibleFactsAndOnlyReturnsHumanOrBotDisplayName() {
        Comments human=new Comments();human.setId(7L);human.setContent("真人评论");
        Users author=new Users();author.setUsername("作者");human.setUser(author);
        Comments role=new Comments();role.setId(8L);role.setBotId(1L);role.setContent("角色评论");
        role.setBot(new CommunityResp.BotInfo(1L,"机器人",null));
        when(mapper.publicPostMetadata(List.of(2L,5L))).thenReturn(List.of(post));
        when(comments.selectPublicCommentsByIds(List.of(7L,8L,9L))).thenReturn(List.of(human,role));
        var result=service.metadata(new CommunityReq.Metadata(List.of(2L,2L,5L),List.of(7L,8L,9L)));
        assertEquals(List.of(new CommunityResp.PostMetadata(2L,"Java")),result.posts());
        assertEquals(List.of(new CommunityResp.CommentMetadata(7L,"真人评论","作者"),
            new CommunityResp.CommentMetadata(8L,"角色评论","机器人")),result.comments());
        verify(mapper).publicPostMetadata(List.of(2L,5L));
        verify(comments).selectPublicCommentsByIds(List.of(7L,8L,9L));
        verify(comments,never()).selectPublicCommentById(any());
    }
    @Test void emptyMetadataDoesNotGenerateInvalidInClausesOrDatabaseQueries() {
        assertEquals(new CommunityResp.Metadata(List.of(),List.of()),service.metadata(new CommunityReq.Metadata(List.of(),List.of())));
        verify(mapper,never()).publicPostMetadata(any());
        verify(comments,never()).selectPublicCommentsByIds(any());
    }
}
