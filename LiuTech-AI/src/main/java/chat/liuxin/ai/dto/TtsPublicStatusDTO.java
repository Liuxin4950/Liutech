package chat.liuxin.ai.dto;

import chat.liuxin.ai.dto.tts.TtsStatusDTO;
import lombok.Data;

@Data
public class TtsPublicStatusDTO {
    private boolean enabled;
    private boolean online;
    private String provider;
    private long checkedAt;
    private String message;

    public static TtsPublicStatusDTO from(TtsStatusDTO status) {
        TtsPublicStatusDTO dto = new TtsPublicStatusDTO();
        if (status == null) {
            dto.setEnabled(false);
            dto.setOnline(false);
            dto.setCheckedAt(System.currentTimeMillis());
            dto.setMessage("状态不可用");
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
