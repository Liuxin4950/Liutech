package chat.liuxin.liutech.mapper;

import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.resl.UserResl;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<Users> {
    // 这里可以添加自定义查询方法
    // 例如：List<User> findByUserName(String userName);
    List<Users> findByUserName(String username);
    
    // 根据邮箱查询用户
    List<Users> findByEmail(String email);

    /**
     * 管理端分页查询用户列表（包含统计信息）
     * @param offset 偏移量
     * @param limit 限制数量
     * @param keyword 关键词（可选，模糊搜索用户名或邮箱）
     * @return 用户列表
     */
    List<UserResl> selectUsersForAdmin(@Param("offset") Integer offset, 
                                      @Param("limit") Integer limit, 
                                      @Param("keyword") String keyword);

    /**
     * 管理端查询用户总数
     * @param keyword 关键词（可选，模糊搜索用户名或邮箱）
     * @return 总数
     */
    Integer countUsersForAdmin(@Param("keyword") String keyword);

}
