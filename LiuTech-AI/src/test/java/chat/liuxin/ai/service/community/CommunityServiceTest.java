package chat.liuxin.ai.service.community;

import chat.liuxin.ai.common.client.BackendApiTransport;
import chat.liuxin.ai.common.monitor.AiMetrics;
import chat.liuxin.ai.common.tools.*;
import chat.liuxin.ai.dto.community.CommunityPreviewRequest;
import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.infra.security.*;
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
    private final CommunityService service=new CommunityService(transport,client,policy,new PromptBudget(new AiChatProperties()),
            new RoleBasedToolRegistry(List.of(new CommunityTools())),mapper,store,json,mock(AiMetrics.class));
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
                    return "{\"decision\":\"SKIP\",\"reason\":\"没有可补充的内容\",\"content\":null,\"targetCommentId\":null}";});
        var result=service.preview(new CommunityPreviewRequest(1L,2L,null));
        assertEquals("SKIP",result.get("decision"));assertEquals(50L,result.get("inputTokens"));assertEquals(20L,result.get("outputTokens"));
        assertFalse(((List<?>)result.get("readTrace")).isEmpty());
        var snapshot=json.valueToTree(result.get("roleSnapshot"));
        assertEquals(7,snapshot.path("version").asInt());
        assertEquals("证据不足时先提问，表达简洁",snapshot.path("systemPrompt").asText());
        verify(transport).internalPost(eq("/internal/community/attempts"),argThat(body->Boolean.TRUE.equals(((Map<?,?>)body).get("preview"))));
        verify(transport,never()).internalPost(eq("/internal/community/comments"),any());
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
