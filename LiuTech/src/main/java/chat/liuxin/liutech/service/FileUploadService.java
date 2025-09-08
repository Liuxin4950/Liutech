package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.config.FileUploadConfig;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.mapper.ResourcesMapper;
import chat.liuxin.liutech.mapper.PostAttachmentsMapper;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.model.Resources;
import chat.liuxin.liutech.model.PostAttachments;
import chat.liuxin.liutech.resl.FileUploadResl;
import chat.liuxin.liutech.utils.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * 文件上传服务类
 * 
 * @author 刘鑫
 * @date 2025-08-07
 */
@Slf4j
@Service
public class FileUploadService {
    
    @Autowired
    private FileUtil fileUtil;
    
    @Autowired
    private FileUploadConfig fileUploadConfig;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private ResourcesMapper resourcesMapper;
    
    @Autowired
    private PostAttachmentsMapper postAttachmentsMapper;
    
    /**
     * 上传图片文件（用于TinyMCE编辑器）
     * 
     * @param file 图片文件
     * @param userId 用户ID
     * @return 上传结果
     */
    public FileUploadResl uploadImage(MultipartFile file, Long userId) {
        log.info("开始上传图片 - 用户ID: {}, 文件名: {}, 大小: {} bytes", 
                userId, file.getOriginalFilename(), file.getSize());
        
        // 验证用户是否存在
        validateUser(userId);
        
        // 验证文件
        validateImageFile(file);
        
        try {
            // 保存文件
            String relativePath = fileUtil.saveFile(file, fileUploadConfig.getImagePath());
            
            // 生成访问URL
            String fileUrl = fileUtil.generateFileUrl(relativePath);
            
            // 构建响应
            FileUploadResl result = new FileUploadResl();
            result.setFileName(file.getOriginalFilename());
            result.setFilePath(relativePath);
            result.setFileUrl(fileUrl);
            result.setFileSize(file.getSize());
            result.setFileType("image");
            result.setExtension(fileUtil.getFileExtension(file.getOriginalFilename()));
            result.setUploadTime(System.currentTimeMillis());
            
            log.info("图片上传成功 - 用户ID: {}, 文件路径: {}, 访问URL: {}", 
                    userId, relativePath, fileUrl);
            
            return result;
            
        } catch (IOException e) {
            log.error("图片上传失败 - 用户ID: {}, 文件名: {}", userId, file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件保存失败: " + e.getMessage());
        }
    }
    
    /**
     * 上传文档文件
     * 
     * @param file 文档文件
     * @param userId 用户ID
     * @param description 文件描述
     * @return 上传结果
     */
    public FileUploadResl uploadDocument(MultipartFile file, Long userId, String description) {
        log.info("开始上传文档 - 用户ID: {}, 文件名: {}, 大小: {} bytes, 描述: {}", 
                userId, file.getOriginalFilename(), file.getSize(), description);
        
        // 验证用户是否存在
        validateUser(userId);
        
        // 验证文件
        validateDocumentFile(file);
        
        try {
            // 保存文件
            String relativePath = fileUtil.saveFile(file, fileUploadConfig.getDocumentPath());
            
            // 生成访问URL
            String fileUrl = fileUtil.generateFileUrl(relativePath);
            
            // 构建响应
            FileUploadResl result = new FileUploadResl();
            result.setFileName(file.getOriginalFilename());
            result.setFilePath(relativePath);
            result.setFileUrl(fileUrl);
            result.setFileSize(file.getSize());
            result.setFileType("document");
            result.setExtension(fileUtil.getFileExtension(file.getOriginalFilename()));
            result.setUploadTime(System.currentTimeMillis());
            
            log.info("文档上传成功 - 用户ID: {}, 文件路径: {}, 访问URL: {}", 
                    userId, relativePath, fileUrl);
            
            return result;
            
        } catch (IOException e) {
            log.error("文档上传失败 - 用户ID: {}, 文件名: {}", userId, file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件保存失败: " + e.getMessage());
        }
    }
    
    /**
     * 上传资源文件
     * 
     * @param file 资源文件
     * @param userId 用户ID
     * @param description 文件描述
     * @return 上传结果
     */
    public FileUploadResl uploadResource(MultipartFile file, Long userId, String description) {
        return uploadResource(file, userId, description, null, null, 0, 0);
    }
    
    /**
     * 上传资源文件（扩展版本，支持草稿附件）
     * 
     * @param file 资源文件
     * @param userId 用户ID
     * @param description 文件描述
     * @param draftKey 草稿关联键（可选）
     * @param type 附件类型（可选）
     * @param downloadType 下载类型
     * @param pointsNeeded 所需积分
     * @return 上传结果
     */
    @Transactional
    public FileUploadResl uploadResource(MultipartFile file, Long userId, String description, String draftKey, String type, Integer downloadType, Integer pointsNeeded) {
        log.info("开始上传资源 - 用户ID: {}, 文件名: {}, 大小: {} bytes, 描述: {}, 草稿键: {}, 类型: {}", 
                userId, file.getOriginalFilename(), file.getSize(), description, draftKey, type);
        
        // 验证用户是否存在
        validateUser(userId);
        
        // 验证文件
        validateResourceFile(file);
        
        try {
            // 保存文件
            String relativePath = fileUtil.saveFile(file, fileUploadConfig.getResourcePath());
            
            // 生成访问URL
            String fileUrl = fileUtil.generateFileUrl(relativePath);
            
            // 创建资源记录
            Resources resource = new Resources();
            resource.setName(file.getOriginalFilename());
            resource.setDescription(description);
            resource.setFileUrl(fileUrl);
            resource.setUploaderId(userId);
            resource.setDownloadType(downloadType != null ? downloadType : 0); // 使用传入参数或默认免费下载
            resource.setPointsNeeded(pointsNeeded != null ? new BigDecimal(pointsNeeded) : BigDecimal.ZERO);
            
            // 保存到数据库
            resourcesMapper.insert(resource);
            Long resourceId = resource.getId();
            
            Long attachmentId = null;
            // 如果提供了草稿键，创建附件关联记录
            if (draftKey != null && !draftKey.trim().isEmpty()) {
                PostAttachments attachment = new PostAttachments();
                attachment.setDraftKey(draftKey);
                attachment.setResourceId(resourceId);
                attachment.setType(type != null ? type : "resource");
                
                postAttachmentsMapper.insert(attachment);
                attachmentId = attachment.getId();
                
                log.info("创建草稿附件关联 - 草稿键: {}, 资源ID: {}, 附件ID: {}, 类型: {}", 
                        draftKey, resourceId, attachmentId, type);
            }
            
            // 构建响应
            FileUploadResl result = new FileUploadResl();
            result.setFileName(file.getOriginalFilename());
            result.setFilePath(relativePath);
            result.setFileUrl(fileUrl);
            result.setFileSize(file.getSize());
            result.setFileType("resource");
            result.setExtension(fileUtil.getFileExtension(file.getOriginalFilename()));
            result.setUploadTime(System.currentTimeMillis());
            result.setResourceId(resourceId);
            result.setAttachmentId(attachmentId);
            
            log.info("资源上传成功 - 用户ID: {}, 文件路径: {}, 访问URL: {}, 资源ID: {}, 附件ID: {}", 
                    userId, relativePath, fileUrl, resourceId, attachmentId);
            
            return result;
            
        } catch (IOException e) {
            log.error("资源上传失败 - 用户ID: {}, 文件名: {}", userId, file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件保存失败: " + e.getMessage());
        }
    }
    
    /**
     * 验证用户是否存在
     * 
     * @param userId 用户ID
     */
    private void validateUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        
        Users user = userMapper.selectById(userId);
        if (user == null || user.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }
    
    /**
     * 验证图片文件
     * 
     * @param file 文件
     */
    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        
        // 检查文件类型
        if (!fileUtil.isAllowedImageType(file.getOriginalFilename())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, 
                    "不支持的图片格式，支持的格式: " + String.join(", ", fileUploadConfig.getAllowedImageTypes()));
        }
        
        // 检查文件大小
        if (!fileUtil.isValidFileSize(file.getSize(), fileUploadConfig.getMaxImageSize())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, 
                    "图片文件大小不能超过 " + (fileUploadConfig.getMaxImageSize() / 1024 / 1024) + "MB");
        }
    }
    
