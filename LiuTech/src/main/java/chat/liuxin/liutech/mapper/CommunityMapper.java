package chat.liuxin.liutech.mapper;

import chat.liuxin.liutech.model.*;
import chat.liuxin.liutech.resp.CommunityResp;
import org.apache.ibatis.annotations.*;
import java.util.Date;
import java.util.List;

/** 社区持久事实。写操作由服务事务约束；全站额度在 settings 行锁下计数。 */
@Mapper
public interface CommunityMapper {
    @Select("SELECT * FROM community_settings WHERE id=1")
    CommunitySettings settings();
    @Select("SELECT * FROM community_settings WHERE id=1 FOR UPDATE")
    CommunitySettings lockSettings();
    @Update("""
        UPDATE community_settings SET enabled=#{enabled}, bot_daily_comment_limit=#{botDailyCommentLimit},
        site_daily_comment_limit=#{siteDailyCommentLimit},post_daily_comment_limit=#{postDailyCommentLimit},
        bot_daily_task_limit=#{botDailyTaskLimit},site_daily_task_limit=#{siteDailyTaskLimit},
        min_delay_seconds=#{minDelaySeconds},max_delay_seconds=#{maxDelaySeconds},cooldown_seconds=#{cooldownSeconds},
        max_chain_comments=#{maxChainComments},version=version+1 WHERE id=1
        """)
    int updateSettings(CommunitySettings settings);

    @Select("SELECT * FROM community_bots WHERE deleted_at IS NULL ORDER BY id")
    List<CommunityBot> bots();
    @Select("SELECT * FROM community_bots WHERE id=#{id} AND deleted_at IS NULL")
    CommunityBot bot(Long id);
    /** 审查历史时保留停用及软删除角色身份。 */
    @Select("SELECT * FROM community_bots WHERE id=#{id}")
    CommunityBot botForAdmin(Long id);
    @Select("SELECT * FROM community_bots WHERE id=#{id} AND deleted_at IS NULL FOR UPDATE")
    CommunityBot lockBot(Long id);
    @Insert("""
        INSERT INTO community_bots(name,avatar_url,personality,system_prompt,background,interests,enabled,participation,version)
        VALUES(#{name},#{avatarUrl},#{personality},#{systemPrompt},#{background},#{interests},#{enabled},#{participation},1)
        """)
    @Options(useGeneratedKeys=true, keyProperty="id")
    int insertBot(CommunityBot bot);
    @Update("""
        UPDATE community_bots SET name=#{name},avatar_url=#{avatarUrl},personality=#{personality},system_prompt=#{systemPrompt},background=#{background},
        interests=#{interests},enabled=#{enabled},participation=#{participation},version=version+1 WHERE id=#{id}
        """)
    int updateBot(CommunityBot bot);
    @Update("UPDATE community_bots SET enabled=FALSE,deleted_at=NOW(),version=version+1 WHERE id=#{id}")
    int deleteBot(Long id);
    @Update("UPDATE community_bots SET version=version+1 WHERE id=#{id}")
    int incrementBotVersion(Long id);

    @Select("SELECT * FROM community_knowledge WHERE bot_id=#{botId} ORDER BY id")
    List<CommunityKnowledge> knowledge(Long botId);
    @Insert("INSERT INTO community_knowledge(bot_id,title,content,version) VALUES(#{botId},#{title},#{content},1)")
    @Options(useGeneratedKeys=true, keyProperty="id")
    int insertKnowledge(CommunityKnowledge knowledge);
    @Update("UPDATE community_knowledge SET title=#{title},content=#{content},version=version+1 WHERE id=#{id} AND bot_id=#{botId}")
    int updateKnowledge(CommunityKnowledge knowledge);
    @Delete("DELETE FROM community_knowledge WHERE id=#{id} AND bot_id=#{botId}")
    int deleteKnowledge(@Param("botId") Long botId, @Param("id") Long id);

