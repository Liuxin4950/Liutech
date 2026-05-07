package chat.liuxin.liutech.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 统一 TTS 推理请求。
 */
@Data
public class TtsSpeechRequestDTO {

    @NotBlank(message = "语音文本不能为空")
    @Size(max = 1200, message = "单次语音文本不能超过1200个字符")
    private String text;
}
