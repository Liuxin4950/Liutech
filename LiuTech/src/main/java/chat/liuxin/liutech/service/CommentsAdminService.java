package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.CommentsMapper;
import chat.liuxin.liutech.model.Comments;
import chat.liuxin.liutech.resp.PageResp;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 评论管理服务类（管理端专用）
 *
 * @author 刘鑫
 */
@Slf4j
@Service
public class CommentsAdminService extends ServiceImpl<CommentsMapper, Comments> {

    @Autowired
    private CommentsMapper commentsMapper;

    /**
     * 分页查询评论列表（管理端）
     * 支持按文章ID、用户ID、状态过滤，可选包含已删除评论
     *
     * @param page          页码，从1开始
     * @param size          每页大小
     * @param postId        文章ID（可选）
     * @param userId        用户ID（可选）
     * @param status        状态过滤（可选）：deleted / active
     * @param includeDeleted 是否包含已删除评论
     * @return 分页结果
     */
    public PageResp<Comments> getCommentListForAdmin(Integer page, Integer size, Long postId,
                                                      Long userId, String status, Boolean includeDeleted) {
        Integer offset = (page - 1) * size;

        List<Comments> commentList = commentsMapper.selectCommentsForAdmin(offset, size, postId, userId, status, includeDeleted);
        Integer total = commentsMapper.countCommentsForAdmin(postId, userId, status, includeDeleted);

        PageResp<Comments> pageResp = new PageResp<>();
        pageResp.setRecords(commentList);
        pageResp.setTotal(total.longValue());
        pageResp.setCurrent(page.longValue());
        pageResp.setSize(size.longValue());
        pageResp.setPages((long) Math.ceil((double) total / size));
        pageResp.setHasNext(page.longValue() < pageResp.getPages());
        pageResp.setHasPrevious(page.longValue() > 1);

        return pageResp;
    }

    /**
     * 根据ID获取评论详情（包含用户信息和文章标题）
     *
     * @param id 评论ID
     * @return 评论信息，不存在时返回null
     */
    public Comments getCommentById(Long id) {
        if (id == null) {
            return null;
        }
        // 使用管理端关联查询，包含用户信息和文章标题
        return commentsMapper.selectCommentsForAdminById(id);
    }

    /**
     * 软删除评论
     *
     * @param id 评论ID
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean softDeleteComment(Long id) {
        try {
            if (id == null) {
                return false;
            }

            LambdaUpdateWrapper<Comments> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Comments::getId, id)
                    .isNull(Comments::getDeletedAt)
                    .set(Comments::getDeletedAt, new Date());

            int result = commentsMapper.update(null, updateWrapper);
            log.info("软删除评论ID: {}, 结果: {}", id, result > 0 ? "成功" : "失败");
            return result > 0;
        } catch (Exception e) {
            log.error("软删除评论失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 批量软删除评论
     *
     * @param ids 评论ID列表
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean batchSoftDeleteComments(List<Long> ids) {
        try {
            if (ids == null || ids.isEmpty()) {
                return false;
            }

            LambdaUpdateWrapper<Comments> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.in(Comments::getId, ids)
                    .isNull(Comments::getDeletedAt)
                    .set(Comments::getDeletedAt, new Date());

            int result = commentsMapper.update(null, updateWrapper);
            log.info("批量软删除评论数量: {}", result);
            return result > 0;
        } catch (Exception e) {
            log.error("批量软删除评论失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 恢复已删除的评论
     *
     * @param id 评论ID
     * @return 是否恢复成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean restoreComment(Long id) {
        try {
            if (id == null) {
                return false;
            }

            int result = commentsMapper.restoreCommentById(id);
            log.info("恢复评论ID: {}, 结果: {}", id, result > 0 ? "成功" : "失败");
            return result > 0;
        } catch (Exception e) {
            log.error("恢复评论失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 彻底删除评论（物理删除）
     * 递归删除所有子孙评论后再删除自身
     *
     * @param id 评论ID
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean permanentDeleteComment(Long id) {
        log.info("彻底删除评论 - 评论ID: {}", id);

        try {
            if (id == null) {
                return false;
            }

            // 递归查询所有子孙评论ID
            List<Long> descendantIds = commentsMapper.selectAllDescendantIds(List.of(id));
            if (descendantIds != null && !descendantIds.isEmpty()) {
                commentsMapper.permanentDeleteByIds(descendantIds);
                log.info("彻底删除评论的子孙评论数量: {}", descendantIds.size());
            }

            // 删除自身
            int result = commentsMapper.deleteById(id);
            boolean success = result > 0;
            log.info("彻底删除评论{} - 评论ID: {}", success ? "成功" : "失败", id);
            return success;
        } catch (Exception e) {
            log.error("彻底删除评论失败 - 评论ID: {}, 错误: {}", id, e.getMessage(), e);
            throw new RuntimeException("彻底删除评论失败: " + e.getMessage());
        }
    }

    /**
     * 批量彻底删除评论（物理删除）
     * 递归删除所有子孙评论后再删除自身
     *
     * @param ids 评论ID列表
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean batchPermanentDeleteComments(List<Long> ids) {
        log.info("批量彻底删除评论 - 评论数量: {}", ids.size());

        try {
            if (ids == null || ids.isEmpty()) {
                return false;
            }

            // 递归查询所有子孙评论ID
            List<Long> descendantIds = commentsMapper.selectAllDescendantIds(ids);
            if (descendantIds != null && !descendantIds.isEmpty()) {
                commentsMapper.permanentDeleteByIds(descendantIds);
                log.info("批量彻底删除评论的子孙评论数量: {}", descendantIds.size());
            }

            // 批量删除自身
            int result = commentsMapper.permanentDeleteByIds(ids);
            boolean success = result > 0;
            log.info("批量彻底删除评论{} - 影响评论数: {}", success ? "成功" : "失败", ids.size());
            return success;
        } catch (Exception e) {
            log.error("批量彻底删除评论失败 - 错误: {}", e.getMessage(), e);
            throw new RuntimeException("批量彻底删除评论失败: " + e.getMessage());
        }
    }
}
