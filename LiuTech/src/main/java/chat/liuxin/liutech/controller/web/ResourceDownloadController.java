package chat.liuxin.liutech.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.DownloadUrlResp;
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
@RequestMapping("/resource")
@RequiredArgsConstructor
public class ResourceDownloadController {
    
    private final ResourceDownloadService resourceDownloadService;
    
    private final UserUtils userUtils;
    
    /**
     * 购买资源（扣减积分）
     * 
     * @param resourceId 资源ID
     * @return 购买结果
     */
    @PostMapping("/purchase/{resourceId}")
    @OperationLog(action = "purchase", targetType = "resource", description = "购买资源")
    public Result<String> purchaseResource(@PathVariable Long resourceId) {
        Long userId = userUtils.getCurrentUserId();
        
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
        
        try {
            return resourceDownloadService.downloadResource(userId, resourceId);
        } catch (Exception e) {
            log.error("下载资源失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 获取资源直链下载地址（COS 等对象存储）
     * <p>
     * 校验购买/权限后返回短期签名 URL，浏览器直接向对象存储下载；
     * 本地磁盘存储不支持直链时返回 url=null，前端回退到 {@link #downloadResource}。
     *
     * @param resourceId 资源ID
     * @return 直链下载信息
     */
    @GetMapping("/download-url/{resourceId}")
    public Result<DownloadUrlResp> getDownloadUrl(@PathVariable Long resourceId) {
        Long userId = userUtils.getCurrentUserId();

        try {
            return Result.success(resourceDownloadService.getDownloadUrl(userId, resourceId));
        } catch (Exception e) {
            log.error("获取资源直链失败: {}", e.getMessage());
            return Result.fail(400, e.getMessage());
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