    /**
     * 验证文档文件
     * 
     * @param file 文件
     */
    private void validateDocumentFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        
        // 检查文件类型
        if (!fileUtil.isAllowedDocumentType(file.getOriginalFilename())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, 
                    "不支持的文档格式，支持的格式: " + String.join(", ", fileUploadConfig.getAllowedDocumentTypes()));
        }
        
        // 检查文件大小
        if (!fileUtil.isValidFileSize(file.getSize(), fileUploadConfig.getMaxFileSize())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, 
                    "文档文件大小不能超过 " + (fileUploadConfig.getMaxFileSize() / 1024 / 1024) + "MB");
        }
    }
    
    /**
     * 验证资源文件
     * 
     * @param file 文件
     */
    private void validateResourceFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        
        // 检查文件类型
        if (!fileUtil.isAllowedResourceType(file.getOriginalFilename())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, 
                    "不支持的资源格式，支持的格式: " + String.join(", ", fileUploadConfig.getAllowedResourceTypes()));
        }
        
        // 检查文件大小
        if (!fileUtil.isValidFileSize(file.getSize(), fileUploadConfig.getMaxFileSize())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, 
                    "资源文件大小不能超过 " + (fileUploadConfig.getMaxFileSize() / 1024 / 1024) + "MB");
        }
    }
    
    /**
     * 查询草稿附件列表
     * 
     * @param draftKey 草稿关联键
     * @param userId 用户ID
     * @return 附件列表
     */
    public java.util.List<java.util.Map<String, Object>> getDraftAttachments(String draftKey, Long userId) {
        log.info("查询草稿附件 - 用户ID: {}, 草稿键: {}", userId, draftKey);
        
        // 验证用户
        validateUser(userId);
        
        // 查询草稿附件
        return postAttachmentsMapper.selectDraftAttachments(draftKey, userId);
    }
    
    /**
     * 查询文章附件列表
     * 
     * @param postId 文章ID
     * @param userId 用户ID
     * @return 附件列表
     */
    public java.util.List<java.util.Map<String, Object>> getPostAttachments(Long postId, Long userId) {
        log.info("查询文章附件 - 用户ID: {}, 文章ID: {}", userId, postId);
        
        // 验证用户
        validateUser(userId);
        
        // 查询文章附件
        return postAttachmentsMapper.selectPostAttachments(postId, userId);
    }
    
    // 新增：更新资源元信息（下载类型、所需积分）
    @Transactional
    public void updateResourceMeta(Long resourceId, Long userId, Integer downloadType, Integer pointsNeeded) {
        log.info("更新资源元信息 - 用户ID: {}, 资源ID: {}, downloadType: {}, pointsNeeded: {}", userId, resourceId, downloadType, pointsNeeded);

        // 校验用户
        validateUser(userId);

        // 查询资源
        Resources resource = resourcesMapper.selectById(resourceId);
        if (resource == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "资源不存在");
        }

        // 权限校验：必须是上传者本人
        if (resource.getUploaderId() == null || !resource.getUploaderId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权修改该资源");
        }

        // 合法性校验与赋值
        if (downloadType == null) {
            downloadType = 0; // 默认免费
        }
        resource.setDownloadType(downloadType);

        if (downloadType == 0) {
            // 免费时强制置为 0
            resource.setPointsNeeded(java.math.BigDecimal.ZERO);
        } else {
            // 积分下载时，至少为 1
            int pts = (pointsNeeded == null ? 1 : Math.max(1, pointsNeeded));
            resource.setPointsNeeded(new java.math.BigDecimal(pts));
        }

        // 更新
        resourcesMapper.updateById(resource);
    }
    
    /**
     * 删除附件
     * 
     * @param resourceId 资源ID
     * @param userId 用户ID
     */
    @Transactional
    public void deleteAttachment(Long resourceId, Long userId) {
        log.info("删除附件 - 用户ID: {}, 资源ID: {}", userId, resourceId);
        
        // 验证用户
        validateUser(userId);
        
        // 查询资源信息
        Resources resource = resourcesMapper.selectById(resourceId);
        if (resource == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        
        // 验证权限（只能删除自己上传的附件）
        if (!resource.getUploaderId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限删除此附件");
        }
        
        try {
            // 删除物理文件
            if (resource.getFileUrl() != null) {
                String fileUrl = resource.getFileUrl();
                String prefix = "http://localhost:8080" + fileUploadConfig.getUrlPrefix() + "/";
                String relativePath = fileUrl;
                if (fileUrl.startsWith(prefix)) {
                    relativePath = fileUrl.substring(prefix.length());
                }
                fileUtil.deleteFile(relativePath);
            }
            
            // 删除数据库记录
            resourcesMapper.deleteById(resourceId);
            
            // 删除附件关联记录
            postAttachmentsMapper.deleteByResourceId(resourceId);
            
            log.info("附件删除成功 - 用户ID: {}, 资源ID: {}", userId, resourceId);
            
        } catch (Exception e) {
            log.error("删除附件失败 - 用户ID: {}, 资源ID: {}", userId, resourceId, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "删除附件失败: " + e.getMessage());
        }
    }
}