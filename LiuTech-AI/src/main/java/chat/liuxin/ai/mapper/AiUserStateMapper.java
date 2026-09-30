package chat.liuxin.ai.mapper;

import org.apache.ibatis.annotations.*;

/** 永久清理留下 ID 墓碑，防止旧认证缓存/在途请求重新创建用户数据。 */
@Mapper
public interface AiUserStateMapper {
    @Insert("INSERT INTO ai_user_state(user_id) VALUES(#{id}) ON DUPLICATE KEY UPDATE user_id=user_id")
    int ensureExists(@Param("id") String userId);

    @Select("SELECT purged FROM ai_user_state WHERE user_id=#{id} FOR UPDATE")
    Integer selectPurgedForUpdate(@Param("id") String userId);

    @Update("UPDATE ai_user_state SET purged=1,updated_at=NOW() WHERE user_id=#{id}")
    int markPurged(@Param("id") String userId);
}
