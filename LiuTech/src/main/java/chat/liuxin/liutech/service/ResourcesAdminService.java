package chat.liuxin.liutech.service;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.mapper.ResourceDownloadsMapper;
import chat.liuxin.liutech.mapper.ResourcesMapper;
import chat.liuxin.liutech.model.ResourceDownloads;
import chat.liuxin.liutech.model.Resources;
import chat.liuxin.liutech.resp.DownloadLogResp;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.ResourceResp;
import lombok.extern.slf4j.Slf4j;

/**
 * 资源管理服务（管理端）
 *
 * @author 刘鑫
 */
@Slf4j
@Service
public class ResourcesAdminService extends ServiceImpl<ResourcesMapper, Resources> {

    @Autowired
    private ResourcesMapper resourcesMapper;

    @Autowired
    private ResourceDownloadsMapper resourceDownloadsMapper;

    /**
     * 获取资源列表（管理端）
     * 支持分页查询和按名称、资源类型、下载类型筛选
     *
     * @param page 页码，从1开始
     * @param size 每页大小
     * @param name 资源名称（可选，模糊搜索）
     * @param resourceType 资源类型（可选）
     * @param downloadType 下载类型（可选）
     * @param includeDeleted 是否包含已删除资源
     * @return 分页结果
     */
    public PageResp<ResourceResp> getResourceListForAdmin(Integer page, Integer size, String name,
                                                           String resourceType, Integer downloadType,
                                                           Boolean includeDeleted) {
        Integer offset = (page - 1) * size;

        List<ResourceResp> resourceList = resourcesMapper.selectResourcesForAdmin(
                offset, size, name, resourceType, downloadType, includeDeleted);

        Integer total = resourcesMapper.countResourcesForAdmin(name, resourceType, downloadType, includeDeleted);

        PageResp<ResourceResp> pageResp = new PageResp<>();
        pageResp.setRecords(resourceList);
        pageResp.setTotal(total.longValue());
        pageResp.setCurrent(page.longValue());
        pageResp.setSize(size.longValue());
        pageResp.setPages((long) Math.ceil((double) total / size));
        pageResp.setHasNext(page.longValue() < pageResp.getPages());
        pageResp.setHasPrevious(page.longValue() > 1);

        return pageResp;
    }

    /**
     * 根据ID获取资源详情
     *
     * @param id 资源ID
     * @return 资源信息
     */
    public ResourceResp getResourceById(Long id) {
        Resources resource = super.getById(id);
        if (resource == null) {
            return null;
        }

        ResourceResp resp = new ResourceResp();
        resp.setId(resource.getId());
        resp.setName(resource.getName());
        resp.setDescription(resource.getDescription());
        resp.setFileUrl(resource.getFileUrl());
        resp.setExternalLink(resource.getExternalLink());
        resp.setResourceType(resource.getResourceType());
        resp.setPurchasedNote(resource.getPurchasedNote());
        resp.setUploaderId(resource.getUploaderId());
        resp.setDownloadType(resource.getDownloadType());
        resp.setPointsNeeded(resource.getPointsNeeded());
        resp.setCreatedAt(resource.getCreatedAt());
        resp.setUpdatedAt(resource.getUpdatedAt());
        resp.setDeletedAt(resource.getDeletedAt());

        return resp;
    }

    /**
     * 创建资源
     *
     * @param resourceResp 资源信息
     * @return 是否保存成功
     */
    public boolean createResource(ResourceResp resourceResp) {
        Resources resource = new Resources();
        resource.setName(resourceResp.getName());
        resource.setDescription(resourceResp.getDescription());
        resource.setFileUrl(resourceResp.getFileUrl());
        resource.setExternalLink(resourceResp.getExternalLink());
        resource.setResourceType(resourceResp.getResourceType());
        resource.setPurchasedNote(resourceResp.getPurchasedNote());
        resource.setUploaderId(resourceResp.getUploaderId());
        resource.setDownloadType(resourceResp.getDownloadType());
        resource.setPointsNeeded(resourceResp.getPointsNeeded());
        return super.save(resource);
    }

    /**
     * 更新资源
     *
     * @param resourceResp 资源信息
     * @return 是否更新成功
     */
    public boolean updateResource(ResourceResp resourceResp) {
        Resources resource = new Resources();
        resource.setId(resourceResp.getId());
        resource.setName(resourceResp.getName());
        resource.setDescription(resourceResp.getDescription());
        resource.setFileUrl(resourceResp.getFileUrl());
        resource.setExternalLink(resourceResp.getExternalLink());
        resource.setResourceType(resourceResp.getResourceType());
        resource.setPurchasedNote(resourceResp.getPurchasedNote());
        resource.setUploaderId(resourceResp.getUploaderId());
        resource.setDownloadType(resourceResp.getDownloadType());
        resource.setPointsNeeded(resourceResp.getPointsNeeded());
        return super.updateById(resource);
    }

