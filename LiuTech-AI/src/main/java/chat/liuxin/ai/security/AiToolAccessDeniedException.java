package chat.liuxin.ai.security;

public class AiToolAccessDeniedException extends RuntimeException {
    public AiToolAccessDeniedException(String message) {
        super(message);
    }
}
