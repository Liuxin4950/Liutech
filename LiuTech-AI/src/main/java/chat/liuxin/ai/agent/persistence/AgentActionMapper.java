package chat.liuxin.ai.agent.persistence;

import chat.liuxin.ai.agent.domain.AgentAction;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentActionMapper extends BaseMapper<AgentAction> {
}
