package chat.liuxin.liutech.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资源直链下载响应
 * <p>
 * COS 等对象存储开启时返回短期签名 URL，浏览器可直接下载；
 * 本地磁盘存储不支持直链时 {@link #url} 为 null，前端回退到原流式下载接口。
 *
 * @author 刘鑫
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownloadUrlResp {

    /**
     * 签名下载 URL；当前存储不支持直链时为空
     */
    private String url;

    /**
     * URL 过期时间戳（毫秒）；url 为空时为 0
     */
    private long expiresAt;
}
