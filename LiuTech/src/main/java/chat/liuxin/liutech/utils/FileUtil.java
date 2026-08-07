package chat.liuxin.liutech.utils;

import chat.liuxin.liutech.config.CosStorageProperties;
import chat.liuxin.liutech.config.FileUploadConfig;
import chat.liuxin.liutech.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件工具类
 * 职责：类型/大小校验、哈希计算、URL 提取与路径归一化
 * 磁盘 IO 与 URL 生成统一委托 {@link FileStorage}，本类不直接操作文件系统
 *
 * @author 刘鑫
 * @date 2025-08-07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileUtil {

    private final FileUploadConfig fileUploadConfig;

    private final FileStorage fileStorage;

    /** COS 配置（bucket/region 未配置时 getBaseUrl() 返回 null，各解析方法自动跳过 COS 形态） */
    private final CosStorageProperties cosStorageProperties;

    /** 图片URL提取正则：匹配 <img src="URL">，支持属性换行和多种格式 */
    private static final Pattern IMG_SRC_PATTERN = Pattern.compile(
            "<img\\s+[^>]*?src\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>", Pattern.CASE_INSENSITIVE);

    /** 图片URL提取正则（无引号版本） */
    private static final Pattern IMG_SRC_PATTERN_NO_QUOTE = Pattern.compile(
            "<img\\s+[^>]*?src\\s*=\\s*([^\\s\"'>]+)[^>]*>", Pattern.CASE_INSENSITIVE);

    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[[^\\]]*\\]\\(([^)\\s]+)\\)");

    /**
     * 验证文件类型是否允许
     *
     * @param filename 文件名
     * @param allowedTypes 允许的文件类型数组
     * @return 是否允许
     */
    public boolean isAllowedFileType(String filename, String[] allowedTypes) {
        String extension = getFileExtension(filename);
        return Arrays.asList(allowedTypes).contains(extension);
    }

    /**
     * 验证图片文件类型
     *
     * @param filename 文件名
     * @return 是否为允许的图片类型
     */
    public boolean isAllowedImageType(String filename) {
        return isAllowedFileType(filename, fileUploadConfig.getAllowedImageTypes());
    }

    /**
     * 验证文档文件类型
     *
     * @param filename 文件名
     * @return 是否为允许的文档类型
     */
    public boolean isAllowedDocumentType(String filename) {
        return isAllowedFileType(filename, fileUploadConfig.getAllowedDocumentTypes());
    }

    /**
     * 验证资源文件类型
     *
     * @param filename 文件名
     * @return 是否为允许的资源类型
     */
    public boolean isAllowedResourceType(String filename) {
        return isAllowedFileType(filename, fileUploadConfig.getAllowedResourceTypes());
    }

    /**
     * 验证文件大小
     *
     * @param fileSize 文件大小
     * @param maxSize 最大允许大小
     * @return 是否在允许范围内
     */
    public boolean isValidFileSize(long fileSize, long maxSize) {
        return fileSize <= maxSize;
    }

    /**
     * 获取文件扩展名
     *
     * @param filename 文件名
     * @return 扩展名（小写）
     */
    public String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * 从完整URL提取相对路径
     * 兼容三种形态：站内完整 URL（SERVER_BASE_URL + /uploads/...）、相对路径 /uploads/...、COS 直出 URL
     * @param fileUrl 完整文件URL（如 http://localhost:8080/uploads/music/xxx.mp3 或 https://liutech-1341692466.cos.ap-chongqing.myqcloud.com/music/xxx.mp3）
     * @return 相对路径（如 music/xxx.mp3），提取失败返回null
     */
    public String extractRelativePath(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return null;
        }

        String prefix = fileUploadConfig.getServerBaseUrl() + fileUploadConfig.getUrlPrefix() + "/";
        if (fileUrl.startsWith(prefix)) {
            return fileUrl.substring(prefix.length());
        }

        // COS 直出 URL：https://<bucket>.cos.<region>.myqcloud.com/<逻辑路径>
        // 仅当 COS 已配置（bucket/region 非空）时识别，未启用 COS 的环境自动跳过
        String cosBaseUrl = cosStorageProperties.getBaseUrl();
        if (cosBaseUrl != null && fileUrl.startsWith(cosBaseUrl + "/")) {
            return fileUrl.substring(cosBaseUrl.length() + 1);
        }

        // 兼容旧数据或其他来源的相对路径 URL
        if (fileUrl.startsWith("/uploads/")) {
            return fileUrl.substring("/uploads/".length());
        }

        return null;
    }

    /**
     * 从URL删除文件（便捷方法）
     *
     * @param fileUrl 完整文件URL（如 /uploads/music/xxx.mp3 或 http://.../uploads/xxx.mp3）
     * @return 是否删除成功（URL无法解析返回false）
     */
    public boolean deleteFileByUrl(String fileUrl) {
        String relativePath = extractRelativePath(fileUrl);
        if (relativePath == null) {
            log.warn("无法解析文件路径: {}", fileUrl);
            return false;
        }
        fileStorage.delete(relativePath);
        return true;
    }

    /**
     * 统一路径归一化：URL / 相对路径 → 逻辑路径（如 images/2026/01/xxx.png）
     * 兼容四种存储格式：相对路径 /uploads/...、站内完整 URL、COS 直出 URL、历史遗留数据
     * 所有引用计数/对账/溯源场景必须走此方法，保证口径一致
     *
     * @param fileUrlOrRelativePath 文件URL或相对路径
     * @return 逻辑路径，无法识别返回null
     */
    public String normalizeToRelativePath(String fileUrlOrRelativePath) {
        if (fileUrlOrRelativePath == null || fileUrlOrRelativePath.isEmpty()) {
            return null;
        }
        String value = fileUrlOrRelativePath.trim();
        String relativePath = extractRelativePath(value);
        if (relativePath != null && !relativePath.isEmpty()) {
            return relativePath;
        }
        if (value.contains("://")) {
            // 畸形双重前缀容错：粘贴来源可能把完整 URL 拼了站内域名前缀（https://系统域https://COS域/...），
            // 计数/对账必须能归一化。仅当最后一个 :// 后的 host 属于系统域名（SERVER_BASE_URL/COS_BASE_URL
            // 的 host 及 www 变体）时才继续提取路径，外部站点绝对 URL 不受影响。
            String malformedPath = extractPathFromMalformedUrl(value);
            if (malformedPath != null) {
                return normalizeToRelativePath(malformedPath);
            }
            return null;
        }
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.startsWith("uploads/")) {
            value = value.substring("uploads/".length());
        }
        return value.isEmpty() ? null : value;
    }

    /**
     * 从畸形双重前缀 URL 提取路径段（host 之后的部分，如 /images/2026/...）
     * 例：https://liuxin.chathttps://static.liuxin.chat/images/x.png → /images/x.png
     * 校验最后一个 :// 后的 host 属于系统域名，防止误伤外部站点 URL
     */
    private String extractPathFromMalformedUrl(String url) {
        int lastScheme = url.lastIndexOf("://");
        if (lastScheme <= 0) {
            return null;
        }
        int hostEnd = url.indexOf('/', lastScheme + 3);
        if (hostEnd < 0) {
            return null;
        }
        String host = url.substring(lastScheme + 3, hostEnd);
        if (!isSystemHost(host)) {
            return null;
        }
        return url.substring(hostEnd);
    }

    /**
     * 判断 host 是否属于系统已知域名：SERVER_BASE_URL / COS_BASE_URL 的 host 及 www 变体
     */
    private boolean isSystemHost(String host) {
        return buildSystemHosts().contains(host);
    }

    /**
     * 构建系统域名集合（含 www 变体，兼容拼接时带/不带 www 的形态）
     */
    private Set<String> buildSystemHosts() {
        Set<String> hosts = new HashSet<>();
        addHostWithWww(hosts, fileUploadConfig.getServerBaseUrl());
        String cosBaseUrl = cosStorageProperties.getBaseUrl();
        if (cosBaseUrl != null) {
            addHostWithWww(hosts, cosBaseUrl);
        }
        return hosts;
    }

    private void addHostWithWww(Set<String> hosts, String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        String host = url.replaceFirst("^https?://", "");
        int slashIdx = host.indexOf('/');
        if (slashIdx >= 0) {
            host = host.substring(0, slashIdx);
        }
        if (host.isEmpty()) {
            return;
        }
        hosts.add(host);
        hosts.add(host.startsWith("www.") ? host.substring(4) : "www." + host);
    }

    /**
     * 计算文件的SHA-256哈希值
     *
     * @param file 上传的文件
     * @return 哈希值的十六进制字符串（64位）
     * @throws IOException IO异常
     */
    public String calculateFileHash(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            return calculateFileHash(is);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256算法不可用", e);
            throw new RuntimeException("哈希计算失败", e);
        }
    }

    /**
     * 计算输入流的SHA-256哈希值
     *
     * @param inputStream 输入流
     * @return 哈希值的十六进制字符串
     * @throws IOException IO异常
     * @throws NoSuchAlgorithmException SHA-256算法不可用
     */
    public String calculateFileHash(InputStream inputStream) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        byte[] buffer = new byte[8192];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
        }

        byte[] hashBytes = digest.digest();
        return bytesToHex(hashBytes);
    }

    /**
     * 字节数组转十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 基于文件头探测图片真实格式
     * 用于 TinyMCE 粘贴上传的 blob：文件名不可靠（如 blobid0.png 实际可能是 CMYK JPEG）
     *
     * @param data 图片字节
     * @return 真实扩展名（小写），无法识别返回 null
     */
    public String detectImageFormat(byte[] data) {
        if (data == null || data.length < 3) {
            return null;
        }
        // JPEG: FF D8 FF
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (data.length < 12) {
            return null;
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((data[0] & 0xFF) == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) {
            return "png";
        }
        // GIF: 47 49 46 38 ("GIF8")
        if (data[0] == 0x47 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x38) {
            return "gif";
        }
        // BMP: 42 4D ("BM")
        if (data[0] == 0x42 && data[1] == 0x4D) {
            return "bmp";
        }
        // WebP: 52 49 46 46 xx xx xx xx 57 45 42 50 ("RIFF....WEBP")
        if (data[0] == 0x52 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x46
                && data[8] == 0x57 && data[9] == 0x45 && data[10] == 0x42 && data[11] == 0x50) {
            return "webp";
        }
        return null;
    }

    /**
     * 从HTML内容中提取所有图片URL（仅提取系统内的图片）
     * 注意：不去重——同一张图出现 N 次返回 N 次，供引用计数按出现次数增减（与"正文每处引用计 1 点"口径一致）
     *
     * @param content HTML内容
     * @return 图片URL列表（按出现次数，不去重）
     */
    public List<String> extractImageUrls(String content) {
        List<String> urls = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return urls;
        }

        // 匹配带引号的 src
        Matcher matcher = IMG_SRC_PATTERN.matcher(content);
        while (matcher.find()) {
            String src = matcher.group(1);
            // 清理 URL：去除首尾引号和空格
            if (src != null) {
                src = src.replaceAll("^[\"']|[\"']$", "").trim();
            }
            if (isSystemImageUrl(src)) {
                urls.add(src);
            }
        }

        Matcher noQuoteMatcher = IMG_SRC_PATTERN_NO_QUOTE.matcher(content);
        while (noQuoteMatcher.find()) {
            String src = noQuoteMatcher.group(1);
            if (src != null) {
                src = src.trim();
            }
            if (isSystemImageUrl(src)) {
                urls.add(src);
            }
        }

        Matcher markdownMatcher = MARKDOWN_IMAGE_PATTERN.matcher(content);
        while (markdownMatcher.find()) {
            String src = markdownMatcher.group(1);
            if (src != null) {
                src = src.trim();
            }
            if (isSystemImageUrl(src)) {
                urls.add(src);
            }
        }

        return urls;
    }

    /**
     * 判断图片 URL 是否属于系统内：能归一化为逻辑路径的才是
     * 兼容：/uploads/... 相对路径、站内完整 URL、COS 直出 URL；外部站点图片（含外部站点的 uploads 路径）一律排除
     * 所有引用计数/对账的 URL 归属判断统一走此口径，不在此方法外散落前缀判断
     */
    private boolean isSystemImageUrl(String src) {
        return src != null && normalizeToRelativePath(src) != null;
    }
}
