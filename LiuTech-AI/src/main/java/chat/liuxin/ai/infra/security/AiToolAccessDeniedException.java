package chat.liuxin.ai.infra.security;

public class AiToolAccessDeniedException extends RuntimeException {
    public AiToolAccessDeniedException(String message) {
        super(message);
    }
}
