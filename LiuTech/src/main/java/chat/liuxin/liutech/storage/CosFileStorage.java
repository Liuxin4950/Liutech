package chat.liuxin.liutech.storage;

import chat.liuxin.liutech.config.CosStorageProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 腾讯云 COS 对象存储实现（{@link FileStorage}）
 * <p>
 * 与 {@link LocalFileStorage} 二选一：cos.enabled=true（环境变量 COS_ENABLED）时本实现生效。
 * - save：上传对象，对象键 = 逻辑路径（目录结构与本地实现一致，数据库零迁移）
 * - generateUrl：返回 COS 默认域名完整 URL（https://&lt;bucket&gt;.cos.&lt;region&gt;.myqcloud.com/...），
 *   前端 img src 直接可用，图片流量不再经过服务器
 *
 * @author 刘鑫
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cos", name = "enabled", havingValue = "true")
public class CosFileStorage implements FileStorage {

    private final CosStorageProperties cosProperties;

    private COSClient cosClient;

    @PostConstruct
    void init() {
        COSCredentials cred = new BasicCOSCredentials(cosProperties.getSecretId(), cosProperties.getSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(cosProperties.getRegion()));
        cosClient = new COSClient(cred, clientConfig);
        log.info("COS 存储已启用: bucket={}, region={}", cosProperties.getBucket(), cosProperties.getRegion());
    }

    @PreDestroy
    void shutdown() {
        if (cosClient != null) {
            cosClient.shutdown();
        }
    }

    @Override
    public String save(byte[] data, String subPath, String originalFilename) throws IOException {
        String relativePath = StoragePathUtil.generateRelativePath(subPath, originalFilename);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(data.length);
        cosClient.putObject(cosProperties.getBucket(), relativePath, new ByteArrayInputStream(data), metadata);
        return relativePath;
    }

    @Override
    public void delete(String relativePath) {
        try {
            // COS 删除不存在的对象不报错，符合"不存在视为成功"约定
            cosClient.deleteObject(cosProperties.getBucket(), relativePath);
        } catch (CosClientException e) {
            log.warn("删除 COS 对象失败: {}", relativePath, e);
        }
    }

    @Override
    public boolean exists(String relativePath) {
        return cosClient.doesObjectExist(cosProperties.getBucket(), relativePath);
    }

    @Override
    public String generateUrl(String relativePath) {
        return cosProperties.getBaseUrl() + "/" + relativePath;
    }

    @Override
    public InputStream open(String relativePath) {
        try {
            COSObject object = cosClient.getObject(cosProperties.getBucket(), relativePath);
            return object.getObjectContent();
        } catch (CosClientException e) {
            // 对象不存在（404）与网络错误都归入"打不开"，调用方按文件不存在处理
            log.warn("打开 COS 对象失败: {}", relativePath, e);
            return null;
        }
    }
}