    @Select("SELECT * FROM posts WHERE id=#{id} FOR UPDATE")
    Posts lockPost(Long id);
    @Select("SELECT * FROM posts WHERE id=#{id} AND status='published' AND deleted_at IS NULL")
    Posts publicPost(Long id);
    @Select("SELECT id FROM posts WHERE status='published' AND deleted_at IS NULL ORDER BY created_at DESC,id DESC LIMIT #{limit}")
    List<Long> recentPublicPostIds(int limit);
    @Select("""
        <script>SELECT id,title FROM posts WHERE status='published' AND deleted_at IS NULL
        AND id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY id</script>
        """)
    List<Posts> publicPostMetadata(@Param("ids") List<Long> ids);
    @Insert("INSERT IGNORE INTO community_post_state(post_id,enabled,first_public_seen,version) VALUES(#{id},TRUE,FALSE,1)")
    int ensurePost(Long id);
    @Select("SELECT * FROM community_post_state WHERE post_id=#{id}")
    CommunityPostState postState(Long id);
    @Update("UPDATE community_post_state SET first_public_seen=TRUE WHERE post_id=#{id} AND first_public_seen=FALSE")
    int markPublished(Long id);
    @Update("UPDATE community_post_state SET enabled=#{enabled},version=version+1 WHERE post_id=#{id} AND enabled!=#{enabled}")
    int setPostEnabled(@Param("id") Long id, @Param("enabled") boolean enabled);

    @Insert("INSERT INTO community_chains(root_event_id,post_id,emitted) VALUES(#{root},#{postId},0)")
    int insertChain(@Param("root") String root, @Param("postId") Long postId);
    @Select("SELECT emitted FROM community_chains WHERE root_event_id=#{root} AND post_id=#{postId} FOR UPDATE")
    Integer chainCount(@Param("root") String root, @Param("postId") Long postId);
    @Update("UPDATE community_chains SET emitted=emitted+1 WHERE root_event_id=#{root}")
    int incrementChain(String root);
    @Insert("""
        INSERT IGNORE INTO community_events(event_key,event_type,bot_id,post_id,comment_id,root_event_id,available_at)
        VALUES(#{key},#{event.eventType},#{event.botId},#{event.postId},#{event.commentId},#{event.rootEventId},TIMESTAMPADD(SECOND,#{delaySeconds},NOW(3)))
        """)
    int insertEvent(@Param("key") String key, @Param("event") CommunityEvent event,@Param("delaySeconds") int delaySeconds);
    /** 当前读而非事务快照，避免首次发布与补评在并发事务中重复安排。 */
    @Select("""
        SELECT id FROM community_events WHERE post_id=#{postId} AND bot_id=#{botId}
        AND (event_key LIKE 'ARTICLE_PUBLISHED:%' OR event_key LIKE 'MANUAL_INVITE:%') ORDER BY id LIMIT 1 FOR UPDATE
        """)
    Long lockArticleInvitation(@Param("postId") Long postId, @Param("botId") Long botId);
    @Select("""
        SELECT root_event_id FROM community_events
        WHERE event_key LIKE CONCAT('ARTICLE_PUBLISHED:',#{postId},':%')
        ORDER BY event_key LIMIT 1 FOR UPDATE
        """)
    String initialArticleRoot(Long postId);
    @Select("""
        SELECT task_id FROM community_publications WHERE bot_id=#{botId} AND post_id=#{postId}
        ORDER BY created_at LIMIT 1 FOR UPDATE
        """)
    String lockArticlePublication(@Param("botId") Long botId, @Param("postId") Long postId);
    @Select("""
        SELECT * FROM community_events WHERE acknowledged_at IS NULL AND available_at <= NOW()
        AND (lease_until IS NULL OR lease_until < NOW()) ORDER BY id LIMIT #{limit} FOR UPDATE SKIP LOCKED
        """)
    List<CommunityEvent> claimable(int limit);
    @Update("UPDATE community_events SET lease_token=#{token},lease_until=DATE_ADD(NOW(), INTERVAL #{seconds} SECOND) WHERE id=#{id}")
    int lease(@Param("id") Long id, @Param("token") String token, @Param("seconds") int seconds);
    @Update("UPDATE community_events SET acknowledged_at=NOW() WHERE id=#{id} AND lease_token=#{token} AND acknowledged_at IS NULL")
    int ack(@Param("id") Long id, @Param("token") String token);

