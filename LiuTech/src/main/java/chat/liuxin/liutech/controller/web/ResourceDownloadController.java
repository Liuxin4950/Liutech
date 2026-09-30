package chat.liuxin.liutech.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import java.nio.charset.StandardCharsets;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.PurchasedResourceResp;
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

/**
 * 资源下载控制器
 * 
 * @author 刘鑫
 * @date 2025-01-15
 */
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

        // 服务层已用明确的业务消息表达失败原因，交给 GlobalExceptionHandler 统一转成 Result，
        // 避免在此把原始异常消息直接回给客户端
        resourceDownloadService.purchaseResource(userId, resourceId);
        return Result.success("购买成功");
    }
    
    /**
     * 下载资源文件
     * 
     * @param resourceId 资源ID
     * @return 文件流
     */
    @GetMapping("/download/{resourceId}")
    public ResponseEntity<Resource> downloadResource(@PathVariable Long resourceId) {
        var file = resourceDownloadService.downloadResource(userUtils.getCurrentUserId(), resourceId);
        var response = ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString());
        if (file.contentLength() >= 0) response.contentLength(file.contentLength());
        return response.body(new InputStreamResource(file.stream()));
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
        return Result.success(resourceDownloadService.getDownloadUrl(userUtils.getCurrentUserId(), resourceId));
    }

    @GetMapping("/purchases")
    public Result<PageResp<PurchasedResourceResp>> getPurchases(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(resourceDownloadService.getUserPurchases(userUtils.getCurrentUserId(), page, size));
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
