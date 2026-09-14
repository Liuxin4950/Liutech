package chat.liuxin.ai.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UserDataPurgeRequest {
    @NotEmpty(message = "用户ID列表不能为空")
    @Size(max = 100, message = "单次最多清理100个用户")
    private List<Long> userIds;
}
