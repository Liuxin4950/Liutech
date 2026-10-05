package chat.liuxin.ai.dto.community;

import chat.liuxin.ai.infra.exception.AIServiceException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.Set;

/** 模型仅提出决策，目标及发布权限由应用再次验证。 */
public record CommunityDecision(String decision, String reason, String content, Long targetCommentId) {
    public static CommunityDecision parse(String text, ObjectMapper mapper, Set<Long> allowedTargets) {
        try {
            JsonNode node = mapper.reader().with(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text);
            if (!node.isObject() || node.size() != 4 || !node.has("decision") || !node.has("reason")
                    || !node.has("content") || !node.has("targetCommentId")) throw invalid();
            if (!node.path("decision").isTextual() || !node.path("reason").isTextual()) throw invalid();
            String action = node.path("decision").asText();
            String reason = node.path("reason").asText();
            if (!Set.of("SKIP", "COMMENT", "REPLY").contains(action) || reason.isBlank() || reason.length() > 300) throw invalid();
            JsonNode targetNode = node.path("targetCommentId");
            Long target = targetNode.isNull() ? null : targetNode.isIntegralNumber() ? targetNode.asLong() : null;
            if (!targetNode.isNull() && target == null) throw invalid();
            JsonNode body = node.path("content");
            if ("SKIP".equals(action)) {
                if (!body.isNull() || target != null) throw invalid();
                return new CommunityDecision(action, reason, null, null);
            }
            if (!body.isTextual() || body.asText().isBlank() || body.asText().length() > 1000) throw invalid();
            if ("COMMENT".equals(action) && target != null) throw invalid();
            if ("REPLY".equals(action) && (target == null || !allowedTargets.contains(target))) throw invalid();
            return new CommunityDecision(action, reason, body.asText().strip(), target);
        } catch (AIServiceException error) { throw error; }
        catch (Exception error) { throw invalid(); }
    }
    private static AIServiceException.ModelException invalid() {
        return new AIServiceException.ModelException("角色决策格式或回复目标无效，本轮未发布");
    }
}
