package chat.liuxin.liutech.controller.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.FileUploadResp;
import chat.liuxin.liutech.service.FileUploadService;
import chat.liuxin.liutech.utils.UserUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件上传控制器
 *
 * @author 刘鑫
 * @date 2025-08-07
 */
@Slf4j
@RestController
@RequestMapping("/upload")
public class FileUploadController {

    @Autowired
    private FileUploadService fileUploadService;

    @Autowired
    private UserUtils userUtils;

    /**
     * 上传图片（用于TinyMCE编辑器）
     *
     * @param file 图片文件
     * @return 上传结果
     */
    @PostMapping("/image")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "upload", targetType = "image", description = "上传图片")
    public Result<FileUploadResp> uploadImage(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        Long userId = userUtils.getCurrentUserId();

        FileUploadResp result = fileUploadService.uploadImage(file, userId);
        return Result.success(result);
    }

    /**
     * 上传文档文件
     *
     * @param file 文档文件
     * @param description 文件描述
     * @return 上传结果
     */
    @PostMapping("/document")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "upload", targetType = "document", description = "上传文档")
    public Result<FileUploadResp> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            HttpServletRequest request) {

        Long userId = userUtils.getCurrentUserId();

        FileUploadResp result = fileUploadService.uploadDocument(file, userId, description);
        return Result.success(result);
    }

    /**
     * 上传资源文件
     *
     * @param file 资源文件
     * @param description 文件描述
     * @param draftKey 草稿关联键（可选）
     * @param type 附件类型（可选）
     * @param downloadType 下载类型（0免费，1积分，默认0）
     * @param pointsNeeded 下载所需积分（默认0）
     * @return 上传结果
     */
    @PostMapping("/resource")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "upload", targetType = "resource", description = "上传资源")
    public Result<FileUploadResp> uploadResource(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "draftKey", required = false) String draftKey,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "downloadType", required = false, defaultValue = "0") Integer downloadType,
            @RequestParam(value = "pointsNeeded", required = false, defaultValue = "0") Integer pointsNeeded,
            HttpServletRequest request) {

        Long userId = userUtils.getCurrentUserId();

        FileUploadResp result = fileUploadService.uploadResource(file, userId, description, draftKey, type, downloadType, pointsNeeded);
        return Result.success(result);
    }

    /**
     * 查询草稿附件列表
     *
     * @param draftKey 草稿关联键
     * @return 附件列表
     */
    @GetMapping("/attachments/draft/{draftKey}")
    public Result<?> getDraftAttachments(
            @PathVariable("draftKey") String draftKey,
            HttpServletRequest request) {

        Long userId = userUtils.getCurrentUserId();

        var result = fileUploadService.getDraftAttachments(draftKey, userId);
        return Result.success(result);
    }

    /**
     * 查询文章附件列表
     *
     * @param postId 文章ID
     * @return 附件列表
     */
    @GetMapping("/attachments/post/{postId}")
    public Result<?> getPostAttachments(
            @PathVariable("postId") Long postId,
            HttpServletRequest request) {

        Long userId = userUtils.getCurrentUserId();

        var result = fileUploadService.getPostAttachments(postId, userId);
        return Result.success(result);
    }

    /**
     * 删除附件
     *
     * @param resourceId 资源ID
     * @return 删除结果
     */
    @DeleteMapping("/attachments/{resourceId}")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "delete", targetType = "attachment", description = "删除附件")
    public Result<?> deleteAttachment(
            @PathVariable("resourceId") Long resourceId,
            HttpServletRequest request) {

        Long userId = userUtils.getCurrentUserId();

        fileUploadService.deleteAttachment(resourceId, userId);
        return Result.success("附件删除成功");
    }

    /**
     * TinyMCE编辑器专用图片上传接口
     * 返回格式符合TinyMCE要求
     *
     * @param file 图片文件
     * @return TinyMCE格式的响应
     */
    @PostMapping("/tinymce/image")
    @PreAuthorize("hasRole('ADMIN')")
    public Object uploadImageForTinyMCE(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        Long userId = userUtils.getCurrentUserId();

        try {
            FileUploadResp result = fileUploadService.uploadImage(file, userId);

            // 返回TinyMCE期望的格式
            return new TinyMCEResponse(result.getFileUrl());

        } catch (Exception e) {
            log.error("TinyMCE图片上传失败", e);
            // TinyMCE错误格式
            return new TinyMCEErrorResponse(e.getMessage());
        }
    }

    /**
     * TinyMCE成功响应格式
     */
    public static class TinyMCEResponse {
        private String location;

        public TinyMCEResponse(String location) {
            this.location = location;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }
    }

    /**
     * TinyMCE错误响应格式
     */
    public static class TinyMCEErrorResponse {
        private String error;

        public TinyMCEErrorResponse(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }

    /**
     * 更新附件元信息（下载类型、所需积分）
     */
    @PutMapping("/attachments/{resourceId}/meta")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "update", targetType = "attachment", description = "更新附件信息")
    public Result<?> updateAttachmentMeta(
            @PathVariable("resourceId") Long resourceId,
            @RequestParam(value = "downloadType", required = false, defaultValue = "0") Integer downloadType,
            @RequestParam(value = "pointsNeeded", required = false, defaultValue = "0") Integer pointsNeeded,
            HttpServletRequest request) {
        Long userId = userUtils.getCurrentUserId();
        fileUploadService.updateResourceMeta(resourceId, userId, downloadType, pointsNeeded);
        return Result.success("附件设置已更新");
    }

    /**
     * 创建外部链接资源（不包含文件上传）
     *
     * @param name 资源名称
     * @param description 资源描述
     * @param externalLink 外部链接
     * @param purchasedNote 购买后显示的说明
     * @param draftKey 草稿键（可选）
     * @param type 附件类型（可选）
     * @param downloadType 下载类型（0免费，1积分，默认0）
     * @param pointsNeeded 所需积分（默认0）
     * @return 上传结果
     */
    @PostMapping("/resource/external")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "create", targetType = "resource", description = "创建外部链接资源")
    public Result<FileUploadResp> createExternalLinkResource(
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("externalLink") String externalLink,
            @RequestParam(value = "purchasedNote", required = false) String purchasedNote,
            @RequestParam(value = "draftKey", required = false) String draftKey,
            @RequestParam(value = "type", required = false, defaultValue = "resource") String type,
            @RequestParam(value = "downloadType", required = false, defaultValue = "0") Integer downloadType,
            @RequestParam(value = "pointsNeeded", required = false, defaultValue = "0") Integer pointsNeeded,
            HttpServletRequest request) {

        Long userId = userUtils.getCurrentUserId();

        FileUploadResp result = fileUploadService.createExternalLinkResource(
            name, description, externalLink, purchasedNote, userId, draftKey, type, downloadType, pointsNeeded
        );
        return Result.success(result);
    }

    /**
     * 更新购买后说明
     *
     * @param resourceId 资源ID
     * @param purchasedNote 购买后说明
     * @return 更新结果
     */
    @PutMapping("/attachments/{resourceId}/purchased-note")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "update", targetType = "resource", description = "更新购买说明")
    public Result<?> updatePurchasedNote(
            @PathVariable("resourceId") Long resourceId,
            @RequestParam("purchasedNote") String purchasedNote,
            HttpServletRequest request) {
        Long userId = userUtils.getCurrentUserId();
        fileUploadService.updatePurchasedNote(resourceId, userId, purchasedNote);
        return Result.success("购买说明已更新");
    }
}
