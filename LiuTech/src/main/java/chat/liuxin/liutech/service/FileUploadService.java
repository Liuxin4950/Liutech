package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.config.FileUploadConfig;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.resl.FileUploadResl;
import chat.liuxin.liutech.utils.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

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
        log.info("开始上传资源 - 用户ID: {}, 文件名: {}, 大小: {} bytes, 描述: {}", 
                userId, file.getOriginalFilename(), file.getSize(), description);
        
        // 验证用户是否存在
        validateUser(userId);
        
        // 验证文件
        validateResourceFile(file);
        
        try {
            // 保存文件
            String relativePath = fileUtil.saveFile(file, fileUploadConfig.getResourcePath());
            
            // 生成访问URL
            String fileUrl = fileUtil.generateFileUrl(relativePath);
            
            // 构建响应
            FileUploadResl result = new FileUploadResl();
            result.setFileName(file.getOriginalFilename());
            result.setFilePath(relativePath);
            result.setFileUrl(fileUrl);
            result.setFileSize(file.getSize());
            result.setFileType("resource");
            result.setExtension(fileUtil.getFileExtension(file.getOriginalFilename()));
            result.setUploadTime(System.currentTimeMillis());
            
            log.info("资源上传成功 - 用户ID: {}, 文件路径: {}, 访问URL: {}", 
                    userId, relativePath, fileUrl);
            
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
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
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
}