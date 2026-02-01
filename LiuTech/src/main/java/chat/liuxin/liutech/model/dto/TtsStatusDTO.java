package chat.liuxin.liutech.model.dto;

import lombok.Data;

/**
 * TTS（语音推理）在线状态 DTO
 */
@Data
public class TtsStatusDTO {

    /**
     * 管理端全局开关
     */
    private boolean enabled;

    /**
     * 当前服务是否可用（可连通）
     */
    private boolean online;

    /**
     * 当前生效的 TTS 基础地址（用于前端调试展示）
     */
    private String baseUrl;

    /**
     * 最近一次探测时间（毫秒时间戳）
     */
    private long checkedAt;

    /**
     * 额外提示信息（例如：未配置地址/探测异常原因）
     */
    private String message;
}

