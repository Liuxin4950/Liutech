package chat.liuxin.ai.agent.persistence;

import chat.liuxin.ai.agent.domain.AgentToolCall;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentToolCallMapper extends BaseMapper<AgentToolCall> {
}
