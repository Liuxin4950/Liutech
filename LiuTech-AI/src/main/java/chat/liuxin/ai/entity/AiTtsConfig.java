package chat.liuxin.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** AI 服务拥有的单行 TTS 配置。 */
@Data
@TableName("ai_tts_config")
public class AiTtsConfig {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Boolean enabled;
    private String provider;

    @TableField("base_url")
    private String baseUrl;

    @TableField("voice_model")
    private String voiceModel;

    @TableField("siliconflow_model")
    private String siliconFlowModel;

    @TableField("siliconflow_voice_uri")
    private String siliconFlowVoiceUri;

    @TableField("response_format")
    private String responseFormat;

    @TableField("sample_rate")
    private Integer sampleRate;

    private BigDecimal speed;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
