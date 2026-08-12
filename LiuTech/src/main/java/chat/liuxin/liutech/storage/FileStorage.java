package chat.liuxin.liutech.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文件存储抽象
 * 业务层只依赖逻辑路径（如 images/2026/08/05/xxx.jpg），不感知存储后端：
 * - 当前实现 {@link LocalFileStorage}（本地磁盘）
 * - 未来可增加 OSS 实现（上传对象存储、URL 返回 CDN 地址），切换时数据库无需迁移
 *
 * @author 刘鑫
 */
public interface FileStorage {

    /**
     * 保存文件
     *
     * @param data              文件字节
     * @param subPath           子路径（如 images、documents、resources、music）
     * @param originalFilename  原始文件名（仅用于生成扩展名）
     * @return 逻辑路径（如 images/2026/08/05/xxx.jpg），由实现决定目录结构
     * @throws IOException IO 异常
     */
    String save(byte[] data, String subPath, String originalFilename) throws IOException;

    /**
     * 删除文件（对象不存在视为成功，不抛异常）
     *
     * @param relativePath 逻辑路径
     */
    void delete(String relativePath);

    /**
     * 文件是否存在
     *
     * @param relativePath 逻辑路径
     * @return 是否存在
     */
    boolean exists(String relativePath);

    /**
     * 生成访问 URL
     * 本地实现返回相对路径（/uploads/...，由前端代理转发）；OSS 实现返回完整 CDN URL
     *
     * @param relativePath 逻辑路径
     * @return 访问 URL
     */
    String generateUrl(String relativePath);

    /**
     * 打开文件内容（付费资源下载等鉴权场景，由后端转发，不直出）
     *
     * @param relativePath 逻辑路径
     * @return 文件内容流（调用方负责关闭），文件不存在返回 null
     */
    InputStream open(String relativePath);

    /**
     * 获取文件内容大小（字节）
     * 用于下载响应设置 Content-Length，前端据此显示下载进度
     *
     * @param relativePath 逻辑路径
     * @return 内容大小（字节），文件不存在或无法获取返回 -1（调用方不设 Content-Length，走 chunked）
     */
    long getContentLength(String relativePath);
}
