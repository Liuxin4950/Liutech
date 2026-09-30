package chat.liuxin.ai.mapper;

import chat.liuxin.ai.entity.AiConversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {
    @Select("SELECT * FROM ai_conversation WHERE id = #{id} FOR UPDATE")
    AiConversation selectForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM ai_conversation WHERE user_id = #{userId} ORDER BY id FOR UPDATE")
    List<AiConversation> selectOwnedForUpdate(@Param("userId") String userId);

    @Select("SELECT id, user_id, title, created_at, updated_at, status, message_count, last_message_at " +
            "FROM ai_conversation WHERE user_id = #{userId} AND status != 9 " +
            "ORDER BY updated_at DESC, id DESC LIMIT #{offset}, #{size}")
    List<AiConversation> selectVisiblePage(@Param("userId") String userId,
                                          @Param("offset") long offset, @Param("size") int size);

    @Update("UPDATE ai_conversation SET message_count = COALESCE(message_count, 0) + 1, " +
            "last_message_at = NOW(), updated_at = NOW() WHERE id = #{id}")
    int incrementMessageCount(@Param("id") Long id);
}
