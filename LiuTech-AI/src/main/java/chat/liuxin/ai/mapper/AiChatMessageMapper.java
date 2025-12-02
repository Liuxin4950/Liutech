package chat.liuxin.ai.mapper;

import chat.liuxin.ai.entity.AiChatMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MyBatis-Plus Mapper：基础CRUD + 自定义查询在XML或注解扩展。
 * 作者：刘鑫
 * 时间：2025-12-01
 * 优化说明：添加高性能查询方法，解决N+1查询问题
 */
@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {
    
    /**
     * 通过用户ID直接查询最近N条消息（避免N+1查询）
     * 使用JOIN查询，一次数据库操作获取结果
     * 
     * @param userId 用户ID
     * @param limit 限制条数
     * @return 消息列表（按时间升序）
     */
    @Select("SELECT m.* FROM ai_chat_message m " +
            "INNER JOIN ai_conversation c ON m.conversation_id = c.id " +
            "WHERE c.user_id = #{userId} " +
            "ORDER BY m.created_at ASC, m.id ASC " +
            "LIMIT #{limit}")
    List<AiChatMessage> selectRecentMessagesByUserId(@Param("userId") String userId, @Param("limit") int limit);
    
    /**
     * 通过用户ID分页查询历史消息（避免N+1查询）
     * 使用JOIN查询，一次数据库操作获取结果
     * 
     * @param userId 用户ID
     * @param offset 偏移量
     * @param size   每页大小
     * @return 消息列表（按时间倒序）
     */
    @Select("SELECT m.* FROM ai_chat_message m " +
            "INNER JOIN ai_conversation c ON m.conversation_id = c.id " +
            "WHERE c.user_id = #{userId} " +
            "ORDER BY m.created_at DESC, m.id DESC " +
            "LIMIT #{off set}, #{size}")
    List<AiChatMessage> selectHistoryMessagesByUserId(@Param("userId") String userId, 
                                                     @Param("offset") int offset, 
                                                     @Param("size") int size);
    
    /**
     * 统计用户的消息总数（避免N+1查询）
     * 使用JOIN查询，一次数据库操作获取结果
     * 
     * @param userId 用户ID
     * @return 消息总数
     */
    @Select("SELECT COUNT(*) FROM ai_chat_message m " +
            "INNER JOIN ai_conversation c ON m.conversation_id = c.id " +
            "WHERE c.user_id = #{userId}")
    long countMessagesByUserId(@Param("userId") String userId);
    
    /**
     * 清理用户旧消息，保留最近N条（避免N+1查询）
     * 使用JOIN查询，一次数据库操作完成清理
     * 
     * @param userId 用户ID
     * @param retainLastN 保留条数
     * @return 删除的记录数
     */
    @Select("SELECT COUNT(*) FROM ai_chat_message m " +
            "INNER JOIN ai_conversation c ON m.conversation_id = c.id " +
            "WHERE c.user_id = #{userId} " +
            "AND m.created_at < (" +
            "  SELECT created_at FROM ai_chat_message m2 " +
            "  INNER JOIN ai_conversation c2 ON m2.conversation_id = c2.id " +
            "  WHERE c2.user_id = #{userId} " +
            "  ORDER BY m2.created_at DESC, m2.id DESC " +
            "  LIMIT 1 OFFSET #{retainLastN}" +
            ")")
    long countOldMessagesForCleanup(@Param("userId") String userId, @Param("retainLastN") int retainLastN);
}