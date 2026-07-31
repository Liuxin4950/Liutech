package chat.liuxin.liutech.resp;

import lombok.Data;

/**
 * AI 运行时状态（前台/管理端响应）
 *
 * 用途：
 * - 给前台和管理端一个统一的“当前 AI/TTS 能力快照”
 * - 避免前端分别请求默认模型、TTS 状态，再自己拼装判断逻辑
 */
@Data
public class AiRuntimeResp {

    /**
     * AI 服务是否可用
     */
    private boolean aiOnline;

    /**
     * AI 服务状态说明
     */
    private String aiMessage;

    /**
     * 当前默认模型
     */
    private String defaultModel;

    /**
     * TTS 公共运行时状态
     */
    private TtsPublicStatusResp tts;
}
