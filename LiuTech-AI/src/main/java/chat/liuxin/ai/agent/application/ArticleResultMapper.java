package chat.liuxin.ai.agent.application;

import chat.liuxin.ai.agent.response.ArticleResultItem;
import chat.liuxin.ai.dto.PostSummaryDTO;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ArticleResultMapper {

    public ArticleResultItem fromSummary(PostSummaryDTO post, String source, String reason) {
        if (post == null) {
            return null;
        }
        Long id = post.getId();
        return ArticleResultItem.builder()
                .id(id)
                .title(post.getTitle())
                .summary(post.getSummary())
                .status("published")
                .categoryName(post.getCategoryName())
                .tagNames(post.getTags() == null ? Collections.emptyList() : post.getTags())
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount())
                .createdAt(post.getCreatedAt())
                .url(id == null ? null : "/post/" + id)
                .adminUrl(id == null ? null : "/admin/posts?postId=" + id)
                .reason(reason)
                .source(source)
                .build();
    }

    public List<ArticleResultItem> fromSummaries(List<PostSummaryDTO> posts, String source, String reason) {
        if (posts == null || posts.isEmpty()) {
            return Collections.emptyList();
        }
        return posts.stream()
                .map(post -> fromSummary(post, source, reason))
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
