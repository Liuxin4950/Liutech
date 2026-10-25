package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.CommentsMapper;
import chat.liuxin.liutech.mapper.PostsMapper;
import chat.liuxin.liutech.mapper.UserViewHistoryMapper;
import chat.liuxin.liutech.resp.PostListResp;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 MySQL 核对公开文章数字与评论可见树在删除、恢复及分页后保持一致。 */
@EnabledIfEnvironmentVariable(named = "LIUTECH_TEST_MYSQL_URL", matches = ".+")
class PostCommentCountMysqlTest {
    private MysqlFixture db;
    private PostsMapper posts;
    private CommentsMapper comments;

    @BeforeEach void setup() throws Exception {
        db = new MysqlFixture("mapper/CommentsMapper.xml", "mapper/PostsMapper.xml", "mapper/UserViewHistoryMapper.xml");
        var pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        db.session.getConfiguration().addInterceptor(pagination);
        posts = db.session.getMapper(PostsMapper.class);
        comments = db.session.getMapper(CommentsMapper.class);
        db.jdbc.update("INSERT INTO users(id,username,email,password_hash) VALUES(1,'reader','reader@example.test','unused')");
        db.jdbc.update("INSERT INTO users(id,username,email,password_hash) VALUES(2,'history','history@example.test','unused')");
        db.jdbc.update("INSERT INTO categories(id,name) VALUES(1,'分类')");
        db.jdbc.update("INSERT INTO posts(id,title,content,category_id,author_id,status) VALUES(2,'文章','正文',1,1,'published')");
        db.jdbc.update("INSERT INTO post_favorites(user_id,post_id) VALUES(1,2)");
        db.jdbc.update("INSERT INTO user_view_history(user_id,post_id) VALUES(2,2)");
        db.jdbc.update("INSERT INTO comments(id,post_id,user_id,content) VALUES(10,2,1,'根'),(13,2,1,'另一根')");
        db.jdbc.update("INSERT INTO comments(id,post_id,user_id,parent_id,content) VALUES(11,2,1,10,'回复')");
        db.jdbc.update("INSERT INTO comments(id,post_id,user_id,parent_id,content) VALUES(12,2,1,11,'二级回复')");
    }

    @AfterEach void cleanup() throws Exception { if (db != null) db.close(); }

    @Test void deletingAndRestoringAncestorsKeepsAllPublicCountersConsistent() {
        assertPublicCounters(4);
        db.jdbc.update("UPDATE comments SET deleted_at=NOW() WHERE id=10");
        assertPublicCounters(1);
        db.jdbc.update("UPDATE comments SET deleted_at=NULL WHERE id=10");
        assertPublicCounters(4);
        db.jdbc.update("UPDATE comments SET deleted_at=NOW() WHERE id=11");
        assertPublicCounters(2);
        db.jdbc.update("UPDATE comments SET deleted_at=NULL WHERE id=11");
        assertPublicCounters(4);
    }

    @Test void authorDraftPreviewDoesNotCountHiddenPublicComments() {
        db.jdbc.update("UPDATE posts SET status='draft' WHERE id=2");
        assertEquals(0, comments.countCommentsByPostId(2L));
        assertEquals(0, posts.selectPostDetailResp(2L, 1L).getCommentCount());
        assertTrue(posts.selectLatestPostListResp(10, null).isEmpty());
    }

    private void assertPublicCounters(int expected) {
        assertEquals(expected, comments.countCommentsByPostId(2L));
        assertEquals(expected, posts.selectPostDetailResp(2L, null).getCommentCount());
        var page = posts.selectPostListResp(new Page<PostListResp>(1, 1), null, null, null,
                "published", null, null, null, "latest");
        assertEquals(1, page.getTotal());
        assertCount(expected, page.getRecords());
        assertCount(expected, posts.selectLatestPostListResp(10, null));
        assertCount(expected, posts.selectHotPostListResp(10, null));
        assertCount(expected, posts.selectRecommendedPostListResp(10, 1L));
        assertCount(expected, posts.selectFavoritePostList(new Page<PostListResp>(1, 1), 1L, null).getRecords());
        assertCount(expected, db.session.getMapper(UserViewHistoryMapper.class)
                .selectViewHistory(new Page<PostListResp>(1, 1), 2L).getRecords());
    }

    private void assertCount(int expected, List<PostListResp> rows) {
        assertEquals(1, rows.size());
        assertEquals(expected, rows.getFirst().getCommentCount());
    }
}
