package chat.liuxin.ai.mapper;

import chat.liuxin.ai.entity.ChatMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * AI聊天消息Mapper接口
 * 
 * @author 刘鑫
 * @since 2025-01-31
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 根据会话ID获取消息历史记录
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 消息列表
     */
    @Select("SELECT * FROM chat_messages WHERE user_id = #{userId} AND session_id = #{sessionId} ORDER BY created_at ASC")
    List<ChatMessage> findByUserIdAndSessionId(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    /**
     * 根据会话ID获取消息数量
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 消息数量
     */
    @Select("SELECT COUNT(*) FROM chat_messages WHERE user_id = #{userId} AND session_id = #{sessionId}")
    Integer countByUserIdAndSessionId(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    /**
     * 获取会话最近的N条消息（用于上下文记忆）
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param limit 限制数量
     * @return 消息列表
     */
    @Select("SELECT * FROM chat_messages WHERE user_id = #{userId} AND session_id = #{sessionId} ORDER BY created_at DESC LIMIT #{limit}")
    List<ChatMessage> findRecentMessages(@Param("userId") Long userId, @Param("sessionId") String sessionId, @Param("limit") Integer limit);

    /**
     * 删除指定会话的所有消息
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 删除行数
     */
    @Delete("DELETE FROM chat_messages WHERE user_id = #{userId} AND session_id = #{sessionId}")
    int deleteByUserIdAndSessionId(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    /**
     * 删除用户所有消息
     * 
     * @param userId 用户ID
     * @return 删除行数
     */
    @Delete("DELETE FROM chat_messages WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 统计总消息数量
     * 
     * @return 总消息数量
     */
    @Select("SELECT COUNT(*) FROM chat_messages")
    Long countTotalMessages();

    /**
     * 统计今日消息数量
     * 
     * @return 今日消息数量
     */
    @Select("SELECT COUNT(*) FROM chat_messages WHERE DATE(created_at) = CURDATE()")
    Long countTodayMessages();
}