package chat.liuxin.liutech.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;

/** 主库持久化跨服务清理任务；无用户外键，用户物理删除后任务仍存在。 */
@Mapper
public interface UserPurgeTaskMapper {
    @Insert("INSERT INTO user_purge_tasks(user_id) VALUES(#{id}) ON DUPLICATE KEY UPDATE user_id=user_id")
    int enqueue(@Param("id") Long userId);

    @Select("SELECT user_id FROM user_purge_tasks WHERE completed_at IS NULL AND next_attempt_at<=NOW() " +
            "ORDER BY next_attempt_at,user_id LIMIT 20")
    List<Long> selectDueIds();

    @Update("UPDATE user_purge_tasks SET attempts=attempts+1, lease_token=#{token}, " +
            "next_attempt_at=DATE_ADD(NOW(),INTERVAL 60 SECOND) " +
            "WHERE user_id=#{id} AND completed_at IS NULL AND next_attempt_at<=NOW()")
    int claim(@Param("id") Long id, @Param("token") String leaseToken);

    @Update("UPDATE user_purge_tasks SET completed_at=NOW(),lease_token=NULL,last_error=NULL " +
            "WHERE user_id=#{id} AND lease_token=#{token} AND completed_at IS NULL")
    int complete(@Param("id") Long id, @Param("token") String leaseToken);

    @Update("UPDATE user_purge_tasks SET last_error=#{error},lease_token=NULL, " +
            "next_attempt_at=DATE_ADD(NOW(),INTERVAL LEAST(3600,30*POW(2,LEAST(attempts,7))) SECOND) " +
            "WHERE user_id=#{id} AND lease_token=#{token} AND completed_at IS NULL")
    int retryLater(@Param("id") Long id, @Param("token") String leaseToken, @Param("error") String error);
}
