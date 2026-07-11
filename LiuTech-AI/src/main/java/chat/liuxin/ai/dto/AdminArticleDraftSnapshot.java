package chat.liuxin.ai.dto;

import lombok.Data;

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
    private Long postId;

    /** 标题 */
    private String title;

    /** 正文（Markdown/HTML） */
    private String content;

    /** 摘要 */
    private String summary;

    /** 当前分类ID */
    private Long categoryId;

    /** 当前标签ID列表 */
    private List<Long> tagIds;

    /** 文章状态 */
    private String status;
}
