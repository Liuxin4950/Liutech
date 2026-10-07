package chat.liuxin.ai.dto;

import java.util.Map;

/** 写作流唯一事件信封；正文、活动与终态共享请求标识和严格递增序号。 */
public record WritingStreamEvent(int version, String requestId, long sequence, long timestamp,
                                 String type, Map<String, Object> data) {
    public static final String EVENT_NAME = "writing-event";

    public Map<String, Object> toPayload() {
        return Map.of("version", version, "requestId", requestId, "sequence", sequence,
                "timestamp", timestamp, "type", type, "data", data);
    }
}
