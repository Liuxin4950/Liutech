package chat.liuxin.liutech.mapper;

import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.resp.UserResp;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
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

    /**
     * 恢复已删除的用户（软删除恢复）
     * @param id 用户ID
     * @param updatedBy 更新人ID
     * @return 影响的行数
     */
    int restoreUserById(@Param("id") Long id, @Param("updatedBy") Long updatedBy);

    /**
     * 批量恢复已删除的用户
     * @param ids 用户ID列表
     * @param updatedBy 更新人ID
     * @return 影响的行数
     */
    int restoreUsersByIds(@Param("ids") List<Long> ids, @Param("updatedBy") Long updatedBy);

    /**
     * 统计用户总数（用于仪表盘）
     * @return 用户总数
     */
    Long countTotalUsers();

    /**
     * 统计指定日期注册的用户数量
     *
     * @param date 日期（只比较年月日）
     * @return 用户数量
     */
    Integer countUsersByDate(@Param("date") Date date);

    /**
     * 根据用户ID查询角色
     *
     * @param id 用户ID
     * @return 角色字符串 (user/admin)
     */
    String selectRoleById(@Param("id") Long id);

    /**
     * 查询所有用户头像URL（用于孤立图片清理）
     *
     * @return 头像URL列表
     */
    @Select("SELECT avatar_url FROM users WHERE avatar_url IS NOT NULL AND deleted_at IS NULL")
    List<String> selectAllAvatarUrls();

    /**
     * 使用乐观锁扣减用户积分（原子操作）
     *
     * @param userId 用户ID
     * @param amount 扣减金额
     * @param currentVersion 当前版本号
     * @param newVersion 新版本号
     * @return 影响的行数（0表示失败，1表示成功）
     */
    int deductPointsWithVersion(
        @Param("userId") Long userId,
        @Param("amount") java.math.BigDecimal amount,
        @Param("currentVersion") Integer currentVersion,
        @Param("newVersion") Integer newVersion
    );

}
