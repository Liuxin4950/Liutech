package chat.liuxin.ai.mapper;

import chat.liuxin.ai.entity.AiConversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {
    /** 消息落库先锁会话行，在同一事务内校验属主、分配序号并更新统计。 */
    @Select("SELECT * FROM ai_conversation WHERE id = #{conversationId} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    AiConversation lockById(@Param("conversationId") Long conversationId);

    /** 只维护消息统计，避免读出的旧实体覆盖标题、归档状态等并发修改。 */
    @Update("UPDATE ai_conversation SET message_count = COALESCE(message_count, 0) + 1, " +
            "last_message_at = #{messageTime}, updated_at = #{messageTime} WHERE id = #{conversationId}")
    int incrementMessageCount(@Param("conversationId") Long conversationId,
                              @Param("messageTime") LocalDateTime messageTime);
}
