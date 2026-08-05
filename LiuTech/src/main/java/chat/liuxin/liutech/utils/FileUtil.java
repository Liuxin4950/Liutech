package chat.liuxin.liutech.utils;

import chat.liuxin.liutech.config.FileUploadConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件工具类
 * 提供文件上传、保存、验证等功能
 * 
 * @author 刘鑫
 * @date 2025-08-07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileUtil {

    private final FileUploadConfig fileUploadConfig;

    /** 图片URL提取正则：匹配 <img src="URL">，支持属性换行和多种格式 */
    private static final Pattern IMG_SRC_PATTERN = Pattern.compile(
            "<img\\s+[^>]*?src\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>", Pattern.CASE_INSENSITIVE);

    /** 图片URL提取正则（无引号版本） */
    private static final Pattern IMG_SRC_PATTERN_NO_QUOTE = Pattern.compile(
            "<img\\s+[^>]*?src\\s*=\\s*([^\\s\"'>]+)[^>]*>", Pattern.CASE_INSENSITIVE);

    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[[^\\]]*\\]\\(([^)\\s]+)\\)");
    
    /**
     * 保存上传的文件
     * 
     * @param file 上传的文件
     * @param subPath 子路径（如：images、documents、resources）
     * @return 文件相对路径
     * @throws IOException IO异常
     */
    public String saveFile(MultipartFile file, String subPath) throws IOException {
        // 生成文件名
        String fileName = generateFileName(file.getOriginalFilename());
        
        // 构建完整路径
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String relativePath = subPath + "/" + datePath + "/" + fileName;
        
        // 创建完整的文件路径（确保为绝对路径，避免Tomcat相对路径解析到临时目录）
        Path base = Paths.get(fileUploadConfig.getBasePath());
        Path fullPath = (base.isAbsolute() ? base : base.toAbsolutePath()).resolve(relativePath);
        
        // 确保目录存在
        Files.createDirectories(fullPath.getParent());
        
        // 保存文件
        java.io.File targetFile = fullPath.toFile();
        if (targetFile != null) {
            file.transferTo(targetFile);
        }
        
        return relativePath;
    }

    /**
     * 保存字节数组为文件（用于压缩后的图片保存）
     * 路径生成规则与 saveFile(MultipartFile) 完全一致
     *
     * @param data 文件字节
     * @param subPath 子路径（如：images）
     * @param originalFilename 用于生成扩展名的文件名
     * @return 文件相对路径
     * @throws IOException IO异常
     */
    public String saveFile(byte[] data, String subPath, String originalFilename) throws IOException {
        String fileName = generateFileName(originalFilename);
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String relativePath = subPath + "/" + datePath + "/" + fileName;
        Path base = Paths.get(fileUploadConfig.getBasePath());
        Path fullPath = (base.isAbsolute() ? base : base.toAbsolutePath()).resolve(relativePath);
        Files.createDirectories(fullPath.getParent());
        Files.write(fullPath, data);
        return relativePath;
    }
    
    /**
     * 生成唯一文件名
     * 
     * @param originalFilename 原始文件名
     * @return 新文件名
     */
    private String generateFileName(String originalFilename) {
        String extension = getFileExtension(originalFilename);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return timestamp + "_" + uuid + "." + extension;
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
     * 生成文件访问URL
     * 
     * @param relativePath 文件相对路径
     * @return 完整的访问URL
     */
    public String generateFileUrl(String relativePath) {
        // 返回相对路径（如 /uploads/images/2026/01/xxx.png），由前端反向代理统一转发：
        // - 开发环境：vite proxy /uploads -> http://localhost:8080
        // - 生产环境：nginx location /uploads/ -> backend
        // 数据库存储与环境无关，避免开发/生产域名不一致导致图片加载失败
        return fileUploadConfig.getUrlPrefix() + "/" + relativePath;
    }
    
    /**
     * 基于文件头探测图片真实格式
     * 用于 TinyMCE 粘贴上传的 blob：文件名不可靠（如 blobid0.png 实际可能是 CMYK JPEG）
     *
     * @param data 图片字节
     * @return 真实扩展名（小写），无法识别返回 null
     */
    public String detectImageFormat(byte[] data) {
        if (data == null || data.length < 12) {
            return null;
        }
        // JPEG: FF D8 FF
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "jpg";
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
     * 删除文件
     *
     * @param relativePath 文件相对路径
     * @return 是否删除成功
     */
    public boolean deleteFile(String relativePath) {
        try {
            Path filePath = Paths.get(fileUploadConfig.getBasePath(), relativePath);
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * 检查文件是否存在
     *
     * @param relativePath 文件相对路径
     * @return 是否存在
     */
    public boolean fileExists(String relativePath) {
        Path filePath = Paths.get(fileUploadConfig.getBasePath(), relativePath);
        return Files.exists(filePath);
    }

    /**
     * 从完整URL提取相对路径
     * @param fileUrl 完整文件URL（如 http://localhost:8080/uploads/music/2025/01/05/xxx.mp3）
     * @return 相对路径（如 music/2025/01/05/xxx.mp3），提取失败返回null
     */
    public String extractRelativePath(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return null;
        }

        String prefix = fileUploadConfig.getServerBaseUrl() + fileUploadConfig.getUrlPrefix() + "/";
        if (fileUrl.startsWith(prefix)) {
            return fileUrl.substring(prefix.length());
        }

        // 兼容旧数据或其他来源的URL
        if (fileUrl.startsWith("/uploads/")) {
            return fileUrl.substring("/uploads/".length());
        }

        return null;
    }

    /**
     * 从完整URL删除文件（便捷方法）
     * @param fileUrl 完整文件URL
     * @return 是否删除成功（URL无法解析返回false）
     */
    public boolean deleteFileByUrl(String fileUrl) {
        String relativePath = extractRelativePath(fileUrl);
        if (relativePath == null) {
            log.warn("无法解析文件路径: {}", fileUrl);
            return false; // URL无法解析返回false
        }
        return deleteFile(relativePath); // 返回实际删除结果
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
     * 从HTML内容中提取所有图片URL（仅提取系统内的图片）
     *
     * @param content HTML内容
     * @return 图片URL列表（去重）
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
            if (src != null && (src.startsWith("/uploads/") || src.contains("/uploads/"))) {
                urls.add(src);
            }
        }

        Matcher noQuoteMatcher = IMG_SRC_PATTERN_NO_QUOTE.matcher(content);
        while (noQuoteMatcher.find()) {
            String src = noQuoteMatcher.group(1);
            if (src != null) {
                src = src.trim();
            }
            if (src != null && (src.startsWith("/uploads/") || src.contains("/uploads/"))) {
                urls.add(src);
            }
        }

        Matcher markdownMatcher = MARKDOWN_IMAGE_PATTERN.matcher(content);
        while (markdownMatcher.find()) {
            String src = markdownMatcher.group(1);
            if (src != null) {
                src = src.trim();
            }
            if (src != null && (src.startsWith("/uploads/") || src.contains("/uploads/"))) {
                urls.add(src);
            }
        }

        return urls;
    }
}
