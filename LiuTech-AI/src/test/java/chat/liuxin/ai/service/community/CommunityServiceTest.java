package chat.liuxin.ai.service.community;

import chat.liuxin.ai.common.client.BackendApiTransport;
import chat.liuxin.ai.common.monitor.AiMetrics;
import chat.liuxin.ai.dto.community.CommunityPreviewRequest;
import chat.liuxin.ai.dto.community.CommunityTask;
import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.infra.security.*;
import chat.liuxin.ai.infra.exception.AIServiceException;
import chat.liuxin.ai.mapper.CommunityMapper;
import chat.liuxin.ai.service.SiliconFlowChatClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommunityServiceTest {
    private final ObjectMapper json=new ObjectMapper();
    private final BackendApiTransport transport=mock(BackendApiTransport.class);
    private final SiliconFlowChatClient client=mock(SiliconFlowChatClient.class);
    private final AiModelPolicy policy=mock(AiModelPolicy.class);
    private final CommunityMapper mapper=mock(CommunityMapper.class);
    private final CommunityStore store=mock(CommunityStore.class);
    private final AiMetrics metrics=mock(AiMetrics.class);
    private final CommunityService service=new CommunityService(transport,client,policy,new PromptBudget(new AiChatProperties()),
            mapper,store,json,metrics);
    @Test void disabledCommunityAllowsPreviewButChargesAttemptAndNeverPublishes() {
        when(transport.internalGet(anyString())).thenReturn(json.readTree("""
           {"bot":{"id":1,"name":"测试","enabled":false,"version":7,"systemPrompt":"证据不足时先提问，表达简洁"},"post":{"id":2,"title":"文章","content":"<p>正文</p>"},
            "comments":[],"knowledge":[],"settings":{"enabled":false},"postEnabled":false,"contextVersion":"v1"}
           """));
        when(policy.resolveModelName()).thenReturn("configured-model");
        when(policy.resolveParameters(null,null,"configured-model")).thenReturn(new AiModelPolicy.ModelParameters(.3,1000,50000,48488,false,false,"test"));
        when(transport.internalPost(eq("/internal/community/attempts"),any())).thenReturn(json.readTree("{\"allowed\":true}"));
        when(client.chat(anyList(),eq("configured-model"),eq(.3),eq(1000),eq(SiliconFlowChatClient.ChatMode.COMMUNITY),eq("BOT"),anyMap()))
                .thenAnswer(inv->{
                    List<org.springframework.ai.chat.messages.Message> messages=inv.getArgument(0);
                    assertTrue(messages.get(0).getText().contains("证据不足时先提问，表达简洁"));
                    Map<String,Object> context=inv.getArgument(6);((AiMetrics.UsageTracker)context.get(AiMetrics.UsageTracker.CONTEXT_KEY)).record(50,20);
                    assertFalse(((chat.liuxin.ai.common.client.ModelExecutionPolicy)context.get(chat.liuxin.ai.common.client.ModelExecutionPolicy.CONTEXT_KEY)).toolsEnabled());
                    return "{\"decision\":\"SKIP\",\"reason\":\"没有可补充的内容\",\"content\":null,\"targetCommentId\":null}";});
        var result=service.preview(new CommunityPreviewRequest(1L,2L,null));
        assertEquals("SKIP",result.get("decision"));assertEquals(50L,result.get("inputTokens"));assertEquals(20L,result.get("outputTokens"));
        assertEquals("文章",result.get("postTitle"));assertEquals(1L,result.get("modelRounds"));
        assertEquals(true,result.get("tokenUsageAvailable"));assertEquals(true,result.get("tokenUsageComplete"));
        assertFalse(((List<?>)result.get("readTrace")).isEmpty());
        var snapshot=json.valueToTree(result.get("roleSnapshot"));
        assertEquals(7,snapshot.path("version").asInt());
        assertEquals("证据不足时先提问，表达简洁",snapshot.path("systemPrompt").asText());
        verify(transport).internalPost(eq("/internal/community/attempts"),argThat(body->Boolean.TRUE.equals(((Map<?,?>)body).get("preview"))));
        verify(transport,never()).internalPost(eq("/internal/community/comments"),any());
    }
    private void configuredSnapshot() {
        when(transport.internalGet(anyString())).thenReturn(json.readTree("""
            {"bot":{"id":1,"name":"测试","enabled":true},"post":{"id":2,"title":"缓存设计","content":"文章缓存设计的正文"},
            "comments":[{"id":3,"userId":7,"user":{"username":"读者"},"content":"这会引入延迟吗？"},
                        {"id":4,"userId":8,"user":{"username":"另一位读者"},"content":"可以用失效重建"},
                        {"id":5,"botId":1,"bot":{"name":"测试"},"content":"自己的旧评论"}],
            "knowledge":[{"id":9,"botId":1,"title":"缓存经验","content":"缓存失效可以延迟重建"}],
            "settings":{"enabled":true},"postEnabled":true,"contextVersion":"v2"}
            """));
        when(policy.resolveModelName()).thenReturn("configured-model");
        when(policy.resolveParameters(null,null,"configured-model")).thenReturn(new AiModelPolicy.ModelParameters(.3,1000,10000,8488,false,false,"test"));
        when(transport.internalPost(eq("/internal/community/attempts"),any())).thenReturn(json.readTree("{\"allowed\":true}"));
    }
    @Test void serverPreparesOwnReferencesAndLetsTheModelChooseAnotherActuallyReadComment() {
        configuredSnapshot();
        when(client.chat(anyList(),anyString(),anyDouble(),anyInt(),any(),anyString(),anyMap())).thenAnswer(inv -> {
            List<org.springframework.ai.chat.messages.Message> messages=inv.getArgument(0);
            String prompt=messages.stream().map(org.springframework.ai.chat.messages.Message::getText).reduce("",String::concat);
            assertTrue(prompt.contains("缓存失效可以延迟重建"));assertTrue(prompt.contains("评论ID=4"));
            assertFalse(prompt.contains("自己的旧评论"));assertFalse(prompt.contains("searchRoleKnowledge"));
            return "[REPLY:4]\n这个方法还可以补充一个缓存过期的例子。";
        });
        var result=service.preview(new CommunityPreviewRequest(1L,2L,3L));
        assertEquals("REPLY",result.get("decision"));assertEquals(4L,result.get("targetCommentId"));
        assertEquals("这会引入延迟吗？",result.get("commentPreview"));assertEquals("可以用失效重建",result.get("targetCommentPreview"));
        assertEquals("另一位读者",result.get("targetAuthorName"));
        verify(store,never()).remember(anyString(),anyLong(),anyLong(),anyLong(),anyLong(),anyString(),anyList(),anyList());
        verify(transport,never()).internalPost(eq("/internal/community/comments"),any());
    }
    @Test void replyKeepsOwnDirectParentDespiteBusyThreadAndSeparatesArticleFromCommentSpeakers() {
        configuredSnapshot();
        var snapshot=json.readTree("""
            {"bot":{"id":1,"name":"测试","enabled":true},"post":{"id":2,"title":"讨论帖子","content":"这是文章正文。"},
            "knowledge":[],"settings":{"enabled":true},"postEnabled":true,"contextVersion":"v3",
            "comments":[{"id":1,"userId":7,"content":"有人吗？"},
                        {"id":2,"parentId":1,"botId":1,"content":"我之前误把文章里的批评当作你的话了。"},
                        {"id":500,"parentId":2,"userId":7,"content":"你在说什么"}]}
            """
        );
        for(int i=0;i<10;i++) snapshot.withArray("comments").add(json.readTree("{\"id\":"+(600+i)+",\"userId\":8,\"content\":\"无关的较新讨论\"}"));
        when(transport.internalGet(anyString())).thenReturn(snapshot);
        when(client.chat(anyList(),anyString(),anyDouble(),anyInt(),any(),anyString(),anyMap())).thenAnswer(inv -> {
            List<org.springframework.ai.chat.messages.Message> messages=inv.getArgument(0);
            String prompt=messages.stream().map(org.springframework.ai.chat.messages.Message::getText).reduce("",String::concat);
            assertTrue(prompt.contains("我之前误把文章里的批评当作你的话了。"));
            assertTrue(prompt.contains("当前触发的直接前文；自己的历史评论（不可回复）"));
            assertTrue(prompt.contains("作者正文，不是评论者的话"));
            assertTrue(prompt.contains("评论ID=500"));assertTrue(prompt.contains("回复对象=评论ID 2"));
            assertTrue(new PromptBudget(new AiChatProperties()).estimateTokens(messages)<=8488);
            return "[REPLY:500] 刚才我说偏了，我误把文章里的话当成了你的话。你只是来打招呼的。";
        });
        var result=service.preview(new CommunityPreviewRequest(1L,2L,500L));
        assertEquals(500L,result.get("targetCommentId"));
        var trace=json.valueToTree(result.get("readTrace"));
        assertTrue(trace.toString().contains("\"id\":2"));
    }
    @Test void missingDirectParentStopsBeforeModelOrAttemptCharge() {
        configuredSnapshot();
        when(transport.internalGet(anyString())).thenReturn(json.readTree("""
            {"bot":{"id":1,"enabled":true},"post":{"id":2,"title":"文章","content":"正文"},
            "comments":[{"id":3,"parentId":99,"userId":7,"content":"你在说什么"}],"knowledge":[],
            "settings":{"enabled":true},"postEnabled":true,"contextVersion":"v4"}
            """));
        assertThrows(AIServiceException.RequestException.class,()->service.preview(new CommunityPreviewRequest(1L,2L,3L)));
        verifyNoInteractions(client);
        verify(transport,never()).internalPost(eq("/internal/community/attempts"),any());
    }
    @Test void constrainedBudgetStillIncludesArticleAndOwnParentInsteadOfOnlyPersonaAndMemory() {
        configuredSnapshot();
        when(policy.resolveParameters(null,null,"configured-model")).thenReturn(new AiModelPolicy.ModelParameters(.3,1000,8500,6988,false,false,"test"));
        when(transport.internalGet(anyString())).thenReturn(json.readTree("""
            {"bot":{"id":1,"enabled":true},"post":{"id":2,"title":"缓存实践","content":"%s"},
            "comments":[{"id":2,"botId":1,"content":"我说的是文章中的缓存失效重建。"},
                        {"id":3,"parentId":2,"userId":7,"content":"什么意思"}],"knowledge":[],
            "settings":{"enabled":true},"postEnabled":true,"contextVersion":"v5"}
            """.formatted("缓存失效重建的真实正文".repeat(1000))));
        when(client.chat(anyList(),anyString(),anyDouble(),anyInt(),any(),anyString(),anyMap())).thenAnswer(inv -> {
            List<org.springframework.ai.chat.messages.Message> messages=inv.getArgument(0);
            String prompt=messages.stream().map(org.springframework.ai.chat.messages.Message::getText).reduce("",String::concat);
            assertTrue(prompt.contains("缓存失效重建的真实正文"));assertTrue(prompt.contains("我说的是文章中的缓存失效重建。"));
            assertTrue(new PromptBudget(new AiChatProperties()).estimateTokens(messages)<=6988);
            return "[SKIP] 已有充分解释";
        });
        var result=service.preview(new CommunityPreviewRequest(1L,2L,3L));
        boolean articleTruncated=false;
        for(var trace:json.valueToTree(result.get("readTrace"))) if("article".equals(trace.path("source").asText())) articleTruncated=trace.path("truncated").asBoolean();
        assertTrue(articleTruncated);
    }
    @Test void failedDecisionStillStoresContextAndActualUsageAndPreviewDoesNotDeleteMemory() {
        configuredSnapshot();
        when(mapper.memories(1)).thenReturn(List.of(Map.of("id","memory","source_post_id",2L,"source_comment_id",4L,"summary","已失效")));
        when(mapper.memorySources("memory")).thenReturn(List.of(3L,4L));
        when(transport.internalPost(eq("/internal/community/visibility"),any())).thenReturn(json.readTree("{\"visiblePostIds\":[2],\"visibleCommentIds\":[4]}"));
        when(client.chat(anyList(),anyString(),anyDouble(),anyInt(),any(),anyString(),anyMap())).thenAnswer(inv -> {
            Map<String,Object> execution=inv.getArgument(6);((AiMetrics.UsageTracker)execution.get(AiMetrics.UsageTracker.CONTEXT_KEY)).record(50,20);
            return "[REPLY:5] 回复自己";
        });
        assertThrows(AIServiceException.ModelException.class,()->service.preview(new CommunityPreviewRequest(1L,2L,3L)));
        verify(mapper,never()).deleteMemory(anyString(),anyLong());
        verify(mapper).run(anyString(),anyString(),eq(1L),eq(2L),eq("FAILED"),argThat(record -> {
            var saved=json.readTree(record);
            return saved.path("postTitle").asText().equals("缓存设计") && saved.path("commentPreview").asText().equals("这会引入延迟吗？")
                    && saved.path("inputTokens").asLong()==50 && saved.path("outputTokens").asLong()==20
                    && saved.path("tokenUsageComplete").asBoolean();
        }),anyString());
        verify(metrics).recordCompleted(eq("configured-model"),eq("COMMUNITY"),eq(false),anyLong(),argThat(usage -> usage.inputTokens()==50 && usage.outputTokens()==20));
    }
    @Test void oldRunsUseOneMetadataBatchAndKeepTheSavedSnapshotTitle() {
        when(mapper.runs(null,50)).thenReturn(List.of(
                Map.of("id","saved","post_id",2L,"status","PREVIEW","result_json","{\"postTitle\":\"原始标题\"}"),
                Map.of("id","old","post_id",2L,"comment_id",3L,"status","FAILED","result_json","{\"targetCommentId\":4}")));
        when(transport.internalPost(eq("/internal/community/metadata"),any())).thenReturn(json.readTree("""
            {"posts":[{"id":2,"title":"当前标题"}],"comments":[{"id":3,"content":"触发内容"},{"id":4,"content":"回复目标","authorName":"读者"}]}
            """));
        var result=service.runs(null,50);
        assertEquals("原始标题",result.get(0).get("postTitle"));assertEquals("当前标题",result.get(1).get("postTitle"));
        assertEquals("触发内容",result.get(1).get("commentPreview"));assertEquals("读者",result.get(1).get("targetAuthorName"));
        verify(transport,times(1)).internalPost(eq("/internal/community/metadata"),any());verify(transport,never()).internalGet(anyString());
    }
    @Test void taskListKeepsDtoAndFillsDisplayFactsInOneBatch() {
        var first=new CommunityTask();first.setPostId(2L);first.setCommentId(3L);
        var second=new CommunityTask();second.setPostId(2L);second.setCommentId(4L);second.setDecisionJson("{\"postTitle\":\"当轮标题\",\"commentPreview\":\"当轮评论\"}");
        when(mapper.tasks(null,50)).thenReturn(List.of(first,second));
        when(store.savedDecision(second.getDecisionJson())).thenReturn(json.readTree(second.getDecisionJson()));
        when(transport.internalPost(eq("/internal/community/metadata"),any())).thenReturn(json.readTree("{\"posts\":[{\"id\":2,\"title\":\"当前标题\"}],\"comments\":[{\"id\":3,\"content\":\"当前评论\"}]}"));
        var result=service.tasks(null,50);
        assertEquals("当前标题",result.get(0).getPostTitle());assertEquals("当轮标题",result.get(1).getPostTitle());
        assertEquals("当前评论",result.get(0).getCommentPreview());assertEquals("当轮评论",result.get(1).getCommentPreview());
        verify(transport,times(1)).internalPost(eq("/internal/community/metadata"),any());
    }
    @Test void legacyPublishedCopyDoesNotDisplayTheSameGenerationUsageTwice() {
        String attemptOne="{\"taskId\":\"task\",\"postId\":2,\"postTitle\":\"标题\",\"contextVersion\":\"v1\",\"processingTime\":10,\"inputTokens\":50,\"outputTokens\":20,\"attempt\":1,\"content\":\"正文\"}";
        String attemptTwo=attemptOne.replace("\"attempt\":1","\"attempt\":2");
        when(mapper.runs(null,50)).thenReturn(List.of(Map.of("id","published","status","SUCCEEDED","result_json",attemptOne),
                Map.of("id","generated","status","GENERATED","result_json",attemptOne),
                Map.of("id","other-attempt","status","GENERATED","result_json",attemptTwo)));
        var result=service.runs(null,50);
        assertEquals(2,result.size());assertEquals("published",result.get(0).get("id"));assertEquals("other-attempt",result.get(1).get("id"));
        verifyNoInteractions(transport);
    }
    @Test void deletedOriginalCommentInvalidatesItsDerivedMemoryEvenWhenBotCommentRemainsVisible() {
        when(mapper.memories(1)).thenReturn(List.of(Map.of("id","memory","source_post_id",2L,"source_comment_id",4L,"summary","旧回复")));
        when(mapper.memorySources("memory")).thenReturn(List.of(3L,4L));
        when(transport.internalPost(eq("/internal/community/visibility"),any())).thenReturn(json.readTree("{\"visiblePostIds\":[2],\"visibleCommentIds\":[4]}"));
        assertTrue(service.memories(1).isEmpty());
        verify(mapper).deleteMemory("memory",1);
        verifyNoInteractions(client);
    }
}
