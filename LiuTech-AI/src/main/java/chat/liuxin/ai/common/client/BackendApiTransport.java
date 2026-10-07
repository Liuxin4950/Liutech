package chat.liuxin.ai.common.client;

import chat.liuxin.ai.infra.exception.AIServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** AI 服务访问主服务的唯一 HTTP 传输层。 */
@Slf4j
@Component
public class BackendApiTransport {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${blog.api.url:http://backend:8080}")
    private String backendApiUrl;

    @Value("${liutech.internal-token:}")
    private String internalToken;

    public BackendApiTransport(
            ObjectMapper objectMapper,
            @Value("${spring.ai.agent.blog-connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${spring.ai.agent.blog-read-timeout-ms:8000}") long readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connectTimeoutMs);
        factory.setReadTimeout((int) readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = objectMapper;
    }

    public JsonNode getJson(String path) {
        String response = restTemplate.getForObject(resolveUrl(path), String.class);
        return readTree(response);
    }

    /** 所有内部业务请求沿用唯一传输层和内部令牌，绝不使用管理员 JWT。 */
    public JsonNode internalGet(String path) { return internalExchange(path, HttpMethod.GET, null); }
    public JsonNode internalPost(String path, Object body) { return internalExchange(path, HttpMethod.POST, body); }

    private JsonNode internalExchange(String path, HttpMethod method, Object body) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new AIServiceException.ConnectionException("内部服务令牌尚未配置");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(InternalHeaders.INTERNAL_TOKEN, internalToken);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        try {
            JsonNode response = readTree(restTemplate.exchange(resolveUrl(path), method,
                    new HttpEntity<>(body, headers), String.class).getBody());
            if (response == null || response.path("code").asInt() != 200) {
                throw new InternalBusinessException(response == null ? 500 : response.path("code").asInt(),
                        response == null ? "内部服务返回异常" : response.path("message").asText("内部服务拒绝操作"));
            }
            return response.path("data");
        } catch (HttpStatusCodeException error) {
            JsonNode response = readTree(error.getResponseBodyAsString());
            throw new InternalBusinessException(response.path("code").asInt(error.getStatusCode().value()),
                    response.path("message").asText("内部服务请求失败"));
        } catch (ResourceAccessException error) {
            throw new AIServiceException.ConnectionException("主服务暂时无法连接，请稍后重试");
        }
    }

    public static class InternalBusinessException extends AIServiceException.RequestException {
        private final int businessCode;
        public InternalBusinessException(int businessCode, String message) { super(message); this.businessCode = businessCode; }
        public int businessCode() { return businessCode; }
    }

    public JsonNode introspect(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        headers.set(InternalHeaders.INTERNAL_TOKEN, internalToken);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    resolveUrl("/internal/auth/introspect"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);
            return readTree(response.getBody());
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 401) {
                throw new InvalidUserTokenException("登录状态无效或已过期");
            }
            if (e.getStatusCode().value() == 403) {
                throw new AuthenticationAuthorityUnavailableException("内部身份服务拒绝访问", e);
            }
            throw new AuthenticationAuthorityUnavailableException("身份服务暂时不可用", e);
        } catch (ResourceAccessException e) {
            throw new AuthenticationAuthorityUnavailableException("身份服务暂时不可用", e);
        } catch (AIServiceException e) {
            throw new AuthenticationAuthorityUnavailableException("身份服务返回异常", e);
        }
    }

    public JsonNode extractData(JsonNode root) {
        if (root != null && root.has("code") && root.get("code").asInt() == 200
                && root.has("data") && !root.get("data").isNull()) {
            return root.get("data");
        }
        return null;
    }

    public String resolveUrl(String path) {
        String base = backendApiUrl == null ? "" : backendApiUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String normalizedPath = path == null ? "" : path.trim();
        if (!normalizedPath.startsWith("/")) normalizedPath = "/" + normalizedPath;
        return base + normalizedPath;
    }

    private JsonNode readTree(String response) {
        try {
            return objectMapper.readTree(response);
        } catch (Exception e) {
            throw new AIServiceException.ConnectionException("主服务返回了无法解析的响应");
        }
    }

    public static final class InternalHeaders {
        public static final String INTERNAL_TOKEN = "X-LiuTech-Internal-Token";
        private InternalHeaders() {}
    }

    public static class InvalidUserTokenException extends RuntimeException {
        public InvalidUserTokenException(String message) { super(message); }
    }

    public static class AuthenticationAuthorityUnavailableException extends RuntimeException {
        public AuthenticationAuthorityUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
