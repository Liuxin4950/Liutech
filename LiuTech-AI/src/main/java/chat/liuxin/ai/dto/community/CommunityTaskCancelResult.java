package chat.liuxin.ai.dto.community;

/** cancelled 仅在主后端取消栅栏已提交后为 true。 */
public record CommunityTaskCancelResult(boolean cancelled, String reason, Long publishedCommentId) {}
