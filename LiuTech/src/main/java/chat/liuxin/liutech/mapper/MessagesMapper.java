package chat.liuxin.liutech.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import chat.liuxin.liutech.model.Messages;

/**
 * 留言Mapper接口
 */
@Mapper
public interface MessagesMapper extends BaseMapper<Messages> {
    // MyBatis-Plus 提供基础CRUD，无需额外定义方法
}
