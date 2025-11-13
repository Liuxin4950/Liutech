package chat.liuxin.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SiliconFlowChatClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${siliconflow.base-url:https://api.siliconflow.cn}")
    private String baseUrl;

    // @Value("${siliconflow.api-key}")
    private String apiKey = "sk-mgeqjwqoregdyyngyospzepxvidnkapkxupvatavqzhtcujo";

    @Value("${siliconflow.chat.model:THUDM/glm-4-9b-chat}")
    private String model;

    @Value("${siliconflow.chat.temperature:0.2}")
    private double temperature;

    public String chat(List<Message> messages) {
        String url = baseUrl + "/v1/chat/completions";
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置硅基流动 API Key (siliconflow.api-key)");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", toOpenAiMessages(messages));
        body.put("temperature", temperature);

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        String resp = restTemplate.postForObject(url, req, String.class);
        try {
            JsonNode root = objectMapper.readTree(resp);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode msg = choices.get(0).get("message");
                if (msg != null && msg.get("content") != null) {
                    return msg.get("content").asText();
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<Map<String, String>> toOpenAiMessages(List<Message> messages) {
        List<Map<String, String>> list = new ArrayList<>();
        for (Message m : messages) {
            Map<String, String> item = new HashMap<>();
            if (m instanceof SystemMessage) item.put("role", "system");
            else if (m instanceof UserMessage) item.put("role", "user");
            else if (m instanceof AssistantMessage) item.put("role", "assistant");
            else item.put("role", "user");
            item.put("content", m.getContent());
            list.add(item);
        }
        return list;
    }
}
