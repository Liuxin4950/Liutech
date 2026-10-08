package chat.liuxin.ai.mapper;

import chat.liuxin.ai.dto.community.CommunityTask;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

/** 社区执行状态唯一持久入口；角色和文章事实始终来自主服务。 */
@Mapper
public interface CommunityMapper {
    @Insert("INSERT IGNORE INTO ai_community_inbox(event_id,event_json) VALUES(#{eventId},#{eventJson})")
    int inbox(@Param("eventId") long eventId, @Param("eventJson") String eventJson);

    @Insert("INSERT IGNORE INTO ai_community_task(id,event_id,bot_id,post_id,comment_id,root_event_id,status,available_at,memory_epoch) " +
            "VALUES(#{id},#{eventId},#{botId},#{postId},#{commentId},#{rootEventId},'READY',NOW(),#{memoryEpoch})")
    int task(CommunityTask task);

    @Update("UPDATE ai_community_worker SET lease_token=#{token},lease_until=TIMESTAMPADD(SECOND,#{seconds},NOW()) " +
            "WHERE id=1 AND (lease_until IS NULL OR lease_until < NOW())")
    int acquireWorker(@Param("token") String token, @Param("seconds") int seconds);

    @Update("UPDATE ai_community_worker SET lease_token=NULL,lease_until=NULL WHERE id=1 AND lease_token=#{token}")
    int releaseWorker(String token);

