package chat.liuxin.ai.security;

import org.springframework.stereotype.Component;

@Component
public class SensitiveLogSanitizer {

    private static final int DEFAULT_MAX_LENGTH = 500;

    public String sanitize(String value) {
        return sanitize(value, DEFAULT_MAX_LENGTH);
    }

    public String sanitize(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String sanitized = value
                .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._\\-]+", "Bearer ***")
                .replaceAll("sk-[A-Za-z0-9_\\-]{12,}", "sk-***")
                .replaceAll("(?i)(api[_-]?key|authorization|jwt|token)\\s*[:=]\\s*[^\\s,;]+", "$1=***");
        int limit = Math.max(80, maxLength);
        if (sanitized.length() > limit) {
            return sanitized.substring(0, limit) + "...[truncated]";
        }
        return sanitized;
    }
}
