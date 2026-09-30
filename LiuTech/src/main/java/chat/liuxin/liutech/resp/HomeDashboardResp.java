package chat.liuxin.liutech.resp;

import chat.liuxin.liutech.model.Comments;
import java.util.List;

public record HomeDashboardResp(List<PostListResp> latestPosts, List<PostListResp> hotPosts,
                                List<CategoryResp> categories, List<TagResp> hotTags,
                                List<Comments> latestComments) {}
