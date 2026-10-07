package chat.liuxin.ai.service.community;

import chat.liuxin.ai.common.tools.*;
import chat.liuxin.ai.infra.exception.AIServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommunityScopeTest {
    private CommunityScope scope() {
        return new CommunityScope(new ObjectMapper().readTree("""
           {"bot":{"id":1},"post":{"id":2,"content":"abcdef"},"comments":[{"id":3,"botId":null},{"id":4,"botId":1}],
            "knowledge":[{"id":5,"botId":1,"title":"本人资料","content":"本人知识"},{"id":6,"botId":9,"title":"其他角色","content":"私有知识"}]}
           """),List.of());
    }
    @Test void botToolsAreOnlyAvailableInServerCommunityMode() {
        var registry=new RoleBasedToolRegistry(List.of(new CommunityTools()));
        assertEquals(1,registry.getToolsForRoleAndMode("BOT","COMMUNITY").size());
        assertTrue(registry.getToolsForRoleAndMode("ADMIN","COMMUNITY").isEmpty());
        assertTrue(registry.getToolsForRoleAndMode("BOT","CHAT").isEmpty());
        assertTrue(registry.getToolsForRoleAndMode("GUEST","COMMUNITY").isEmpty());
    }
    @Test void toolsBindIdentityAndRejectOtherRoleKnowledge() {
        var scope=scope();var tools=new CommunityTools();var context=new ToolContext(Map.of(CommunityScope.CONTEXT_KEY,scope));
        assertEquals("bc",tools.readCommunityArticle(1,2,context).get("content"));
        assertEquals("本人知识",tools.readRoleKnowledge(5L,0,20,context).get("content"));
        assertThrows(AIServiceException.RequestException.class,()->tools.readRoleKnowledge(6L,0,20,context));
        assertThrows(AIServiceException.RequestException.class,()->tools.readCommunityArticle(0,20,new ToolContext(Map.of())));
    }
    @Test void truncatedToolDeliveryCannotClaimACompleteReadOrAuthorizeReplies() {
        var scope=scope();assertTrue(scope.readTargets().isEmpty());
        scope.toolStarted("readCommunityComments","{}");
        new CommunityTools().readCommunityComments(new ToolContext(Map.of(CommunityScope.CONTEXT_KEY,scope)));
        scope.toolResultDelivered("readCommunityComments","partial response",true);
        assertTrue(scope.readTargets().isEmpty());
        assertNull(scope.readTrace.getFirst().get("end"));
        assertEquals(true,scope.readTrace.getFirst().get("truncated"));
        assertEquals(false,scope.readTrace.getFirst().get("rangeComplete"));
        scope.toolStarted("readCommunityComments","{}");
        new CommunityTools().readCommunityComments(new ToolContext(Map.of(CommunityScope.CONTEXT_KEY,scope)));
        scope.toolResultDelivered("readCommunityComments","complete response",false);
        assertEquals(Set.of(3L),scope.readTargets());
    }
}
