package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;


/** 持久化用户清理任务调用的 AI 数据清理客户端。 */
@Slf4j
@Component
public class AiUserDataClient {

    private final RestTemplate restTemplate;

    @Value("${ai.user-data.url:${AI_USER_DATA_URL:http://ai:8081}}")
    private String aiServiceUrl;

    @Value("${liutech.internal-token:}")
    private String internalToken;

    public AiUserDataClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(8000);
        this.restTemplate = new RestTemplate(factory);
    }

    public void purgeUser(Long userId) {
        HttpHeaders headers = internalHeaders();
        try {
            var response = restTemplate.exchange(
                    normalizeBaseUrl() + "/ai/internal/users/" + userId + "/permanent-data",
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    PurgeConfirmation.class);
            var body = response.getBody();
            if (body == null || !userId.equals(body.userId()) || !body.permanentlyPurged()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 未确认永久清理");
            }
            log.info("AI 用户数据清理确认: userId={}", userId);
        } catch (Exception e) {
            log.error("AI 用户数据清理调用失败: userId={}", userId, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "AI 服务用户数据清理失败，任务将重试", e);
        }
    }

    public record PurgeConfirmation(Long userId, int conversationsDeleted, int messagesDeleted,
                                    boolean permanentlyPurged) {}

    private HttpHeaders internalHeaders() {
        if (internalToken == null || internalToken.isBlank()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "内部服务令牌未配置，无法彻底删除用户");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-LiuTech-Internal-Token", internalToken);
        return headers;
    }

    private String normalizeBaseUrl() {
        String value = aiServiceUrl == null ? "" : aiServiceUrl.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
