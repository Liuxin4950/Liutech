package chat.liuxin.liutech.service;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.CarouselMapper;
import chat.liuxin.liutech.mapper.ImagesMapper;
import chat.liuxin.liutech.model.Carousel;
import chat.liuxin.liutech.model.Images;
import chat.liuxin.liutech.resp.CarouselResp;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

/**
 * 轮播图服务类
 *
 * 主要功能：
 * 1. 轮播图的增删改查操作
 * 2. 状态管理和排序
 * 3. 软删除支持
 *
 * @author liuxin
 */
@Slf4j
@Service
@RequiredArgsConstructor
 extends ServiceImpl<CarouselMapper, Carousel> {

    private final CarouselMapper carouselMapper;

    private final ImagesMapper imagesMapper;

    private final ImagesService imagesService;

    /**
     * 获取启用的轮播图列表（前台展示）
     * @return 轮播图响应列表
     */
    @Transactional(readOnly = true)
    public List<CarouselResp> getActiveCarousels() {
        List<Carousel> carousels = carouselMapper.selectActiveCarousels();
        return carousels.stream().map(this::convertToResp).collect(Collectors.toList());
    }

    /**
     * 获取有效轮播图（分页）
     * @param current 当前页
     * @param size 每页大小
     * @return 轮播图分页数据
     */
    @Transactional(readOnly = true)
    public IPage<CarouselResp> getValidCarousels(long current, long size) {
        Page<Carousel> page = new Page<>(current, size);
        IPage<Carousel> carouselPage = carouselMapper.selectValidCarousels(page);
        return carouselPage.convert(this::convertToResp);
    }

    /**
     * 管理员分页查询所有轮播图
     * @param current 当前页
     * @param size 每页大小
     * @param status 状态筛选
     * @param includeDeleted 是否包含已删除的轮播图
     * @return 轮播图分页数据
     */
    @Transactional(readOnly = true)
    public IPage<CarouselResp> getAllCarousels(long current, long size, Integer status, Boolean includeDeleted) {
        Page<Carousel> page = new Page<>(current, size);
        // 使用自定义查询，绕过 @TableLogic 逻辑删除
        IPage<Carousel> carouselPage = carouselMapper.selectAllCarouselsWithDeleted(page, status, Boolean.TRUE.equals(includeDeleted));
        return carouselPage.convert(this::convertToResp);
    }

    /**
     * 根据ID获取轮播图详情
     * @param id 轮播图ID
     * @return 轮播图详情
     */
    @Transactional(readOnly = true)
    public CarouselResp getCarouselById(Long id) {
        validateCarouselId(id);
        // 使用自定义查询，绕过 @TableLogic
        Carousel carousel = carouselMapper.selectByIdWithDeleted(id);
        if (carousel == null || carousel.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "轮播图不存在");
        }
        return convertToResp(carousel);
    }

    /**
     * 创建轮播图
     * @param carousel 轮播图数据
     * @return 轮播图ID
     */
    @Transactional
    public Long createCarousel(Carousel carousel) {
        validateCarouselData(carousel);

        // 设置默认值
        if (carousel.getSortOrder() == null) {
            carousel.setSortOrder(0);
        }
        if (carousel.getStatus() == null) {
            carousel.setStatus(1);
        }

        boolean success = this.save(carousel);
        if (!success) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建轮播图失败");
        }

        incrementImageReference(carousel.getImageUrl());

        return carousel.getId();
    }

    /**
     * 更新轮播图
     * @param carousel 轮播图数据
     * @return 是否成功
     */
    @Transactional
    public boolean updateCarousel(Carousel carousel) {
        validateCarouselId(carousel.getId());
        validateCarouselData(carousel);
        Carousel existCarousel = carouselMapper.selectByIdWithDeleted(carousel.getId());
        if (existCarousel == null || existCarousel.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "轮播图不存在");
        }

        // 处理图片变化
        String oldImageUrl = existCarousel.getImageUrl();
        String newImageUrl = carousel.getImageUrl();

        if (newImageUrl != null && !newImageUrl.equals(oldImageUrl)) {
            decrementImageReference(oldImageUrl);
            incrementImageReference(newImageUrl);
        }

        return this.updateById(carousel);
    }

    /**
     * 删除轮播图（软删除）
     * @param id 轮播图ID
     * @return 是否成功
     */
    @Transactional
    public boolean deleteCarousel(Long id) {
        validateCarouselId(id);
        Carousel carousel = carouselMapper.selectByIdWithDeleted(id);
        if (carousel == null || carousel.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "轮播图不存在");
        }

        // 软删除不改变 usage_count（引用仍存在，只是标记删除）
        // usage_count 只在物理删除时减少

        return performSoftDelete(id);
    }

    /**
     * 批量删除轮播图
     * @param ids 轮播图ID列表
     * @return 是否成功
     */
    @Transactional
    public boolean batchDeleteCarousels(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "轮播图ID列表不能为空");
        }

        // 软删除不改变 usage_count（引用仍存在，只是标记删除）

        // 批量软删除
        LambdaUpdateWrapper<Carousel> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.in(Carousel::getId, ids)
                .set(Carousel::getDeletedAt, new Date());
        return this.update(updateWrapper);
    }

    /**
     * 更新轮播图状态
     * @param id 轮播图ID
     * @param status 新状态
     * @return 是否成功
     */
    @Transactional
    public boolean updateCarouselStatus(Long id, Integer status) {
        validateCarouselId(id);
        validateCarouselStatus(status);
        validateCarouselExistsAndNotDeleted(id);

        LambdaUpdateWrapper<Carousel> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Carousel::getId, id)
                .set(Carousel::getStatus, status);
        return this.update(updateWrapper);
    }

    /**
     * 更新轮播图排序
     * @param id 轮播图ID
     * @param sortOrder 新排序
     * @return 是否成功
     */
    @Transactional
    public boolean updateCarouselSort(Long id, Integer sortOrder) {
        validateCarouselId(id);
        validateCarouselExistsAndNotDeleted(id);

        LambdaUpdateWrapper<Carousel> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Carousel::getId, id)
                .set(Carousel::getSortOrder, sortOrder);
        return this.update(updateWrapper);
    }

    /**
     * 恢复已删除的轮播图
     * @param id 轮播图ID
     * @return 是否成功
     */
    @Transactional
    public boolean restoreCarousel(Long id) {
        validateCarouselId(id);

        // 使用自定义查询，绕过 @TableLogic 检查已删除记录
        Carousel carousel = carouselMapper.selectByIdWithDeleted(id);
        if (carousel == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "轮播图不存在");
        }
        if (carousel.getDeletedAt() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "轮播图未被删除，无需恢复");
        }

        // 使用原生SQL恢复，绕过 @TableLogic 的限制
        int result = carouselMapper.restoreCarouselById(id);
        return result > 0;
    }

    /**
     * 验证轮播图ID
     */
    private void validateCarouselId(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "轮播图ID不能为空");
        }
    }

    /**
     * 验证轮播图数据
     */
    private void validateCarouselData(Carousel carousel) {
        if (carousel.getTitle() == null || carousel.getTitle().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "轮播图标题不能为空");
        }
        if (carousel.getImageUrl() == null || carousel.getImageUrl().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "轮播图图片URL不能为空");
        }
    }

    /**
     * 验证轮播图状态
     */
    private void validateCarouselStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "状态无效");
        }
    }

    /**
     * 验证轮播图存在且未删除
     */
    private void validateCarouselExistsAndNotDeleted(Long id) {
        // 使用自定义查询，绕过 @TableLogic
        Carousel carousel = carouselMapper.selectByIdWithDeleted(id);
        if (carousel == null || carousel.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "轮播图不存在");
        }
    }

    /**
     * 执行软删除
     */
    private boolean performSoftDelete(Long id) {
        LambdaUpdateWrapper<Carousel> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Carousel::getId, id)
                .set(Carousel::getDeletedAt, new Date());
        return this.update(updateWrapper);
    }

    /**
     * 转换为响应数据
     * @param carousel 轮播图实体
     * @return 轮播图响应数据
     */
    private CarouselResp convertToResp(Carousel carousel) {
        Carousel source = Objects.requireNonNull(carousel, "carousel");
        CarouselResp resp = new CarouselResp();
        BeanUtils.copyProperties(source, resp);

        // 设置状态名称
        resp.setStatusName(getStatusName(source.getStatus()));

        // 设置删除状态
        if (source.getDeletedAt() != null) {
            resp.setDeleteStatus("已删除");
        } else {
            resp.setDeleteStatus("正常");
        }

        return resp;
    }

    /**
     * 获取状态名称
     * @param status 状态
     * @return 状态名称
     */
    private String getStatusName(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "禁用";
            case 1: return "启用";
            default: return "未知";
        }
    }

    /**
     * 彻底删除轮播图（物理删除）
     * @param id 轮播图ID
     * @return 是否成功
     */
    @Transactional
    public boolean permanentDeleteCarousel(Long id) {
        validateCarouselId(id);

        // 检查轮播图是否存在（无论是否已删除）- 使用自定义查询绕过 @TableLogic
        Carousel carousel = carouselMapper.selectByIdWithDeleted(id);
        if (carousel == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "轮播图不存在");
        }

        // 物理删除时减少 usage_count（文件由定时任务在 usage_count=0 时清理）
        // 只有未软删除的轮播图才减少 usage_count（软删除时没减，物理删除时就不能再减）
        if (carousel.getDeletedAt() == null) {
            decrementImageReference(carousel.getImageUrl());
        }

        // 物理删除记录
        int rows = carouselMapper.permanentDeleteById(id);
        return rows > 0;
    }

    /**
     * 批量彻底删除轮播图（物理删除）
     * @param ids 轮播图ID列表
     * @return 是否成功
     */
    @Transactional
    public boolean batchPermanentDeleteCarousels(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "轮播图ID列表不能为空");
        }

        // 物理删除时减少 usage_count（文件由定时任务在 usage_count=0 时清理）
        // 只有未软删除的轮播图才减少 usage_count
        for (Long id : ids) {
            Carousel carousel = carouselMapper.selectByIdWithDeleted(id);
            if (carousel != null && carousel.getDeletedAt() == null) {
                decrementImageReference(carousel.getImageUrl());
            }
        }

        int rows = carouselMapper.batchPermanentDelete(ids);
        return rows > 0;
    }

    // ==================== 图片引用计数管理 ====================

    /**
     * 增加图片引用计数
     * @param imageUrl 图片URL
     */
    private void incrementImageReference(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }
        try {
            Images img = imagesService.getImageByUrl(imageUrl);
            if (img != null && img.getDeletedAt() == null) {
                imagesMapper.incrementUsageCount(img.getId(), 1);
                log.debug("轮播图增加图片引用: {} -> {}", imageUrl, img.getUsageCount() + 1);
            }
        } catch (Exception e) {
            log.warn("增加图片引用计数失败: {}", imageUrl, e);
        }
    }

    /**
     * 减少图片引用计数
     * @param imageUrl 图片URL
     */
    private void decrementImageReference(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }
        try {
            Images img = imagesService.getImageByUrl(imageUrl);
            if (img != null) {
                // 无论图片是否已软删除，都减少 usage_count
                imagesMapper.incrementUsageCount(img.getId(), -1);
                log.debug("轮播图减少图片引用: {} -> {}", imageUrl, Math.max(0, img.getUsageCount() - 1));
            }
        } catch (Exception e) {
            log.warn("减少图片引用计数失败: {}", imageUrl, e);
        }
    }
}
