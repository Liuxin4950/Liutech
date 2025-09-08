package chat.liuxin.liutech.service;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import chat.liuxin.liutech.mapper.ResourceDownloadsMapper;
import chat.liuxin.liutech.mapper.ResourcesMapper;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.ResourceDownloads;
import chat.liuxin.liutech.model.Resources;
import chat.liuxin.liutech.model.Users;
import lombok.extern.slf4j.Slf4j;

/**
 * 资源下载服务
 * 
 * @author 刘鑫
 * @date 2025-01-15
 */
@Slf4j
@Service
public class ResourceDownloadService {
    
    @Autowired
    private ResourcesMapper resourcesMapper;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private ResourceDownloadsMapper resourceDownloadsMapper;
    
    @Value("${file.upload-dir}")
    private String uploadDir;
    
    /**
     * 购买资源（扣减积分）
     * 
     * @param userId 用户ID
     * @param resourceId 资源ID
     */
    @Transactional
    public void purchaseResource(Long userId, Long resourceId) {
        // 检查资源是否存在
        Resources resource = resourcesMapper.selectById(resourceId);
        if (resource == null) {
            throw new RuntimeException("资源不存在");
        }
        
        // 检查是否为免费资源
        if (resource.getDownloadType() == 0) {
            throw new RuntimeException("该资源为免费资源，无需购买");
        }
        
        // 检查是否已购买
        if (hasUserPurchased(userId, resourceId)) {
            throw new RuntimeException("您已购买过该资源");
        }
        
        // 检查用户积分是否足够
        Users user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        BigDecimal userPoints = user.getPoints() != null ? user.getPoints() : BigDecimal.ZERO;
        BigDecimal requiredPoints = resource.getPointsNeeded();
        
        if (userPoints.compareTo(requiredPoints) < 0) {
            throw new RuntimeException("积分不足，需要 " + requiredPoints + " 积分，当前仅有 " + userPoints + " 积分");
        }
        
        // 扣减用户积分
        UpdateWrapper<Users> userUpdateWrapper = new UpdateWrapper<>();
        userUpdateWrapper.eq("id", userId)
                        .setSql("points = points - " + requiredPoints);
        int updateResult = userMapper.update(null, userUpdateWrapper);
        if (updateResult == 0) {
            throw new RuntimeException("积分扣减失败");
        }
        
        // 创建购买记录
        ResourceDownloads download = new ResourceDownloads();
        download.setUserId(userId);
        download.setResourceId(resourceId);
        download.setPointsUsed(requiredPoints);
        download.setDownloadedAt(new Date());
        
        int insertResult = resourceDownloadsMapper.insert(download);
        if (insertResult == 0) {
            throw new RuntimeException("购买记录创建失败");
        }
        
        log.info("用户 {} 成功购买资源 {}，消费积分 {}", userId, resourceId, requiredPoints);
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
        if (fileUrl.startsWith("/uploads/")) {
            fileUrl = fileUrl.substring("/uploads/".length());
        }
        
        Path filePath = Paths.get(uploadDir, fileUrl);
        File file = filePath.toFile();
        
        if (!file.exists()) {
            throw new RuntimeException("文件不存在");
        }
        
        // 创建文件资源
        Resource fileResource = new FileSystemResource(file);
        
        // 设置响应头
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getName() + "\"");
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        
        log.info("用户 {} 下载资源 {} - {}", userId, resourceId, resource.getName());
        
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