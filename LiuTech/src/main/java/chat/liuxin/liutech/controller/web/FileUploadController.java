package chat.liuxin.liutech.controller.web;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resl.FileUploadResl;
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
    public Result<FileUploadResl> uploadImage(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        
        Long userId = userUtils.getCurrentUserId();
        log.info("接收到图片上传请求 - 用户ID: {}, 文件名: {}", userId, file.getOriginalFilename());
        
        FileUploadResl result = fileUploadService.uploadImage(file, userId);
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
    public Result<FileUploadResl> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            HttpServletRequest request) {
        
        Long userId = userUtils.getCurrentUserId();
        log.info("接收到文档上传请求 - 用户ID: {}, 文件名: {}, 描述: {}", 
                userId, file.getOriginalFilename(), description);
        
        FileUploadResl result = fileUploadService.uploadDocument(file, userId, description);
        return Result.success(result);
    }
    
    /**
     * 上传资源文件
     * 
     * @param file 资源文件
     * @param description 文件描述
     * @return 上传结果
     */
    @PostMapping("/resource")
    public Result<FileUploadResl> uploadResource(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            HttpServletRequest request) {
        
        Long userId = userUtils.getCurrentUserId();
        log.info("接收到资源上传请求 - 用户ID: {}, 文件名: {}, 描述: {}", 
                userId, file.getOriginalFilename(), description);
        
        FileUploadResl result = fileUploadService.uploadResource(file, userId, description);
        return Result.success(result);
    }
    
    /**
     * TinyMCE编辑器专用图片上传接口
     * 返回格式符合TinyMCE要求
     * 
     * @param file 图片文件
     * @return TinyMCE格式的响应
     */
    @PostMapping("/tinymce/image")
    public Object uploadImageForTinyMCE(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        
        Long userId = userUtils.getCurrentUserId();
        log.info("接收到TinyMCE图片上传请求 - 用户ID: {}, 文件名: {}", userId, file.getOriginalFilename());
        
        try {
            FileUploadResl result = fileUploadService.uploadImage(file, userId);
            
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
}