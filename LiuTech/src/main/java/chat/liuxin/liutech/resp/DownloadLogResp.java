package chat.liuxin.liutech.resp;

import chat.liuxin.liutech.model.ResourceDownloads;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 下载记录响应类
 * 继承 ResourceDownloads 实体类，只添加扩展字段
 * 用于管理端下载记录列表展示
 *
 * @author 刘鑫
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DownloadLogResp extends ResourceDownloads {

    /**
     * 下载用户名
     */
    private String username;

    /**
     * 资源名称
     */
    private String resourceName;
}
