package chat.liuxin.ai.dto;

public record UserDataPurgeResult(Long userId, int conversationsDeleted, int messagesDeleted) {
}
