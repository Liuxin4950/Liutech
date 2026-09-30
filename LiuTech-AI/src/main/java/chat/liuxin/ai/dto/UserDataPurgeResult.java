package chat.liuxin.ai.dto;

public record UserDataPurgeResult(Long userId, int conversationsDeleted, int messagesDeleted, boolean permanentlyPurged) {
    public UserDataPurgeResult(Long userId, int conversationsDeleted, int messagesDeleted) {
        this(userId, conversationsDeleted, messagesDeleted, false);
    }
}
