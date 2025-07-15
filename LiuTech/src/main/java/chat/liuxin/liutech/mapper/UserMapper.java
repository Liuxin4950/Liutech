package chat.liuxin.liutech.mapper;

import chat.liuxin.liutech.model.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

public interface UserMapper extends BaseMapper<User> {
    // 这里可以添加自定义查询方法
    // 例如：List<User> findByUserName(String userName);
    List<User> findByUserName(String userName);


}
