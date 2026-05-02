package chat.liuxin.ai.agent.response;

import lombok.Builder;
import lombok.Data;

/**
 * 自然语言文本事件负载。
 * 只承载模型输出的自然语言文本，不承载结构化数据。
 * JSON 结构：{ "content": "我找到了这些相关文章，可以直接点开阅读。" }
 *
 * @author liuxin
 * @see AgentSseEnvelope
 */
@Data
@Builder
public class DataPayload {

    /**
     * 自然语言文本内容。
     * 只承载模型输出的自然语言文本，不承载结构化数据。
     */
    private String content;

    /**
     * 创建文本事件负载。
     *
     * @param content 文本内容
     */
    public static DataPayload of(String content) {
        return DataPayload.builder()
                .content(content)
                .build();
    }
}