    /**
     * 批量软删除资源
     *
     * @param ids 资源ID列表
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean removeByIds(List<Long> ids) {
        try {
            if (ids == null || ids.isEmpty()) {
                return false;
            }

            LambdaUpdateWrapper<Resources> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.in(Resources::getId, ids)
                    .set(Resources::getDeletedAt, new Date());

            int result = resourcesMapper.update(null, updateWrapper);
            log.info("软删除资源数量: {}", result);
            return result > 0;
        } catch (Exception e) {
            log.error("批量删除资源失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 恢复已删除的资源
     *
     * @param id 资源ID
     * @return 是否恢复成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean restoreResource(Long id) {
        try {
            if (id == null) {
                return false;
            }

            int result = resourcesMapper.restoreResourceById(id);
            log.info("恢复资源ID: {}, 结果: {}", id, result > 0 ? "成功" : "失败");
            return result > 0;
        } catch (Exception e) {
            log.error("恢复资源失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 彻底删除资源（物理删除）
     * 同时删除关联的下载记录
     *
     * @param id 资源ID
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean permanentDeleteResource(Long id) {
        log.info("彻底删除资源 - 资源ID: {}", id);

        try {
            if (id == null) {
                return false;
            }

            // 先删除关联的下载记录
            LambdaUpdateWrapper<ResourceDownloads> downloadWrapper = new LambdaUpdateWrapper<>();
            downloadWrapper.eq(ResourceDownloads::getResourceId, id);
            int downloadResult = resourceDownloadsMapper.delete(downloadWrapper);
            log.info("彻底删除资源时，删除关联下载记录数量: {}", downloadResult);

            // 物理删除资源
            int result = resourcesMapper.permanentDeleteByIds(Collections.singletonList(id));
            boolean success = result > 0;
            log.info("彻底删除资源{} - 资源ID: {}", success ? "成功" : "失败", id);
            return success;
        } catch (Exception e) {
            log.error("彻底删除资源失败 - 资源ID: {}, 错误: {}", id, e.getMessage(), e);
            throw new RuntimeException("彻底删除资源失败: " + e.getMessage());
        }
    }

    /**
     * 批量彻底删除资源（物理删除）
     * 同时删除关联的下载记录
     *
     * @param ids 资源ID列表
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean batchPermanentDeleteResources(List<Long> ids) {
        log.info("批量彻底删除资源 - 资源数量: {}", ids.size());

        try {
            if (ids == null || ids.isEmpty()) {
                return false;
            }

            // 先删除关联的下载记录
            LambdaUpdateWrapper<ResourceDownloads> downloadWrapper = new LambdaUpdateWrapper<>();
            downloadWrapper.in(ResourceDownloads::getResourceId, ids);
            int downloadResult = resourceDownloadsMapper.delete(downloadWrapper);
            log.info("批量彻底删除资源时，删除关联下载记录数量: {}", downloadResult);

            // 物理删除资源
            int result = resourcesMapper.permanentDeleteByIds(ids);
            boolean success = result > 0;
            log.info("批量彻底删除资源{} - 影响资源数: {}", success ? "成功" : "失败", ids.size());
            return success;
        } catch (Exception e) {
            log.error("批量彻底删除资源失败 - 错误: {}", e.getMessage(), e);
            throw new RuntimeException("批量彻底删除资源失败: " + e.getMessage());
        }
    }

    /**
     * 获取下载记录列表（管理端）
     *
     * @param page 页码
     * @param size 每页大小
     * @param userId 用户ID（可选）
     * @param resourceId 资源ID（可选）
     * @return 分页结果
     */
    public PageResp<DownloadLogResp> getDownloadLogsForAdmin(Integer page, Integer size,
                                                              Long userId, Long resourceId) {
        Integer offset = (page - 1) * size;

        List<DownloadLogResp> logList = resourceDownloadsMapper.selectDownloadLogsForAdmin(
                offset, size, userId, resourceId);

        Integer total = resourceDownloadsMapper.countDownloadLogsForAdmin(userId, resourceId);

        PageResp<DownloadLogResp> pageResp = new PageResp<>();
        pageResp.setRecords(logList);
        pageResp.setTotal(total.longValue());
        pageResp.setCurrent(page.longValue());
        pageResp.setSize(size.longValue());
        pageResp.setPages((long) Math.ceil((double) total / size));
        pageResp.setHasNext(page.longValue() < pageResp.getPages());
        pageResp.setHasPrevious(page.longValue() > 1);

        return pageResp;
    }
}
