package chat.liuxin.liutech.resp;

import java.util.Date;

/** 列表中的父评论预览，保留软删除节点和真实作者身份。 */
public record CommentParentResp(Long id, Long postId, String content, String authorName,
                                String authorType, Date deletedAt) {}
