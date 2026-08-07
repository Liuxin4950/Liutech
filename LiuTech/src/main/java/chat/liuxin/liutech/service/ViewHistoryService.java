package chat.liuxin.liutech.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.PostsMapper;
import chat.liuxin.liutech.mapper.UserViewHistoryMapper;
import chat.liuxin.liutech.model.Posts;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.PostListResp;
import lombok.RequiredArgsConstructor;

/**
 * 用户浏览历史服务
 * 记录/查询/清空当前用户的文章浏览历史。
 * 浏览历史按用户维度存储，同一文章重复浏览只刷新时间（列表置顶）。
 *
 * @author 刘鑫
 * @date 2026-08-07
 */
@Service
@RequiredArgsConstructor
public class ViewHistoryService {

    private final UserViewHistoryMapper userViewHistoryMapper;

    private final PostsMapper postsMapper;

    /**
     * 记录一次浏览
     * 文章不存在或已删除时拒绝记录，避免留下垃圾数据。
     *
     * @param postId 文章ID
     * @param userId 用户ID
     * @throws BusinessException 当文章不存在或已删除时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordView(Long postId, Long userId) {
        Posts post = postsMapper.selectById(postId);
        if (post == null || post.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND, "文章不存在");
        }
        userViewHistoryMapper.upsertViewHistory(userId, postId);
    }

    /**
     * 分页查询当前用户的浏览历史（按最近浏览时间倒序）
     *
     * @param page   页码
     * @param size   每页条数
     * @param userId 用户ID
     * @return 浏览历史文章分页（含 viewedAt 最近浏览时间）
     */
    @Transactional(readOnly = true)
    public PageResp<PostListResp> getViewHistory(Integer page, Integer size, Long userId) {
        Page<PostListResp> pageParam = new Page<>(page, size);
        IPage<PostListResp> result = userViewHistoryMapper.selectViewHistory(pageParam, userId);
        return new PageResp<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 清空当前用户的浏览历史
     *
     * @param userId 用户ID
     * @return 清除的记录数
     */
    @Transactional(rollbackFor = Exception.class)
    public int clearViewHistory(Long userId) {
        return userViewHistoryMapper.deleteByUserId(userId);
    }
}
