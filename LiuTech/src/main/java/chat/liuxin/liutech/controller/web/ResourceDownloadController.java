package chat.liuxin.liutech.controller.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.service.ResourceDownloadService;
import chat.liuxin.liutech.utils.UserUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 资源下载控制器
 * 
 * @author 刘鑫
 * @date 2025-01-15
 */
@Slf4j
@RestController
@RequestMapping("/api/resource")
public class ResourceDownloadController {
    
    @Autowired
    private ResourceDownloadService resourceDownloadService;
    
    @Autowired
    private UserUtils userUtils;
    
    /**
     * 购买资源（扣减积分）
     * 
     * @param resourceId 资源ID
     * @return 购买结果
     */
    @PostMapping("/purchase/{resourceId}")
    public Result<String> purchaseResource(@PathVariable Long resourceId) {
        Long userId = userUtils.getCurrentUserId();
        log.info("用户 {} 尝试购买资源 {}", userId, resourceId);
        
        try {
            resourceDownloadService.purchaseResource(userId, resourceId);
            return Result.success("购买成功");
        } catch (Exception e) {
            log.error("购买资源失败: {}", e.getMessage());
            return Result.fail(500, e.getMessage());
        }
    }
    
    /**
     * 下载资源文件
     * 
     * @param resourceId 资源ID
     * @return 文件流
     */
    @GetMapping("/download/{resourceId}")
    public ResponseEntity<Resource> downloadResource(@PathVariable Long resourceId) {
        Long userId = userUtils.getCurrentUserId();
        log.info("用户 {} 尝试下载资源 {}", userId, resourceId);
        
        try {
            return resourceDownloadService.downloadResource(userId, resourceId);
        } catch (Exception e) {
            log.error("下载资源失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 检查用户是否已购买资源
     * 
     * @param resourceId 资源ID
     * @return 是否已购买
     */
    @GetMapping("/check/{resourceId}")
    public Result<Boolean> checkPurchaseStatus(@PathVariable Long resourceId) {
        Long userId = userUtils.getCurrentUserId();
        boolean purchased = resourceDownloadService.hasUserPurchased(userId, resourceId);
        return Result.success(purchased);
    }
}