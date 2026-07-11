package chat.liuxin.ai.service;

import chat.liuxin.ai.dto.FieldUpdatePayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * field-update 标记解析器。
 *
 * 流式解析 AI 输出中的 {@code ---field-update--- JSON ---end---} 标记：
 * - 标记外的文本作为正常 data 输出（透传给前端）
 * - 标记内的 JSON 解析为 {@link FieldUpdatePayload}，用于发 field-update SSE 事件
 *
 * 处理跨 chunk 的标记（开始/结束标记可能被 chunk 边界分割），通过缓冲区累积
 * 并保留末尾可能与 START_MARKER 前缀匹配的部分。
 * 线程不安全，每个写作流应使用独立实例。
 *
 * @author 刘鑫
 */
@Slf4j
public class FieldUpdateParser {

    private static final String START_MARKER = "---field-update---";
    private static final String END_MARKER = "---end---";

    private final StringBuilder buffer = new StringBuilder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 喂入一个 chunk，返回解析结果。
     *
     * @return dataTexts 为标记外文本片段（应通过 data 事件透传）；
     *         fieldUpdates 为本 chunk 中完整解析出的 payload（应通过 field-update 事件发送）
     */
    public ParseResult feed(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return new ParseResult(List.of(), List.of());
        }
        buffer.append(chunk);

        List<String> dataTexts = new ArrayList<>();
        List<FieldUpdatePayload> fieldUpdates = new ArrayList<>();

        while (true) {
            int startIdx = buffer.indexOf(START_MARKER);
            if (startIdx == -1) {
                // 无开始标记：输出 buffer 中不可能是 START 前缀的部分，
                // 保留末尾可能与 START_MARKER 前缀匹配的部分防跨 chunk 截断
                int k = startMarkerSuffixLength(buffer);
                int safeEnd = buffer.length() - k;
                if (safeEnd > 0) {
                    dataTexts.add(buffer.substring(0, safeEnd));
                    buffer.delete(0, safeEnd);
                }
                break;
            }
            // 输出开始标记前的文本
            if (startIdx > 0) {
                dataTexts.add(buffer.substring(0, startIdx));
                buffer.delete(0, startIdx);
            }
            // 找结束标记（从开始标记之后找）
            int endIdx = buffer.indexOf(END_MARKER, START_MARKER.length());
            if (endIdx == -1) {
                // 结束标记未到，等更多 chunk
                break;
            }
            // 提取并解析 JSON
            String json = buffer.substring(START_MARKER.length(), endIdx).trim();
            FieldUpdatePayload payload = parseJson(json);
            if (payload != null) {
                fieldUpdates.add(payload);
            }
            buffer.delete(0, endIdx + END_MARKER.length());
        }

        return new ParseResult(dataTexts, fieldUpdates);
    }

    /**
     * 流结束时调用，返回缓冲区中剩余的文本。
     * 未闭合的 field-update 标记部分会被丢弃（AI 输出不完整时）。
     */
    public String flush() {
        String rest = buffer.toString();
        buffer.setLength(0);
        int startIdx = rest.indexOf(START_MARKER);
        if (startIdx != -1) {
            return rest.substring(0, startIdx);
        }
        return rest;
    }

    /** 返回 buffer 末尾与 START_MARKER 前缀匹配的最大长度（用于跨 chunk 标记保留）。 */
    private int startMarkerSuffixLength(StringBuilder sb) {
        int len = sb.length();
        int maxK = Math.min(len, START_MARKER.length() - 1);
        for (int k = maxK; k >= 1; k--) {
            boolean match = true;
            for (int i = 0; i < k; i++) {
                if (sb.charAt(len - k + i) != START_MARKER.charAt(i)) {
                    match = false;
                    break;
                }
            }
            if (match) return k;
        }
        return 0;
    }

    private FieldUpdatePayload parseJson(String json) {
        if (json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, FieldUpdatePayload.class);
        } catch (Exception e) {
            log.warn("解析 field-update JSON 失败: {}, json={}", e.getMessage(), json);
            return null;
        }
    }

    /** 解析结果。 */
    public record ParseResult(List<String> dataTexts, List<FieldUpdatePayload> fieldUpdates) {}
}
