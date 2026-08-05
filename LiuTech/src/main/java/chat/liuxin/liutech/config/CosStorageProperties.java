package chat.liuxin.liutech.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 腾讯云 COS 对象存储配置
 * 通过 .env 环境变量注入（COS_ENABLED / COS_SECRET_ID / COS_SECRET_KEY / COS_REGION / COS_BUCKET）
 * 真实密钥只放本地 .env（已 gitignore），不入库
 *
 * @author 刘鑫
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "cos")
public class CosStorageProperties {

    /**
     * 是否启用 COS（false 或未配置 = 使用本地磁盘 {@code LocalFileStorage}）
     */
    private boolean enabled = false;

    /**
     * SecretId（腾讯云访问管理 CAM）
     */
    private String secretId;

    /**
     * SecretKey（与 SecretId 成对）
     */
    private String secretKey;

    /**
     * 桶地域，如 ap-chongqing
     */
    private String region;

    /**
     * 桶名（BucketName-APPID 格式，如 liutech-1341692466）
     */
    private String bucket;

    /**
     * 自定义访问域名（可选，如 https://static.liuxin.chat）
     * 配置后 generateUrl 用它替代 COS 默认域名（需先在 COS 控制台绑定自定义源站域名 + 证书）
     */
    private String baseUrl;

    /**
     * 生成访问域名（如 https://liutech-1341692466.cos.ap-chongqing.myqcloud.com）
     * 配置了 COS_BASE_URL 时优先使用自定义域名；配置不全时返回 null（未启用 COS 的部署环境）
     */
    public String getBaseUrl() {
        if (baseUrl != null && !baseUrl.isEmpty()) {
            return baseUrl;
        }
        if (bucket == null || bucket.isEmpty() || region == null || region.isEmpty()) {
            return null;
        }
        return "https://" + bucket + ".cos." + region + ".myqcloud.com";
    }
}
