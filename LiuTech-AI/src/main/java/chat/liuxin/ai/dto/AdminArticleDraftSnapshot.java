package chat.liuxin.ai.dto;

import lombok.Data;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 管理员文章草稿快照。
 *
 * 编辑文章时由前端 AdminAgentSidebar 随写作请求发送，让 AI 能读取当前正在编辑的内容。
 * 作为不可信上下文注入到消息序列，仅供 AI 事实参考，不能作为系统指令。
 *
 * @author 刘鑫
 */
@Data
public class AdminArticleDraftSnapshot {

    /** 文章ID（新建时为空） */
    @Positive
    private Long postId;

    /** 标题 */
    @Size(max = 200)
    private String title;

    /** 正文（Markdown/HTML） */
    @Size(max = 200000, message = "草稿过长，请分篇编辑")
    private String content;

    /** 摘要 */
    @Size(max = 500)
    private String summary;

    /** 当前分类ID */
    private Long categoryId;

    /** 当前标签ID列表 */
    @Size(max = 20)
    private List<Long> tagIds;

    /** 文章状态 */
    @Size(max = 20)
    private String status;
}
