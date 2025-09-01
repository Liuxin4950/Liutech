package chat.liuxin.ai.mapper;

import chat.liuxin.ai.entity.ChatSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * AI聊天会话Mapper接口
 * 
 * @author 刘鑫
 * @since 2025-01-31
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    /**
     * 根据用户ID和会话ID查找会话
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 会话信息
     */
    @Select("SELECT * FROM chat_sessions WHERE user_id = #{userId} AND session_id = #{sessionId} AND deleted_at IS NULL")
    ChatSession findByUserIdAndSessionId(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    /**
     * 根据用户ID获取所有会话列表
     * 
     * @param userId 用户ID
     * @return 会话列表
     */
    @Select("SELECT * FROM chat_sessions WHERE user_id = #{userId} AND deleted_at IS NULL ORDER BY updated_at DESC")
    List<ChatSession> findByUserId(@Param("userId") Long userId);

    /**
     * 更新会话消息数量
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param messageCount 消息数量
     * @return 更新行数
     */
    @Update("UPDATE chat_sessions SET message_count = #{messageCount}, updated_at = NOW() WHERE user_id = #{userId} AND session_id = #{sessionId}")
    int updateMessageCount(@Param("userId") Long userId, @Param("sessionId") String sessionId, @Param("messageCount") Integer messageCount);

    /**
     * 软删除指定会话
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 更新行数
     */
    @Update("UPDATE chat_sessions SET deleted_at = NOW() WHERE user_id = #{userId} AND session_id = #{sessionId}")
    int softDeleteByUserIdAndSessionId(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    /**
     * 软删除用户所有会话
     * 
     * @param userId 用户ID
     * @return 更新行数
     */
    @Update("UPDATE chat_sessions SET deleted_at = NOW() WHERE user_id = #{userId}")
    int softDeleteByUserId(@Param("userId") Long userId);

    /**
     * 统计活跃会话数量
     * 
     * @return 活跃会话数量
     */
    @Select("SELECT COUNT(DISTINCT CONCAT(user_id, '-', session_id)) FROM chat_sessions WHERE deleted_at IS NULL")
    Long countActiveSessions();

    /**
     * 统计活跃用户数量
     * 
     * @return 活跃用户数量
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM chat_sessions WHERE deleted_at IS NULL")
    Long countActiveUsers();
}