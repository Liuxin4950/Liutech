package chat.liuxin.ai.agent.application;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AdminArticleDraftRequest {
    private Long postId;

    @Size(max = 120, message = "标题长度不能超过120个字符")
    private String title;

    @Size(max = 50000, message = "正文长度不能超过50000个字符")
    private String content;

    @Size(max = 500, message = "摘要长度不能超过500个字符")
    private String summary;

    private Long categoryId;

    @Size(max = 20, message = "标签数量不能超过20个")
    private List<Long> tagIds;

    @Size(max = 32, message = "状态长度不能超过32个字符")
    private String status;

    @Size(max = 500, message = "封面地址长度不能超过500个字符")
    private String coverImage;

    @Size(max = 500, message = "缩略图地址长度不能超过500个字符")
    private String thumbnail;
}