    @Select("SELECT * FROM ai_community_task WHERE status IN ('READY','RUNNING','DECIDED') AND available_at <= NOW() " +
            "AND (lease_until IS NULL OR lease_until < NOW()) ORDER BY created_at,id LIMIT 1 FOR UPDATE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    CommunityTask next();

    @Update("UPDATE ai_community_task SET status=IF(decision_json IS NULL,'RUNNING','DECIDED'), " +
            "lease_until=TIMESTAMPADD(SECOND,#{seconds},NOW()) WHERE id=#{id} " +
            "AND status IN ('READY','RUNNING','DECIDED') AND (lease_until IS NULL OR lease_until < NOW())")
    int lease(@Param("id") String id, @Param("seconds") int seconds);

    @Update("UPDATE ai_community_task SET attempts=attempts+1,memory_epoch=COALESCE(memory_epoch,#{epoch}) WHERE id=#{id} AND status IN ('READY','RUNNING','DECIDED')")
    int attempt(@Param("id") String id, @Param("epoch") long epoch);

    @Update("UPDATE ai_community_task SET decision_json=#{json},context_version=#{version},status='DECIDED' WHERE id=#{id} AND status IN ('READY','RUNNING','DECIDED')")
    int decide(@Param("id") String id,@Param("json") String json,@Param("version") String version);

    @Update("UPDATE ai_community_task SET status=#{status},error=#{error},lease_until=NULL WHERE id=#{id} AND status IN ('READY','RUNNING','DECIDED')")
    int finish(@Param("id") String id,@Param("status") String status,@Param("error") String error);

    /** 最后一次失败也计数；过期决策即使耗尽自动重试也必须失效。 */
    @Update("UPDATE ai_community_task SET status=#{status},failures=failures+1,error=#{error},lease_until=NULL, " +
            "decision_json=IF(#{stale},NULL,decision_json),context_version=IF(#{stale},NULL,context_version) WHERE id=#{id} AND status IN ('READY','RUNNING','DECIDED')")
    int fail(@Param("id") String id,@Param("status") String status,@Param("error") String error,@Param("stale") boolean stale);

    @Update("UPDATE ai_community_task SET status='READY',decision_json=IF(#{stale},NULL,decision_json), " +
            "context_version=IF(#{stale},NULL,context_version), " +
            "failures=failures+1,error=#{error},lease_until=NULL,available_at=TIMESTAMPADD(SECOND,#{delay},NOW()) WHERE id=#{id} AND status IN ('READY','RUNNING','DECIDED')")
    int retry(@Param("id") String id,@Param("error") String error,@Param("stale") boolean stale,@Param("delay") int delay);

    @Select("<script>SELECT * FROM (SELECT * FROM ai_community_task <where>" +
            "<if test='botId != null'>AND bot_id=#{botId}</if> " +
            "<if test='postId != null'>AND post_id=#{postId}</if> " +
            "<choose><when test='status != null'>AND status=#{status}</when><otherwise>AND status IN ('READY','RUNNING','DECIDED','FAILED')</otherwise></choose></where> " +
            "<if test='status == null'>UNION ALL (SELECT * FROM ai_community_task WHERE status NOT IN ('READY','RUNNING','DECIDED','FAILED') " +
            "<if test='botId != null'>AND bot_id=#{botId}</if><if test='postId != null'>AND post_id=#{postId}</if> " +
            "ORDER BY created_at DESC,id DESC LIMIT #{limit})</if>) queue " +
            "ORDER BY IF(status IN ('READY','RUNNING','DECIDED'),0,1),created_at DESC,id DESC " +
            "<if test='status != null'>LIMIT #{limit}</if></script>")
    List<CommunityTask> tasks(@Param("botId") Long botId,@Param("postId") Long postId,@Param("status") String status,@Param("limit") int limit);

    @Select("SELECT * FROM ai_community_task WHERE id=#{id}")
    CommunityTask taskById(String id);
    @Select("SELECT COUNT(*)>0 FROM ai_community_task WHERE id=#{id} AND status='CANCELLED'")
    boolean cancelled(String id);
    @Update("UPDATE ai_community_task SET status='CANCELLED',error='管理员已取消任务',lease_until=NULL " +
            "WHERE id=#{id} AND status!='SUCCEEDED'")
    int cancel(String id);

    @Select("SELECT * FROM ai_community_task WHERE id=#{id} FOR UPDATE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    CommunityTask taskForRetry(String id);

    /** 同一 taskId 继续执行；保留已生成结果、尝试次数与记忆版本，发布重交仍幂等。 */
    @Update("UPDATE ai_community_task SET status='READY',failures=0,error=NULL,available_at=NOW(),lease_until=NULL, " +
            "decision_json=IF(#{regenerate},NULL,decision_json),context_version=IF(#{regenerate},NULL,context_version) " +
            "WHERE id=#{id} AND status='FAILED'")
    int retryFailed(@Param("id") String id,@Param("regenerate") boolean regenerate);

    @Insert("INSERT INTO ai_community_run(id,task_id,bot_id,post_id,status,result_json,error) VALUES(#{id},#{taskId},#{botId},#{postId},#{status},#{json},#{error})")
    int run(@Param("id") String id,@Param("taskId") String taskId,@Param("botId") long botId,
            @Param("postId") long postId,@Param("status") String status,@Param("json") String json,@Param("error") String error);

    @Update("UPDATE ai_community_run SET status=#{status},result_json=#{json},error=#{error} " +
            "WHERE id=#{id} AND task_id=#{taskId} AND bot_id=#{botId}")
    int updateRun(@Param("id") String id,@Param("taskId") String taskId,@Param("botId") long botId,
                  @Param("status") String status,@Param("json") String json,@Param("error") String error);

    /** 兼容升级前已缓存的决策；正式决策对应同一任务最后一轮有正文的生成记录。 */
    @Select("SELECT id FROM ai_community_run WHERE task_id=#{taskId} " +
            "AND JSON_UNQUOTE(JSON_EXTRACT(result_json,'$.decision')) IN ('COMMENT','REPLY') " +
            "ORDER BY created_at DESC,id DESC LIMIT 1")
    String latestGeneratedRun(String taskId);

    @Select("<script>SELECT r.id,r.task_id,r.bot_id,r.post_id,t.comment_id,r.status,r.result_json,r.error,r.created_at " +
            "FROM ai_community_run r LEFT JOIN ai_community_task t ON t.id=r.task_id " +
            "<where><if test='botId != null'>AND r.bot_id=#{botId}</if> " +
            "<if test='postId != null'>AND r.post_id=#{postId}</if></where> ORDER BY r.created_at DESC,r.id DESC LIMIT #{limit}</script>")
    List<Map<String,Object>> runs(@Param("botId") Long botId,@Param("postId") Long postId,@Param("limit") int limit);

    @Insert("INSERT IGNORE INTO ai_community_role_state(bot_id,memory_epoch) VALUES(#{botId},0)")
    int ensureRole(long botId);

    @Select("SELECT memory_epoch FROM ai_community_role_state WHERE bot_id=#{botId} FOR UPDATE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    long epoch(long botId);

    @Update("UPDATE ai_community_role_state SET memory_epoch=memory_epoch+1 WHERE bot_id=#{botId}")
    int advanceEpoch(long botId);

    @Delete("DELETE FROM ai_community_memory WHERE bot_id=#{botId}")
    int clearMemory(long botId);

    @Select("SELECT id,bot_id,source_post_id,source_comment_id,summary,created_at FROM ai_community_memory WHERE bot_id=#{botId} ORDER BY created_at DESC LIMIT 100")
    List<Map<String,Object>> memories(long botId);

    @Select("SELECT user_id FROM ai_community_memory_participant WHERE memory_id=#{memoryId}")
    List<Long> participants(String memoryId);

    @Insert("INSERT IGNORE INTO ai_community_memory(id,task_id,bot_id,source_post_id,source_comment_id,summary) " +
            "VALUES(#{id},#{taskId},#{botId},#{postId},#{commentId},#{summary})")
    int memory(@Param("id") String id,@Param("taskId") String taskId,@Param("botId") long botId,
               @Param("postId") long postId,@Param("commentId") long commentId,@Param("summary") String summary);

    @Insert("INSERT IGNORE INTO ai_community_memory_participant(memory_id,user_id) VALUES(#{memoryId},#{userId})")
    int participant(@Param("memoryId") String memoryId,@Param("userId") long userId);

    @Delete("DELETE FROM ai_community_memory WHERE id=#{id} AND bot_id=#{botId}")
    int deleteMemory(@Param("id") String id,@Param("botId") long botId);

    @Select("SELECT DISTINCT m.bot_id FROM ai_community_memory m JOIN ai_community_memory_participant p ON m.id=p.memory_id WHERE p.user_id=#{userId} ORDER BY m.bot_id")
    List<Long> participantBots(long userId);

    @Delete("DELETE m FROM ai_community_memory m JOIN ai_community_memory_participant p ON m.id=p.memory_id WHERE p.user_id=#{userId}")
    int purgeParticipant(long userId);
    @Insert("INSERT IGNORE INTO ai_community_user_state(user_id,purged) VALUES(#{userId},0)")
    int ensureUser(long userId);
    @Select("SELECT purged FROM ai_community_user_state WHERE user_id=#{userId} FOR UPDATE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    boolean userPurged(long userId);
    @Update("UPDATE ai_community_user_state SET purged=1 WHERE user_id=#{userId}")
    int markUserPurged(long userId);
    @Insert("INSERT IGNORE INTO ai_community_memory_source(memory_id,source_comment_id) VALUES(#{memoryId},#{commentId})")
    int memorySource(@Param("memoryId") String memoryId,@Param("commentId") long commentId);
    @Select("SELECT source_comment_id FROM ai_community_memory_source WHERE memory_id=#{memoryId}")
    List<Long> memorySources(String memoryId);
}
