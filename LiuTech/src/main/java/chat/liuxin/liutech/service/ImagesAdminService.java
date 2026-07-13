package chat.liuxin.liutech.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.ImagesMapper;
import chat.liuxin.liutech.model.Images;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.utils.FileUtil;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

/**
 * 图片管理服务（Admin）
 * 提供图片的分页查询、软删除、恢复、物理删除、孤立图片清理等功能
 *
 * @author 刘鑫
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImagesAdminService extends ServiceImpl<ImagesMapper, Images> {

    private final ImagesMapper imagesMapper;

    private final FileUtil fileUtil;

    /**
     * 分页查询图片列表（管理端）
     *
     * @param page          页码，从1开始
     * @param size          每页大小
     * @param fileName      文件名（可选，模糊搜索）
     * @param mimeType      MIME类型（可选，模糊搜索）
     * @param status        状态（可选）
     * @param includeDeleted 是否包含已删除
     * @return 分页结果
     */
    public PageResp<Images> getImageListForAdmin(Integer page, Integer size,
                                                  String fileName, String mimeType,
                                                  Integer status, Boolean includeDeleted) {
        Integer offset = (page - 1) * size;

        List<Images> imageList = imagesMapper.selectImagesForAdmin(offset, size, fileName, mimeType, status, includeDeleted);
        Integer total = imagesMapper.countImagesForAdmin(fileName, mimeType, status, includeDeleted);

        PageResp<Images> pageResp = new PageResp<>();
        pageResp.setRecords(imageList);
        pageResp.setTotal(total.longValue());
        pageResp.setCurrent(page.longValue());
        pageResp.setSize(size.longValue());
        pageResp.setPages((long) Math.ceil((double) total / size));
        pageResp.setHasNext(page.longValue() < pageResp.getPages());
        pageResp.setHasPrevious(page.longValue() > 1);

        return pageResp;
    }

    /**
     * 根据ID获取图片详情
     *
     * @param id 图片ID
     * @return 图片信息
     */
    public Images getImageById(Long id) {
        return imagesMapper.selectById(id);
    }

    /**
     * 软删除图片
     * 如果图片被引用（usage_count > 0），返回警告信息
     *
     * @param id 图片ID
     * @return 操作结果，包含警告信息（如果有）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> softDeleteImage(Long id) {
        Images image = imagesMapper.selectById(id);
        if (image == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);

        if (image.getUsageCount() != null && image.getUsageCount() > 0) {
            result.put("warning", "该图片正在被 " + image.getUsageCount() + " 篇文章引用，删除后文章中的图片将无法显示");
        }

        boolean success = removeById(id);
        if (!success) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "软删除图片失败");
        }

        log.debug("软删除图片 - ID: {}, 文件名: {}", id, image.getFileName());
        return result;
    }

    /**
     * 批量软删除图片
     *
     * @param ids 图片ID列表
     * @return 操作结果，包含警告信息（如果有）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchSoftDeleteImages(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片ID列表不能为空");
        }

        Map<String, Object> result = new HashMap<>();
        int warningCount = 0;

        for (Long id : ids) {
            Images image = imagesMapper.selectById(id);
            if (image != null && image.getUsageCount() != null && image.getUsageCount() > 0) {
                warningCount++;
            }
        }

        boolean success = removeByIds(ids);
        if (!success) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "批量软删除图片失败");
        }

        result.put("success", true);
        if (warningCount > 0) {
            result.put("warning", "有 " + warningCount + " 张图片正在被文章引用，删除后文章中的图片将无法显示");
        }

        log.debug("批量软删除图片 - 数量: {}", ids.size());
        return result;
    }

    /**
     * 恢复已删除的图片
     *
     * @param id 图片ID
     * @return 是否恢复成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean restoreImage(Long id) {
        if (id == null) {
            return false;
        }

        int result = imagesMapper.restoreImageById(id);
        log.debug("恢复图片ID: {}, 结果: {}", id, result > 0 ? "成功" : "失败");
        return result > 0;
    }

    /**
     * 物理删除图片（同时删除文件系统中的文件）
     *
     * @param id 图片ID
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean permanentDeleteImage(Long id) {
        Images image = imagesMapper.selectById(id);
        if (image == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在");
        }

        // 删除文件系统中的文件
        deleteImageFile(image);

        // 物理删除数据库记录
        int result = imagesMapper.permanentDeleteById(id);
        boolean success = result > 0;

        log.debug("物理删除图片{} - ID: {}, 文件名: {}", success ? "成功" : "失败", id, image.getFileName());
        return success;
    }

    /**
     * 批量物理删除图片（同时删除文件系统中的文件）
     *
     * @param ids 图片ID列表
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean batchPermanentDeleteImages(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }

        // 先删除文件系统中的文件
        for (Long id : ids) {
            Images image = imagesMapper.selectById(id);
            if (image != null) {
                deleteImageFile(image);
            }
        }

        // 批量物理删除数据库记录
        int result = imagesMapper.batchPermanentDelete(ids);
        boolean success = result > 0;

        log.debug("批量物理删除图片{} - 影响数量: {}", success ? "成功" : "失败", ids.size());
        return success;
    }

    /**
     * 查询孤立图片（usage_count = 0 且未删除）
     *
     * @return 孤立图片列表
     */
    public List<Images> getOrphanImages() {
        return imagesMapper.selectOrphanImages();
    }

    /**
     * 清理孤立图片（物理删除 usage_count = 0 的未删除图片）
     *
     * @return 清理的图片数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int cleanupOrphanImages() {
        List<Images> orphans = imagesMapper.selectOrphanImages();
        if (orphans.isEmpty()) {
            return 0;
        }

        for (Images image : orphans) {
            deleteImageFile(image);
        }

        List<Long> ids = orphans.stream().map(Images::getId).toList();
        int result = imagesMapper.batchPermanentDelete(ids);

        log.debug("清理孤立图片 - 数量: {}", result);
        return result;
    }

    /**
     * 删除文件系统中的图片文件
     *
     * @param image 图片记录
     */
    private void deleteImageFile(Images image) {
        try {
            boolean deleted = fileUtil.deleteFileByUrl(image.getFileUrl());
            if (deleted) {
                log.debug("删除图片文件成功: {}", image.getFileUrl());
            } else {
                log.warn("删除图片文件失败或文件不存在: {}", image.getFileUrl());
            }
        } catch (Exception e) {
            log.error("删除图片文件异常: {}", image.getFileUrl(), e);
        }
    }
}