    @Select("SELECT * FROM community_events WHERE id=#{id}")
    CommunityEvent event(Long id);
    @Select("SELECT * FROM community_events WHERE id=#{id} FOR UPDATE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    CommunityEvent lockEvent(Long id);
    /** 事件绑定与取消栅栏在正式发布事务内当前读，覆盖旧版 AI 请求。 */
    @Select("SELECT * FROM community_events WHERE bot_id=#{botId} AND post_id=#{postId} " +
            "AND root_event_id=#{root} AND comment_id <=> #{commentId} ORDER BY id LIMIT 1 FOR UPDATE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    CommunityEvent publicationEvent(@Param("botId") Long botId,@Param("postId") Long postId,
        @Param("root") String root,@Param("commentId") Long commentId);
    @Update("UPDATE community_events SET event_type='CANCELLED',acknowledged_at=COALESCE(acknowledged_at,NOW()), " +
            "lease_token=NULL,lease_until=NULL WHERE id=#{id}")
    int cancelEvent(Long id);
    @Select("<script>SELECT e.*,p.title AS post_title,LEFT(c.content,200) AS comment_preview," +
            "TIMESTAMPDIFF(SECOND,NOW(3),e.available_at) AS due_seconds," +
            "CAST(UNIX_TIMESTAMP(e.available_at)*1000 AS SIGNED) AS available_at_epoch_ms," +
            "TIMESTAMPDIFF(SECOND,NOW(3),e.lease_until) AS lease_remaining_seconds," +
            "IF(e.lease_token IS NOT NULL AND e.lease_until &gt; NOW(),'DISPATCHING','WAITING') AS status FROM community_events e " +
            "LEFT JOIN posts p ON p.id=e.post_id LEFT JOIN comments c ON c.id=e.comment_id " +
            "WHERE e.acknowledged_at IS NULL AND e.event_type!='CANCELLED' " +
            "<if test='botId != null'>AND e.bot_id=#{botId}</if> " +
            "<if test='postId != null'>AND e.post_id=#{postId}</if> " +
            "ORDER BY e.available_at,e.id</script>")
    List<CommunityEvent> pendingEvents(@Param("botId") Long botId,@Param("postId") Long postId);
    /** ACK 丢失时任务可能已发表；保留软删评论参与判断，不把撤回当作未发表。 */
    @Select("SELECT p.comment_id AS commentId,p.created_at AS createdAt,TRUE AS duplicate " +
            "FROM community_publications p JOIN comments c ON c.community_task_id=p.task_id " +
            "WHERE p.bot_id=#{botId} AND p.post_id=#{postId} AND c.root_event_id=#{rootEventId} " +
            "AND p.created_at >= #{createdAt} ORDER BY p.created_at DESC LIMIT 1")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    CommunityResp.Published eventPublication(CommunityEvent event);

    @Select("SELECT id FROM comments WHERE post_id=#{postId} FOR UPDATE")
    List<Long> lockPostComments(Long postId);

    @Select("SELECT * FROM comments WHERE id=#{id} AND deleted_at IS NULL FOR UPDATE")
    Comments lockComment(Long id);
    /** 最近讨论与触发祖先合并，避免长线程或排队旧任务丢失回复对象。根不可达时不返回零散评论。 */
    @Select("""
        WITH RECURSIVE ancestors AS (
          SELECT id,parent_id,0 AS depth FROM comments WHERE id=#{commentId} AND post_id=#{postId} AND deleted_at IS NULL
          UNION ALL SELECT c.id,c.parent_id,a.depth+1 FROM comments c JOIN ancestors a ON a.parent_id=c.id
          WHERE c.post_id=#{postId} AND c.deleted_at IS NULL AND a.depth<999
        ), thread AS (
          SELECT c.* FROM comments c JOIN ancestors a ON c.id=a.id WHERE a.parent_id IS NULL
          UNION ALL SELECT c.* FROM comments c JOIN thread t ON c.parent_id=t.id
          WHERE c.post_id=#{postId} AND c.deleted_at IS NULL
        ), recent AS (
          SELECT id FROM thread ORDER BY created_at DESC,id DESC LIMIT 100
        ), selected AS (
          SELECT id FROM recent UNION SELECT id FROM ancestors WHERE depth<=10
        ) SELECT t.*,u.username,u.avatar_url,b.name AS bot_name,b.avatar_url AS bot_avatar_url
        FROM thread t JOIN selected s ON s.id=t.id
        JOIN posts p ON p.id=t.post_id AND p.deleted_at IS NULL AND p.status='published'
        LEFT JOIN users u ON u.id=t.user_id LEFT JOIN community_bots b ON b.id=t.bot_id
        ORDER BY t.created_at,t.id
        """)
    @ResultMap("chat.liuxin.liutech.mapper.CommentsMapper.CommentWithUserMap")
    List<Comments> threadComments(@Param("postId") Long postId, @Param("commentId") Long commentId);

    @Select("SELECT comment_id AS commentId,created_at AS createdAt,TRUE AS duplicate FROM community_publications WHERE task_id=#{taskId}")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    CommunityResp.Published publication(String taskId);
    @Insert("INSERT INTO community_publications(task_id,bot_id,post_id,comment_id,created_at) VALUES(#{taskId},#{botId},#{postId},#{commentId},#{now})")
    int recordPublication(@Param("taskId") String taskId, @Param("botId") Long botId, @Param("postId") Long postId,
                          @Param("commentId") Long commentId, @Param("now") Date now);
    @Select("""
        SELECT COUNT(*) FROM community_publications WHERE created_at >= #{start} AND created_at < #{end}
        AND (#{botId} IS NULL OR bot_id=#{botId}) AND (#{postId} IS NULL OR post_id=#{postId})
        """)
    int publicationCount(@Param("botId") Long botId,@Param("postId") Long postId,@Param("start") Date start,@Param("end") Date end);
    @Select("SELECT MAX(created_at) FROM community_publications WHERE bot_id=#{botId} AND post_id=#{postId}")
    Date lastPublication(@Param("botId") Long botId,@Param("postId") Long postId);
    @Select("SELECT allowed,reason FROM community_attempts WHERE task_id=#{taskId} AND attempt=#{attempt}")
    CommunityResp.Attempt attempt(@Param("taskId") String taskId,@Param("attempt") int attempt);
    @Insert("INSERT INTO community_attempts(task_id,attempt,bot_id,post_id,allowed,reason) VALUES(#{taskId},#{attempt},#{botId},#{postId},#{allowed},#{reason})")
    int recordAttempt(@Param("taskId") String taskId,@Param("attempt") int attempt,@Param("botId") Long botId,
                      @Param("postId") Long postId,@Param("allowed") boolean allowed,@Param("reason") String reason);
    @Select("""
        SELECT COUNT(*) FROM community_attempts WHERE allowed=TRUE
        AND created_at >= FROM_UNIXTIME(#{start.time,jdbcType=BIGINT}/1000)
        AND created_at < FROM_UNIXTIME(#{end.time,jdbcType=BIGINT}/1000)
        AND (#{botId} IS NULL OR bot_id=#{botId})
        """)
    int attemptCount(@Param("botId") Long botId,@Param("start") Date start,@Param("end") Date end);
}
