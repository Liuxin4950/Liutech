package chat.liuxin.liutech.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传请求类
 * 
 * @author 刘鑫
 * @date 2025-08-07
 */
@Data
public class FileUploadReq {
    
    /**
     * 上传的文件
     */
    @NotNull(message = "文件不能为空")
    private MultipartFile file;
    
    /**
     * 文件描述
     */
    private String description;
    
    /**
     * 文件类型（image, document, resource）
     */
    @NotBlank(message = "文件类型不能为空")
    private String fileType;
    
    /**
     * 草稿关联键（可选，用于草稿附件）
     */
    private String draftKey;
    
    /**
     * 附件类型（可选，如 image, document, resource 等）
     */
    private String type;
}