package chat.liuxin.liutech.service;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import chat.liuxin.liutech.mapper.ResourceDownloadsMapper;
import chat.liuxin.liutech.mapper.ResourcesMapper;
import chat.liuxin.liutech.model.ResourceDownloads;
import chat.liuxin.liutech.model.Resources;
import lombok.extern.slf4j.Slf4j;

/**
 * 资源下载服务（安全加固版）
 *
 * 安全特性：
 * 1. 使用唯一索引防止重复购买
 * 2. 使用PointsService进行原子性积分扣减
 * 3. 先插入购买记录，后扣减积分（防止积分扣减成功但记录插入失败）
 *
 * @author 刘鑫
 * @date 2025-01-15（2025-01-18安全加固）
 */
@Slf4j
@Service
public class ResourceDownloadService {

    @Autowired
    private ResourcesMapper resourcesMapper;

    @Autowired
    private ResourceDownloadsMapper resourceDownloadsMapper;

    @Autowired
    private PointsService pointsService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    /**
     * 购买资源（扣减积分）- 安全加固版
     *
     * 安全措施：
     * 1. 数据库唯一索引防止重复购买
     * 2. 使用PointsService进行原子性积分扣减
     * 3. 先插入购买记录，再扣减积分
     * 4. 完整的异常处理和回滚
     *
     * @param userId 用户ID
     * @param resourceId 资源ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void purchaseResource(Long userId, Long resourceId) {
        // 1. 检查资源是否存在
        Resources resource = resourcesMapper.selectById(resourceId);
        if (resource == null) {
            throw new RuntimeException("资源不存在");
        }

        // 2. 检查是否为免费资源
        if (resource.getDownloadType() == 0) {
            throw new RuntimeException("该资源为免费资源，无需购买");
        }

        // 3. 检查是否为资源上传者（上传者无需购买）
        if (resource.getUploaderId().equals(userId)) {
            throw new RuntimeException("您是该资源的上传者，无需购买");
        }

        BigDecimal requiredPoints = resource.getPointsNeeded();

        // 4. 先插入购买记录（利用唯一索引防止重复购买）
        // 注意：先插入记录，再扣减积分，避免积分扣减成功但记录插入失败
        ResourceDownloads download = new ResourceDownloads();
        download.setUserId(userId);
        download.setResourceId(resourceId);
        download.setPointsUsed(requiredPoints);
        download.setDownloadedAt(new java.util.Date());

        try {
            int insertResult = resourceDownloadsMapper.insert(download);
            if (insertResult == 0) {
                throw new RuntimeException("购买记录创建失败");
            }
        } catch (DuplicateKeyException e) {
            // 唯一索引冲突，说明已经购买过
            log.warn("用户{}尝试重复购买资源{}", userId, resourceId);
            throw new RuntimeException("您已购买过该资源，请勿重复购买");
        }

        // 5. 扣减积分（原子操作，包含并发安全）
        try {
            pointsService.deductPoints(
                userId,
                requiredPoints,
                PointsService.SOURCE_RESOURCE_DOWNLOAD,
                resourceId,
                "购买资源：" + resource.getName()
            );
        } catch (Exception e) {
            // 积分扣减失败，事务会自动回滚，删除已插入的购买记录
            log.error("用户{}购买资源{}积分扣减失败，事务回滚", userId, resourceId, e);
            throw new RuntimeException("积分扣减失败：" + e.getMessage());
        }

        log.info("用户{}成功购买资源{}，消费{}积分", userId, resourceId, requiredPoints);
    }

    /**
     * 下载资源文件
     *
     * @param userId 用户ID
     * @param resourceId 资源ID
     * @return 文件响应
     */
    public ResponseEntity<Resource> downloadResource(Long userId, Long resourceId) {
        // 检查资源是否存在
        Resources resource = resourcesMapper.selectById(resourceId);
        if (resource == null) {
            throw new RuntimeException("资源不存在");
        }

        // 检查下载权限
        if (resource.getDownloadType() == 1 && !hasUserPurchased(userId, resourceId)) {
            throw new RuntimeException("请先购买该资源");
        }

        // 构建文件路径
        String fileUrl = resource.getFileUrl();

        // 处理不同格式的文件URL
        // 情况1: 完整URL (http://localhost:8080/uploads/resources/...)
        // 情况2: 相对路径 (/uploads/resources/...)
        if (fileUrl.contains("/uploads/")) {
            // 提取 /uploads/ 之后的部分
            int uploadsIndex = fileUrl.indexOf("/uploads/");
            fileUrl = fileUrl.substring(uploadsIndex + "/uploads/".length());
        }

        Path filePath = Paths.get(uploadDir, fileUrl);
        File file = filePath.toFile();

        if (!file.exists()) {
            log.error("文件不存在: {}", filePath);
            throw new RuntimeException("文件不存在");
        }

        // 创建文件资源
        Resource fileResource = new FileSystemResource(file);

        // 设置响应头
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getName() + "\"");
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        log.info("用户{}下载资源{} - {}", userId, resourceId, resource.getName());

        return ResponseEntity.ok()
                .headers(headers)
                .body(fileResource);
    }

    /**
     * 检查用户是否已购买资源
     *
     * @param userId 用户ID
     * @param resourceId 资源ID
     * @return 是否已购买
     */
    public boolean hasUserPurchased(Long userId, Long resourceId) {
        // 检查资源是否为免费
        Resources resource = resourcesMapper.selectById(resourceId);
        if (resource != null && resource.getDownloadType() == 0) {
            return true; // 免费资源视为已购买
        }

        // 检查是否为资源上传者
        if (resource != null && resource.getUploaderId().equals(userId)) {
            return true; // 上传者可以免费下载
        }

        // 查询购买记录
        int count = resourceDownloadsMapper.countUserPurchase(userId, resourceId);
        return count > 0;
    }
}
