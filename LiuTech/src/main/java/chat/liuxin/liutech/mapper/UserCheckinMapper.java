package chat.liuxin.liutech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import chat.liuxin.liutech.model.UserCheckin;
import chat.liuxin.liutech.resp.UserCheckinResp;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 用户签到记录Mapper接口
 *
 * @author 刘鑫
 * @since 2025-01-30
 */
@Mapper
public interface UserCheckinMapper extends BaseMapper<UserCheckin> {

    /**
     * 查询用户今日是否已签到
     *
     * @param userId 用户ID
     * @param date 日期
     * @return 签到记录
     */
    @Select("SELECT * FROM user_checkins WHERE user_id = #{userId} AND checkin_date = #{date}")
    UserCheckin findByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    /**
     * 查询用户最近的签到记录
     *
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 签到记录列表
     */
    @Select("SELECT * FROM user_checkins WHERE user_id = #{userId} ORDER BY checkin_date DESC LIMIT #{limit}")
    List<UserCheckin> findRecentCheckins(@Param("userId") Long userId, @Param("limit") Integer limit);

    /**
     * 统计用户总签到次数
     *
     * @param userId 用户ID
     * @return 总签到次数
     */
    @Select("SELECT COUNT(*) FROM user_checkins WHERE user_id = #{userId}")
    Integer countByUserId(@Param("userId") Long userId);

    /**
     * 查询用户最后一次签到记录
     *
     * @param userId 用户ID
     * @return 最后一次签到记录
     */
    @Select("SELECT * FROM user_checkins WHERE user_id = #{userId} ORDER BY checkin_date DESC LIMIT 1")
    UserCheckin findLastCheckinByUserId(@Param("userId") Long userId);

    /**
     * 管理端分页查询签到记录（关联用户名）
     *
     * @param offset    偏移量
     * @param limit     每页大小
     * @param userId    用户ID（可选）
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @return 带用户名的签到记录列表
     */
    List<UserCheckinResp> selectCheckinsForAdmin(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * 管理端统计签到记录总数
     *
     * @param userId    用户ID（可选）
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @return 总数
     */
    Long countCheckinsForAdmin(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}