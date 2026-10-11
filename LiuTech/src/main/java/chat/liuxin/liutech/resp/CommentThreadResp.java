package chat.liuxin.liutech.resp;

import chat.liuxin.liutech.model.Comments;

/** 管理端同文章关系快照；平铺分页避免深层 JSON 递归与静默截断。 */
public record CommentThreadResp(Long commentId, Long postId, String postTitle, Long snapshotMaxId,
                                PageResp<Comments> comments) {}
