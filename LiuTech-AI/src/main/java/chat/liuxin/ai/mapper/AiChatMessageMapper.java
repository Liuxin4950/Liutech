package chat.liuxin.ai.mapper;

import chat.liuxin.ai.entity.AiChatMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus Mapper：基础CRUD + 自定义查询在XML或注解扩展。
 */
@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {
}