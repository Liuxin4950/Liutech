package chat.liuxin.liutech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import chat.liuxin.liutech.model.UserViewHistory;
import chat.liuxin.liutech.resp.PostListResp;

/**
 * 用户浏览历史Mapper接口
 *
 * @author 刘鑫
 * @date 2026-08-07
 */
@Mapper
public interface UserViewHistoryMapper extends BaseMapper<UserViewHistory> {

    /**
     * 记录浏览：不存在则插入，已存在则刷新浏览时间（upsert）
     * @param userId 用户ID
     * @param postId 文章ID
     * @return 影响行数
     */
    int upsertViewHistory(@Param("userId") Long userId, @Param("postId") Long postId);

    /**
     * 分页查询用户浏览历史（JOIN 文章信息，仅已发布未删除文章，按浏览时间倒序）
     * @param page 分页参数
     * @param userId 用户ID
     * @return 浏览历史文章分页
     */
    IPage<PostListResp> selectViewHistory(Page<PostListResp> page, @Param("userId") Long userId);

    /**
     * 清空用户的浏览历史
     * @param userId 用户ID
     * @return 影响行数
     */
    int deleteByUserId(@Param("userId") Long userId);
}
