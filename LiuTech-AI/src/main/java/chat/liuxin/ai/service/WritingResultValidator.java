package chat.liuxin.ai.service;

import chat.liuxin.ai.dto.WritingContentPatch;
import chat.liuxin.ai.infra.exception.AIServiceException;
import java.util.List;

/** 同步与流式共用最后一道写作语义校验；不负责 SSE 或文章保存。 */
public final class WritingResultValidator {
    private WritingResultValidator() {}
    public static chat.liuxin.ai.common.client.ModelExecutionPolicy executionPolicy(
            FieldUpdateCollector collector, WritingContentSession session, WritingToolEventSink sink) {
        return new chat.liuxin.ai.common.client.ModelExecutionPolicy(true, sink,
                () -> !collector.isEmpty() || session.wasReviewed());
    }
    public record Body(String html, WritingContentPatch patch) {}
    public static Body validate(String output, String original, String mode, List<?> fields,
                                FieldUpdateCollector collector, WritingContentSession session) {
        boolean requested = fields.contains("content") && !fields.contains("check");
        boolean allowed = fields.isEmpty() || requested;
        boolean reviewed = session.wasReviewed();
        if ((output == null || output.isBlank()) && collector.isEmpty() && !reviewed) {
            throw new AIServiceException.ModelException("AI 没有返回有效内容，请稍后重试");
        }
        boolean patchMode = "patch".equals(mode);
        if (patchMode && requested && !reviewed) {
            throw new AIServiceException.ModelException("AI 没有生成可定位的局部修改，正文未修改，请重试");
        }
        String html = !patchMode && allowed ? WritingHtmlValidator.validate(output, original) : null;
        if (!patchMode && requested && html == null) {
            throw new AIServiceException.ModelException("AI 没有返回完整有效的 HTML 正文，正文未修改，请重试");
        }
        WritingContentPatch patch = reviewed ? session.finish() : null;
        if (patch != null && (!patchMode || !allowed)) {
            throw new AIServiceException.ModelException("正文修改方式与本轮范围不一致，正文未修改");
        }
        return new Body(html, patch);
    }
}
