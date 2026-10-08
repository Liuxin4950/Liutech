package chat.liuxin.liutech.resp;

import chat.liuxin.liutech.model.*;
import java.util.Date;
import java.util.List;

/** 内部执行快照不包含 JWT、私聊、草稿和其他角色的资料。 */
public final class CommunityResp {
    private CommunityResp() {}
    public record BotInfo(Long id, String name, String avatarUrl) {}
    public record PostInfo(Long id, String title, String content, String summary, Date updatedAt) {}
    public record Context(CommunityBot bot, CommunitySettings settings, PostInfo post, List<Comments> comments,
        List<CommunityKnowledge> knowledge, String contextVersion, boolean postEnabled) {}
    public record Attempt(boolean allowed, String reason) {}
    public record Published(Long commentId, Date createdAt, boolean duplicate) {}
    public record Queued(int queued) {}
    public record Cancelled(boolean cancelled, String reason, Long publishedCommentId) {}
    /** queued 为新事件数，skipped 为已安排/已发布或文章停用的候选角色数，postCount 为本批检查的文章数。 */
    public record Backfill(int queued, int skipped, int postCount) {}
    public record PostMetadata(Long id, String title) {}
    public record CommentMetadata(Long id, String content, String authorName) {}
    public record Metadata(List<PostMetadata> posts, List<CommentMetadata> comments) {}
    public record Visibility(List<Long> visiblePostIds, List<Long> visibleCommentIds) {}
    public record Thread(Long postId, String postTitle, Long rootCommentId, long total,
        boolean truncated, List<Comments> comments) {}
}
