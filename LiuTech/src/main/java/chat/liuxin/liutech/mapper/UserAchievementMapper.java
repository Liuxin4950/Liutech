package chat.liuxin.liutech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import chat.liuxin.liutech.model.UserAchievementClaim;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserAchievementMapper extends BaseMapper<UserAchievementClaim> {
    /** 按用户串行领取，再查询领取记录；跨标签页重复请求读到同一已提交结果。 */
    @Select("SELECT id FROM users WHERE id = #{userId} AND deleted_at IS NULL FOR UPDATE")
    Long lockUser(@Param("userId") Long userId);
}
