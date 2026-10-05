package chat.liuxin.ai.service.community;

import chat.liuxin.ai.dto.community.CommunityDecision;
import chat.liuxin.ai.infra.exception.AIServiceException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CommunityDecisionTest {
    private final ObjectMapper mapper=new ObjectMapper();
    @Test void skipIsExplicitAndNullInsteadOfAnEmptyComment() {
        var decision=CommunityDecision.parse("{\"decision\":\"SKIP\",\"reason\":\"没有补充内容\",\"content\":null,\"targetCommentId\":null}",mapper,Set.of());
        assertNull(decision.content());
        assertThrows(AIServiceException.ModelException.class,()->CommunityDecision.parse("{\"decision\":\"SKIP\",\"reason\":\"无内容\",\"content\":\"\",\"targetCommentId\":null}",mapper,Set.of()));
    }
    @Test void replyMustUseAnActuallyReadTarget() {
        String text="{\"decision\":\"REPLY\",\"reason\":\"有观点补充\",\"content\":\"我补充一点\",\"targetCommentId\":12}";
        assertEquals(12L,CommunityDecision.parse(text,mapper,Set.of(12L)).targetCommentId());
        assertThrows(AIServiceException.ModelException.class,()->CommunityDecision.parse(text,mapper,Set.of(11L)));
    }
    @Test void malformedTruncatedAndOversizedResponsesNeverBecomeComments() {
        for(String text: new String[]{"", "```json {} ```", "{\"decision\":\"COMMENT\"}",
                "{\"decision\":\"COMMENT\",\"reason\":12,\"content\":\"你好\",\"targetCommentId\":null}",
                "{\"decision\":\"COMMENT\",\"reason\":\"补充\",\"content\":\" \",\"targetCommentId\":null}",
                "{\"decision\":\"COMMENT\",\"reason\":\"补充\",\"content\":\""+"字".repeat(1001)+"\",\"targetCommentId\":null}",
                "{\"decision\":\"SKIP\",\"reason\":\"没有补充\",\"content\":null,\"targetCommentId\":null} {}"}) {
            assertThrows(AIServiceException.ModelException.class,()->CommunityDecision.parse(text,mapper,Set.of()),text);
        }
    }
}
