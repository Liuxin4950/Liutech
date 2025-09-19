package chat.liuxin.liutech.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传响应类
 *
 * @author 刘鑫
 * @date 2025-08-07
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResp {

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件相对路径
     */
    private String filePath;

    /**
     * 文件访问URL
     */
    private String fileUrl;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 文件扩展名
     */
    private String extension;

    /**
     * 上传时间戳
     */
    private Long uploadTime;

    /**
     * 资源ID（resources表主键）
     */
    private Long resourceId;

    /**
     * 附件关联ID（post_attachments表主键）
     */
    private Long attachmentId;
}
