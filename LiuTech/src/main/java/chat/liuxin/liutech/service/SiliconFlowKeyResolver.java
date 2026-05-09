package chat.liuxin.liutech.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析 SiliconFlow Key。
 *
 * 本地 Spring Boot 直跑时不会自动加载项目根目录 .env，所以这里做一层轻量兜底。
 * 优先级：TTS 专用 key > 通用 SiliconFlow key > AI 模型 key。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiliconFlowKeyResolver {

    private static final String SOURCE_TTS = "SILICONFLOW_TTS_API_KEY";
    private static final String SOURCE_COMPAT = "SILICONFLOW_API_KEY";
    private static final String SOURCE_AI = "SPRING_AI_OPENAI_API_KEY";

    private final Environment environment;

    private volatile Map<String, String> dotenvCache;

    public String resolveTtsApiKey() {
        return firstNonBlank(
                property("siliconflow.tts-api-key"),
                property("SILICONFLOW_TTS_API_KEY"),
                System.getenv(SOURCE_TTS),
                dotenv(SOURCE_TTS),
                property("siliconflow.api-key"),
                property("SILICONFLOW_API_KEY"),
                System.getenv(SOURCE_COMPAT),
                dotenv(SOURCE_COMPAT),
                property("spring.ai.openai.api-key"),
                property("SPRING_AI_OPENAI_API_KEY"),
                System.getenv(SOURCE_AI),
                dotenv(SOURCE_AI)
        );
    }

    public boolean hasTtsApiKey() {
        return resolveTtsApiKey() != null;
    }

    public String resolveTtsApiKeySource() {
        if (hasAny("siliconflow.tts-api-key", "SILICONFLOW_TTS_API_KEY", SOURCE_TTS)) {
            return SOURCE_TTS;
        }
        if (hasAny("siliconflow.api-key", "SILICONFLOW_API_KEY", SOURCE_COMPAT)) {
            return SOURCE_COMPAT;
        }
        if (hasAny("spring.ai.openai.api-key", "SPRING_AI_OPENAI_API_KEY", SOURCE_AI)) {
            return SOURCE_AI + " fallback";
        }
        return null;
    }

    private boolean hasAny(String propertyName, String envPropertyName, String dotenvName) {
        return firstNonBlank(
                property(propertyName),
                property(envPropertyName),
                System.getenv(dotenvName),
                dotenv(dotenvName)
        ) != null;
    }

    private String property(String name) {
        return normalize(environment.getProperty(name));
    }

    private String dotenv(String name) {
        return dotenv().get(name);
    }

    private Map<String, String> dotenv() {
        Map<String, String> hit = dotenvCache;
        if (hit != null) {
            return hit;
        }
        Map<String, String> loaded = new HashMap<>();
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 6 && current != null; i++) {
            Path envFile = current.resolve(".env");
            if (Files.isRegularFile(envFile)) {
                loaded.putAll(readDotenv(envFile));
                break;
            }
            current = current.getParent();
        }
        dotenvCache = loaded;
        return loaded;
    }

    private Map<String, String> readDotenv(Path envFile) {
        Map<String, String> values = new HashMap<>();
        try {
            List<String> lines = Files.readAllLines(envFile);
            for (String line : lines) {
                String trimmed = line == null ? "" : line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.startsWith("export ")) {
                    trimmed = trimmed.substring("export ".length()).trim();
                }
                int idx = trimmed.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, idx).trim();
                String normalized = normalize(unquote(trimmed.substring(idx + 1).trim()));
                if (normalized != null) {
                    values.put(key, normalized);
                }
            }
        } catch (IOException e) {
            log.debug("Failed to read .env file", e);
        }
        return values;
    }

    private String unquote(String value) {
        if (value.length() >= 2) {
            boolean doubleQuoted = value.startsWith("\"") && value.endsWith("\"");
            boolean singleQuoted = value.startsWith("'") && value.endsWith("'");
            if (doubleQuoted || singleQuoted) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
