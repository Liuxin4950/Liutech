package chat.liuxin.ai.dto.community;

import chat.liuxin.ai.infra.exception.AIServiceException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.regex.Pattern;

/** 模型自主发言或沉默，回复对象只能来自本轮实际给出的公开候选。 */
public record CommunityDecision(String decision, String reason, String content, Long targetCommentId,
                                boolean contentTruncated) {
    private static final Pattern MARKER=Pattern.compile("^\\[(COMMENT|SKIP|REPLY(?::\\s*(\\d+))?)\\]\\s*(.*)$",Pattern.DOTALL|Pattern.CASE_INSENSITIVE);
    public static CommunityDecision parse(String text,ObjectMapper mapper,Set<Long> allowedTargets) {
        return parse(text,mapper,allowedTargets,null);
    }
    public static CommunityDecision parse(String text, ObjectMapper mapper, Set<Long> allowedTargets, Long triggerCommentId) {
        String content=text==null?"":text.strip();
        if(content.startsWith("```") && content.endsWith("```")) {
            int line=content.indexOf('\n');
            if(line>=0) content=content.substring(line+1,content.length()-3).strip();
            else content=content.substring(3,content.length()-3).strip().replaceFirst("(?i)^(json|text|plaintext)\\s+","");
        }
        String reason="参与公开讨论";
        String action=null;
        Long target=null;
        if("[[SKIP]]".equals(content)) return skip("没有可补充的内容");
        var marker=MARKER.matcher(content);
        if(marker.matches()) {
            action=marker.group(1).toUpperCase(java.util.Locale.ROOT);
            content=marker.group(3).strip();
            if("SKIP".equals(action)) return skip(content.isBlank()?"没有可补充的内容":shortText(content,300));
            if(action.startsWith("REPLY")) {
                action="REPLY";
                if(marker.group(2)==null) throw invalidTarget();
                try { target=Long.valueOf(marker.group(2)); } catch(NumberFormatException error) { throw invalidTarget(); }
            }
        }
        // 兼容旧 JSON 和代码围栏；字段可选、允许额外字段，目标仍须校验。
        if(content.startsWith("{") || content.startsWith("\"")) {
            try {
                JsonNode node=mapper.reader().with(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(content);
                if(node.isTextual()) content=node.asText();
                else if(node.isObject()) {
                    if(node.path("reason").isTextual() && !node.path("reason").asText().isBlank()) reason=node.path("reason").asText();
                    if("SKIP".equalsIgnoreCase(node.path("decision").asText())) return skip(shortText(reason,300));
                    if(node.path("decision").isTextual()) action=node.path("decision").asText().toUpperCase(java.util.Locale.ROOT);
                    JsonNode targetNode=node.path("targetCommentId");
                    if(!targetNode.isMissingNode() && !targetNode.isNull()) {
                        if(targetNode.isIntegralNumber()) target=targetNode.asLong();
                        else if(targetNode.isTextual() && targetNode.asText().matches("\\d+")) target=Long.valueOf(targetNode.asText());
                        else throw invalidTarget();
                    }
                    content=node.path("content").isTextual()?node.path("content").asText()
                            :node.path("text").isTextual()?node.path("text").asText():"";
                } else throw invalid();
            } catch(AIServiceException error) { throw error; }
            catch(Exception error) { throw invalid(); }
        }
        content=content.strip();
        if(content.isBlank()) throw invalid();
        if(action==null) {
            if(target!=null) action="REPLY";
            else if(triggerCommentId!=null && allowedTargets.contains(triggerCommentId)) { action="REPLY";target=triggerCommentId; }
            else action="COMMENT";
        }
        if(!"COMMENT".equals(action) && !"REPLY".equals(action)) throw invalid();
        if("REPLY".equals(action) && (target==null || !allowedTargets.contains(target))) throw invalidTarget();
        if("COMMENT".equals(action)) target=null;
        boolean truncated=content.length()>1000;
        return new CommunityDecision(action,shortText(reason,300),shortText(content,1000),target,truncated);
    }
    private static CommunityDecision skip(String reason) { return new CommunityDecision("SKIP",reason,null,null,false); }
    private static String shortText(String text,int maxChars) {
        if(text.length()<=maxChars) return text;
        int end=Character.isHighSurrogate(text.charAt(maxChars-1))?maxChars-1:maxChars;
        return text.substring(0,end);
    }
    private static AIServiceException.ModelException invalid() {
        return new AIServiceException.ModelException("角色未返回可发布的完整评论内容，本轮未发布");
    }
    private static AIServiceException.ModelException invalidTarget() {
        return new AIServiceException.ModelException("角色选择的回复评论不在本轮可读候选中，本轮未发布");
    }
}
