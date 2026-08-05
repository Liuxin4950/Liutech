package chat.liuxin.liutech.service;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.PostSeriesMapper;
import chat.liuxin.liutech.mapper.PostsMapper;
import chat.liuxin.liutech.model.PostSeries;
import chat.liuxin.liutech.model.Posts;
import chat.liuxin.liutech.req.SeriesSortItemReq;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.PostSeriesResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文章系列服务类
 * 系列是有序的文章合集，一篇文章最多属于一个系列，通过 posts.series_id + posts.series_sort 维护归属与顺序。
 *
 * @author 刘鑫
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostSeriesService extends ServiceImpl<PostSeriesMapper, PostSeries> {

    private final PostSeriesMapper postSeriesMapper;

    private final PostsMapper postsMapper;

    private final ImageReferenceService imageReferenceService;

    /**
     * 查询所有系列（含已发布文章数），web 端列表用
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "postSeries", unless = "#result == null || #result.isEmpty()")
    public List<PostSeriesResp> getAllSeriesWithPostCount() {
        return postSeriesMapper.selectSeriesWithPostCount();
    }

    /**
     * 管理端分页查询系列列表
     */
    @Transactional(readOnly = true)
    public PageResp<PostSeriesResp> getSeriesListForAdmin(Integer page, Integer size, String name, Boolean includeDeleted) {
        Integer offset = (page - 1) * size;
        List<PostSeriesResp> list = postSeriesMapper.selectSeriesForAdmin(offset, size, name, includeDeleted);
        Integer total = postSeriesMapper.countSeriesForAdmin(name, includeDeleted);

        PageResp<PostSeriesResp> pageResp = new PageResp<>();
        pageResp.setRecords(list);
        pageResp.setTotal(total.longValue());
        pageResp.setCurrent(page.longValue());
        pageResp.setSize(size.longValue());
        pageResp.setPages((long) Math.ceil((double) total / size));
        pageResp.setHasNext(page.longValue() < pageResp.getPages());
        pageResp.setHasPrevious(page.longValue() > 1);
        return pageResp;
    }

    /**
     * 根据ID查询系列详情（含已发布文章数）
     */
    @Transactional(readOnly = true)
    public PostSeriesResp getSeriesDetail(Long id) {
        return postSeriesMapper.selectSeriesDetailById(id);
    }

    /**
     * 根据系列名查询系列（重名校验用）
     */
    @Transactional(readOnly = true)
    public PostSeries getSeriesByName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        LambdaQueryWrapper<PostSeries> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostSeries::getName, name.trim());
        return postSeriesMapper.selectOne(wrapper);
    }

    /**
     * 创建系列
     */
    @CacheEvict(value = "postSeries", allEntries = true)
    public boolean save(PostSeriesResp resp) {
        if (getSeriesByName(resp.getName()) != null) {
            throw new BusinessException(ErrorCode.SERIES_NAME_EXISTS);
        }
        PostSeries series = new PostSeries();
        series.setName(resp.getName());
        series.setDescription(resp.getDescription());
        series.setCoverImage(resp.getCoverImage());
        boolean saved = super.save(series);
        if (saved) {
            incrementCoverReference(series.getCoverImage());
        }
        return saved;
    }

    /**
     * 更新系列
     */
    @CacheEvict(value = "postSeries", allEntries = true)
    public boolean updateById(PostSeriesResp resp) {
        PostSeries exist = postSeriesMapper.selectById(resp.getId());
        if (exist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "系列不存在");
        }
        String oldCover = exist.getCoverImage();
        String newCover = resp.getCoverImage();
        PostSeries series = new PostSeries();
        series.setId(resp.getId());
        series.setName(resp.getName());
        series.setDescription(resp.getDescription());
        series.setCoverImage(newCover);
        boolean updated = super.updateById(series);
        if (updated && newCover != null && !newCover.equals(oldCover)) {
            decrementCoverReference(oldCover);
            incrementCoverReference(newCover);
        }
        return updated;
    }

    /**
     * 软删除系列
     * 文章的 series_id 不动；查询时 LEFT JOIN ... s.deleted_at IS NULL 使系列信息为 null。
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "postSeries", "postList", "hotPosts", "latestPosts" }, allEntries = true)
    public boolean removeByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        // 解除关联文章的系列归属（文章保留，仅移出系列），避免 series_id 残留指向已软删除系列
        LambdaUpdateWrapper<Posts> postsUpdate = new LambdaUpdateWrapper<>();
        postsUpdate.in(Posts::getSeriesId, ids)
                .isNull(Posts::getDeletedAt)
                .set(Posts::getSeriesId, null);
        postsMapper.update(null, postsUpdate);

        LambdaUpdateWrapper<PostSeries> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(PostSeries::getId, ids).set(PostSeries::getDeletedAt, new Date());
        return postSeriesMapper.update(null, wrapper) > 0;
    }

    /**
     * 恢复已删除的系列
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "postSeries", allEntries = true)
    public boolean restoreSeries(Long id) {
        return postSeriesMapper.restoreSeriesById(id) > 0;
    }

    /**
     * 彻底删除系列（物理删除）
     * 外键 ON DELETE SET NULL 自动把关联文章的 series_id 置空。
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "postSeries", "postList", "hotPosts", "latestPosts" }, allEntries = true)
    public boolean permanentDeleteSeries(Long id) {
        PostSeries series = postSeriesMapper.selectByIdWithDeleted(id);
        // 仅未软删除的系列才扣减（软删除时未扣减，已靠对账移除引用）
        if (series != null && series.getDeletedAt() == null) {
            decrementCoverReference(series.getCoverImage());
        }
        log.info("彻底删除系列 - 系列ID: {}", id);
        return postSeriesMapper.deleteBatchIds(Collections.singletonList(id)) > 0;
    }

    /**
     * 批量彻底删除系列（物理删除）
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "postSeries", "postList", "hotPosts", "latestPosts" }, allEntries = true)
    public boolean batchPermanentDeleteSeries(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        for (Long id : ids) {
            PostSeries series = postSeriesMapper.selectByIdWithDeleted(id);
            if (series != null && series.getDeletedAt() == null) {
                decrementCoverReference(series.getCoverImage());
            }
        }
        log.info("批量彻底删除系列 - 数量: {}", ids.size());
        return postSeriesMapper.deleteBatchIds(ids) > 0;
    }

    /**
     * 拖拽排序：批量更新系列内文章排序
     *
     * @param seriesId   系列ID（仅更新该系列内文章，防越权）
     * @param items      每项包含 postId 与 seriesSort
     * @param operatorId 操作人ID
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "postSeries", "postList", "hotPosts", "latestPosts" }, allEntries = true)
    public boolean batchUpdateSeriesSort(Long seriesId, List<SeriesSortItemReq> items, Long operatorId) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        for (SeriesSortItemReq item : items) {
            Integer sort = item.getSeriesSort() != null ? item.getSeriesSort() : 0;
            postsMapper.updateSeriesSort(item.getPostId(), seriesId, sort, operatorId);
        }
        return true;
    }

    /**
     * 增加系列封面图片引用计数
     */
    private void incrementCoverReference(String coverUrl) {
        if (!StringUtils.hasText(coverUrl)) {
            return;
        }
        imageReferenceService.addReferences(List.of(coverUrl));
    }

    /**
     * 减少系列封面图片引用计数
     */
    private void decrementCoverReference(String coverUrl) {
        if (!StringUtils.hasText(coverUrl)) {
            return;
        }
        imageReferenceService.removeReferences(List.of(coverUrl));
    }
}
