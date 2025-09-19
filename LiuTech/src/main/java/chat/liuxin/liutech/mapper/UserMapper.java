package chat.liuxin.liutech.mapper;

import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.resp.UserResp;
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
     * @param username 用户名（可选，模糊搜索）
     * @param email 邮箱（可选，模糊搜索）
     * @param status 用户状态（可选，0禁用，1启用）
     * @param includeDeleted 是否包含已删除用户
     * @return 用户列表
     */
    List<UserResp> selectUsersForAdmin(@Param("offset") Integer offset,
                                       @Param("limit") Integer limit,
                                       @Param("username") String username,
                                       @Param("email") String email,
                                       @Param("status") Integer status,
                                       @Param("includeDeleted") Boolean includeDeleted);

    /**
     * 管理端查询用户总数
     * @param username 用户名（可选，模糊搜索）
     * @param email 邮箱（可选，模糊搜索）
     * @param status 用户状态（可选，0禁用，1启用）
     * @param includeDeleted 是否包含已删除用户
     * @return 总数
     */
    Integer countUsersForAdmin(@Param("username") String username,
                              @Param("email") String email,
                              @Param("status") Integer status,
                              @Param("includeDeleted") Boolean includeDeleted);

}
