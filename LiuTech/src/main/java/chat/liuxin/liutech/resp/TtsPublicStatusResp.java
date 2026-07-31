package chat.liuxin.liutech.resp;

import lombok.Data;

/**
 * TTS 公共在线状态。
 *
 * 只暴露播放侧真正需要的信息，避免公开接口泄漏本地推理地址、
 * 模型名称、云端音色 URI 或 API Key 来源。
 */
@Data
public class TtsPublicStatusResp {

    private boolean enabled;
    private boolean online;
    private String provider;
    private long checkedAt;
    private String message;

    public static TtsPublicStatusResp from(TtsStatusResp status) {
        TtsPublicStatusResp dto = new TtsPublicStatusResp();
        if (status == null) {
            dto.setEnabled(false);
            dto.setOnline(false);
            dto.setMessage("状态不可用");
            dto.setCheckedAt(System.currentTimeMillis());
            return dto;
        }
        dto.setEnabled(status.isEnabled());
        dto.setOnline(status.isOnline());
        dto.setProvider(status.getProvider());
        dto.setCheckedAt(status.getCheckedAt());
        dto.setMessage(status.getMessage());
        return dto;
    }
}
