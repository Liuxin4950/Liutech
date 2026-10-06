package chat.liuxin.ai.dto.community;

public record CommunityTaskRetryResult(boolean queued,String reason,String retryKind) {
    public CommunityTaskRetryResult(boolean queued,String reason) { this(queued,reason,null); }
}
