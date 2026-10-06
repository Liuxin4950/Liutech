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
        assertEquals("SKIP",CommunityDecision.parse("[SKIP] 本轮没有新观点",mapper,Set.of()).decision());
    }
    @Test void replyMustUseAnActuallyReadTarget() {
        String text="{\"decision\":\"REPLY\",\"reason\":\"有观点补充\",\"content\":\"我补充一点\",\"targetCommentId\":12}";
        assertEquals(12L,CommunityDecision.parse(text,mapper,Set.of(12L)).targetCommentId());
        assertThrows(AIServiceException.ModelException.class,()->CommunityDecision.parse(text,mapper,Set.of(11L)));
    }
    @Test void malformedAndEmptyStructuredResponsesNeverBecomeComments() {
        for(String text: new String[]{"", "```json {} ```", "{\"decision\":\"COMMENT\"}",
                "{\"decision\":\"COMMENT\",\"reason\":\"补充\",\"content\":\" \",\"targetCommentId\":null}",
                "{\"decision\":\"SKIP\",\"reason\":\"没有补充\",\"content\":null,\"targetCommentId\":null} {}"}) {
            assertThrows(AIServiceException.ModelException.class,()->CommunityDecision.parse(text,mapper,Set.of()),text);
        }
    }
    @Test void simpleMarkersKeepAutonomousChoiceInsteadOfReplacingItWithTheTrigger() {
        var reply=CommunityDecision.parse("[REPLY:12]\n这个补充让我想到缓存失效的例子。",mapper,Set.of(11L,12L),11L);
        assertEquals(12L,reply.targetCommentId());
        assertEquals("COMMENT",CommunityDecision.parse("[COMMENT]\n这篇文章的实例很清楚。",mapper,Set.of(11L),11L).decision());
        assertThrows(AIServiceException.ModelException.class,()->CommunityDecision.parse("[REPLY:999] 回复其他线程",mapper,Set.of(11L),11L));
        assertEquals(11L,CommunityDecision.parse("我也遇到过这个问题。",mapper,Set.of(11L),11L).targetCommentId());
    }
    @Test void legacyJsonAcceptsFencesNumericStringTargetsAndAdditionalFields() {
        var reply=CommunityDecision.parse("```json\n{\"decision\":\"REPLY\",\"content\":\"可以补充一个例子\",\"targetCommentId\":\"12\",\"extra\":true}\n```",mapper,Set.of(12L));
        assertEquals("REPLY",reply.decision());assertEquals(12L,reply.targetCommentId());
    }
    @Test void oversizedCompletedContentIsBoundedWithoutSplittingUnicode() {
        var decision=CommunityDecision.parse("[COMMENT] "+"字".repeat(999)+"😀补充",mapper,Set.of());
        assertTrue(decision.contentTruncated());
        assertEquals(999,decision.content().length());
    }
}
