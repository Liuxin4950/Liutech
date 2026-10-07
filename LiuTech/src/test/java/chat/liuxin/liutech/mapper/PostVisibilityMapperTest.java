package chat.liuxin.liutech.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import chat.liuxin.liutech.model.Posts;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** 检验公开关联数据的实际 SQL，保留软删关联不能绕过文章可见性。 */
class PostVisibilityMapperTest {
    private MybatisConfiguration configuration;

    @BeforeEach
    void setUp() throws Exception {
        configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "visibility-test"), Posts.class);
        for (String name : new String[]{"TagsMapper", "PostFavoritesMapper", "CommentsMapper"}) {
            String path = "mapper/" + name + ".xml";
            try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(input);
                new XMLMapperBuilder(input, configuration, path, configuration.getSqlFragments()).parse();
            }
        }
    }

    @Test
    void publicPostTagsRequireVisiblePublishedArticle() {
        String sql = sql("TagsMapper.selectTagsByPostId", Map.of("postId", 1L));
        assertTrue(sql.contains("INNER JOIN posts p"), sql);
        assertVisibleArticle(sql);
    }

    @Test
    void allTagCountersUseSamePublishedArticleScope() {
        for (String method : new String[]{"selectTagsWithPostCount", "selectHotTags", "selectTagsByName"}) {
            assertVisibleArticle(sql("TagsMapper." + method, Map.of("limit", 5, "name", "tag")));
        }
    }

    @Test
    void favoriteCountExcludesHiddenArticlesLikeFavoriteList() {
        String sql = sql("PostFavoritesMapper.countFavoritesByUserId", Map.of("userId", 42L));
        assertTrue(sql.contains("INNER JOIN posts p"), sql);
        assertVisibleArticle(sql);
    }

    @Test
    void interactionLockUsesCurrentReadAndDisablesQueryCache() {
        configuration.addMapper(PostsMapper.class);
        var statement = configuration.getMappedStatement("chat.liuxin.liutech.mapper.PostsMapper.selectByIdForUpdate");
        String sql = statement.getBoundSql(Map.of("id", 1L)).getSql();
        assertTrue(sql.endsWith("FOR UPDATE"));
        assertTrue(sql.contains("deleted_at IS NULL"));
        assertFalse(statement.isUseCache());
        assertTrue(statement.isFlushCacheRequired());
    }


    @Test
    void permanentArticleDeletionFindsRootsIncludingSoftDeletedComments() {
        String sql = sql("CommentsMapper.selectRootCommentIdsByPostIds", Map.of("postIds", java.util.List.of(1L, 2L)));
        assertTrue(sql.contains("parent_id IS NULL"), sql);
        assertTrue(sql.contains("post_id IN"), sql);
        assertFalse(sql.contains("deleted_at"), sql);
        assertFalse(sql.contains("published"), sql);
    }


    @Test
    void adminTagCountersKeepDraftArticleRelationsVisible() {
        String sql = sql("TagsMapper.selectTagsForAdmin", Map.of("includeDeleted", true, "offset", 0, "limit", 10));
        assertTrue(sql.contains("p.deleted_at IS NULL"));
        assertFalse(sql.contains("p.status"), sql);
    }

    private String sql(String method, Map<String, Object> params) {
        return configuration.getMappedStatement("chat.liuxin.liutech.mapper." + method)
                .getBoundSql(params).getSql().replaceAll("\\s+", " ");
    }

    private void assertVisibleArticle(String sql) {
        assertTrue(sql.contains("p.deleted_at IS NULL"), sql);
        assertTrue(sql.contains("p.status = 'published'"), sql);
    }
}
