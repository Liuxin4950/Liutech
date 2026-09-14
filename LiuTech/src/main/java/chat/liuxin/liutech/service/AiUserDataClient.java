package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/** 主服务彻底删除用户前调用的 AI 数据清理客户端。 */
@Slf4j
@Component
public class AiUserDataClient {

    private static final int MAX_BATCH_SIZE = 100;
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
            restTemplate.exchange(
                    normalizeBaseUrl() + "/ai/internal/users/" + userId + "/data",
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    String.class);
            log.info("AI 用户数据清理确认: userId={}", userId);
        } catch (Exception e) {
            log.error("AI 用户数据清理失败，已中止彻底删除: userId={}", userId, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "AI 服务用户数据清理失败，已中止彻底删除，请稍后重试", e);
        }
    }

    public void purgeUsers(List<Long> userIds) {
        for (int start = 0; start < userIds.size(); start += MAX_BATCH_SIZE) {
            List<Long> batch = userIds.subList(start, Math.min(start + MAX_BATCH_SIZE, userIds.size()));
            purgeBatch(batch);
        }
    }

    private void purgeBatch(List<Long> userIds) {
        HttpHeaders headers = internalHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            restTemplate.exchange(
                    normalizeBaseUrl() + "/ai/internal/users/purge",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("userIds", userIds), headers),
                    String.class);
            log.info("AI 用户数据批量清理确认: users={}", userIds.size());
        } catch (Exception e) {
            log.error("AI 用户数据批量清理失败，已中止彻底删除: users={}", userIds, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "AI 服务用户数据清理失败，已中止批量彻底删除，请稍后重试", e);
        }
    }

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
