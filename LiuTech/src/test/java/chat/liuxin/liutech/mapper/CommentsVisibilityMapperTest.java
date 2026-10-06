package chat.liuxin.liutech.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** 校验 MyBatis 展开后的公共读取条件，后台审核查询保持独立。 */
class CommentsVisibilityMapperTest {
    private MybatisConfiguration configuration;

    @BeforeEach
    void setUp() throws Exception {
        configuration = new MybatisConfiguration();
        String resource = "mapper/CommentsMapper.xml";
        try (var input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @ParameterizedTest
    @MethodSource("publicQueries")
    void publicQueriesRequireVisibleCommentAndPublishedUndeletedPost(Query query) {
        String sql = sql(query.name(), query.parameters());
        assertTrue(sql.contains("INNER JOIN posts p ON p.id = " + query.commentAlias() + ".post_id"), sql);
        assertTrue(sql.contains("p.deleted_at IS NULL AND p.status = 'published'"), sql);
        assertTrue(sql.contains("c.deleted_at IS NULL"), sql);
    }

    static Stream<Query> publicQueries() {
        return Stream.of(
                new Query("selectRecentForCommunity", Map.of("postId",1L), "c"),
                new Query("selectPublicCommentById", Map.of("id", 1L), "c"),
                new Query("selectPublicCommentsByIds", Map.of("ids", List.of(1L,2L)), "c"),
                new Query("selectCommentsByPostId", Map.of("postId", 1L), "c"),
                new Query("selectTopLevelCommentsByPostId", Map.of("postId", 1L), "c"),
                new Query("selectChildCommentsByParentId", Map.of("parentId", 1L), "c"),
                new Query("countCommentsByPostId", Map.of("postId", 1L), "c"),
                new Query("selectLatestComments", Map.of("limit", 5), "c"),
                new Query("selectAllDescendantsByRootIds", Map.of("rootIds", List.of(1L, 2L)), "d"));
    }

    @Test
    void publicQueriesShareAncestorVisibilityAndCommunityDoesNotBuildAnotherPolicy() {
        for (String name:List.of("selectPublicCommentById","selectPublicCommentsByIds","selectCommentsByPostId","selectLatestComments","countCommentsByPostId","selectRecentForCommunity")) {
            String sql=sql(name, Map.of("id",1L,"postId",1L,"limit",5,"ids",List.of(1L,2L)));
            assertTrue(sql.contains("WITH RECURSIVE visible_comments AS"),sql);
            assertTrue(sql.contains("c.parent_id IS NULL AND c.deleted_at IS NULL"),sql);
            assertTrue(sql.contains("INNER JOIN visible_comments ancestor ON c.parent_id=ancestor.id WHERE c.deleted_at IS NULL"),sql);
            assertTrue(sql.contains("INNER JOIN visible_comments visible ON visible.id=c.id"),sql);
            assertTrue(sql.contains("LEFT JOIN community_bots b ON c.bot_id = b.id") || name.equals("countCommentsByPostId"),sql);
        }
        String achievement=sql("countVisibleCommentsByUserId",Map.of("userId",1L));
        assertTrue(achievement.contains("JOIN visible_comments visible ON visible.id=c.id"),achievement);
        assertTrue(achievement.contains("c.user_id=?"),achievement);
        String admin=sql("selectCommentsForAdminById",Map.of("id",1L));
        assertFalse(admin.contains("visible_comments"),admin);
    }

    @Test
    void visibilityConditionsAreAppliedBeforeLatestLimitAndPagination() {
        String latest = sql("selectLatestComments", Map.of("limit", 5));
        assertTrue(latest.indexOf("p.status = 'published'") < latest.indexOf("ORDER BY"));
        assertTrue(latest.indexOf("ORDER BY") < latest.indexOf("LIMIT ?"));
        String paged = sql("selectCommentsByPostId", Map.of("postId", 42L));
        assertTrue(paged.contains("AND c.post_id = ?"));
        assertTrue(paged.indexOf("p.status = 'published'") < paged.indexOf("ORDER BY"));
    }

    @Test
    void metadataPostLabelsUseBoundedIdsAndOnlyPublicUndeletedArticles() {
        configuration.addMapper(CommunityMapper.class);
        var bound=configuration.getMappedStatement(CommunityMapper.class.getName()+".publicPostMetadata")
            .getBoundSql(Map.of("ids",List.of(1L,2L)));
        String sql=bound.getSql().replaceAll("\\s+"," ");
        assertTrue(sql.contains("status='published' AND deleted_at IS NULL"),sql);
        assertTrue(sql.contains("id IN"),sql);
        assertEquals(2,bound.getParameterMappings().size());
    }

    @Test
    void adminQueriesCanStillReadCommentsOnHiddenPostsAndDeletedComments() {
        String detail = sql("selectCommentsForAdminById", Map.of("id", 1L));
        String list = sql("selectCommentsForAdmin", Map.of("includeDeleted", true, "offset", 0, "limit", 20));
        String count = sql("countCommentsForAdmin", Map.of("includeDeleted", true));
        String descendants = sql("selectAllDescendantIds", Map.of("ids", List.of(1L)));
        for (String sql : List.of(detail, list, count, descendants)) {
            assertFalse(sql.contains("p.status = 'published'"), sql);
            assertFalse(sql.contains("p.deleted_at IS NULL"), sql);
            assertFalse(sql.contains("c.deleted_at IS NULL"), sql);
        }
    }

    @Test
    void roleAuditRetainsHiddenAndDeletedFactsAndLimitsLatestThreadReplies() {
        String list=sql("selectCommentsByBotForAdmin",Map.of("botId",1L,"offset",0L,"limit",20));
        String root=sql("selectRootCommentIdForAdmin",Map.of("commentId",3L,"postId",2L));
        String count=sql("countThreadCommentsForAdmin",Map.of("rootCommentId",1L,"postId",2L));
        String thread=sql("selectThreadCommentsForAdmin",Map.of("rootCommentId",1L,"postId",2L,"limit",200));
        for (String audit:List.of(list,root,count,thread)) {
            assertFalse(audit.contains("deleted_at IS NULL"),audit);
            assertFalse(audit.contains("status = 'published'"),audit);
        }
        assertTrue(list.contains("WHERE c.bot_id=?"),list);
        assertTrue(thread.contains("ORDER BY c.created_at DESC,c.id DESC LIMIT ?"),thread);
        assertTrue(thread.endsWith("ORDER BY c.created_at,c.id"),thread);
        assertTrue(thread.contains("WHERE c.post_id=?"),thread);
        assertEquals("AdminCommentMap",configuration.getMappedStatement(CommentsMapper.class.getName()+".selectThreadCommentsForAdmin")
            .getResultMaps().getFirst().getId().substring(CommentsMapper.class.getName().length()+1));
    }

    @Test
    void permanentDeleteUsesPhysicalSqlWithoutVisibilityOrLogicalDeleteConditions() {
        String delete = sql("permanentDeleteByIds", Map.of("ids", List.of(1L, 2L)));
        assertEquals("DELETE FROM comments WHERE id IN ( ? , ? )", delete);
        assertFalse(delete.contains("deleted_at"));
        assertFalse(delete.contains("posts"));
    }

    @Test
    void deleteDescendantQueryDeduplicatesAndOrdersDeepestNodesFirst() {
        String sql = sql("selectAllDescendantIds", Map.of("ids", List.of(1L, 2L)));
        assertTrue(sql.contains("SELECT id, 1 AS depth FROM comments"), sql);
        assertTrue(sql.contains("SELECT c.id, d.depth + 1 AS depth"), sql);
        assertTrue(sql.endsWith("GROUP BY id ORDER BY MAX(depth) DESC, id DESC"), sql);
    }

    private String sql(String name, Map<String, ?> parameters) {
        return configuration.getMappedStatement(CommentsMapper.class.getName() + "." + name)
                .getBoundSql(parameters).getSql().replaceAll("\\s+", " ").trim();
    }

    record Query(String name, Map<String, ?> parameters, String commentAlias) {}
}
