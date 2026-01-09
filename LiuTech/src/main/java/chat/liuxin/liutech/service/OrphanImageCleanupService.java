package chat.liuxin.liutech.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import chat.liuxin.liutech.mapper.CarouselMapper;
import chat.liuxin.liutech.mapper.MusicMapper;
import chat.liuxin.liutech.mapper.PostsMapper;
import chat.liuxin.liutech.mapper.ResourcesMapper;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.Carousel;
import chat.liuxin.liutech.model.Posts;
import chat.liuxin.liutech.model.Resources;
import lombok.extern.slf4j.Slf4j;

/**
 * 孤立图片清理服务
 * 定期清理数据库中没有引用的图片文件
 * @author liuxin
 */
@Slf4j
@Service
public class OrphanImageCleanupService {

    private static final long ORPHAN_FILE_THRESHOLD_DAYS = 7; // 生产环境：7天
    private static final String[] IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"};
    /**
     * 文件名时间戳格式: yyyyMMddHHmmss
     */
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("^(\\d{14})_[a-f0-9]{32}\\.(.+)$");

    /**
     * 从HTML内容中提取图片URL的正则
     * 匹配 <img src="..."> 和 <img src='...'> 格式
     */
    private static final Pattern IMG_SRC_PATTERN = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"'][^>]*>");

    /**
     * 从任意文本中提取 /uploads/images/ 路径的图片
     */
    private static final Pattern UPLOADS_IMAGE_PATTERN = Pattern.compile("/uploads/images/[^\\s\"']+");

    /**
     * 图片上传基础路径
     * 从环境变量 FILE_UPLOAD_BASE_PATH 读取，未配置则使用 ${user.dir}/uploads
     */
    @Value("${file.upload.base-path:${user.dir}/uploads}")
    private String uploadBasePath;

    @Autowired
    private CarouselMapper carouselMapper;

    @Autowired
    private ResourcesMapper resourcesMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PostsMapper postsMapper;

    @Autowired
    private MusicMapper musicMapper;

    /**
     * 每天凌晨3点执行（北京时间）
     * 测试时可改为 @Scheduled(cron = "0 * * * * ?") 每分钟执行
     */
    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Shanghai")
    // @Scheduled(cron = "0 * * * * ?") // 每分钟执行（测试用）
    public void cleanupOrphanImages() {
        log.info("开始清理孤立图片...");

        try {
            // 1. 获取数据库中所有被引用的图片文件名
            Set<String> usedFileNames = collectUsedFileNames();
            log.info("数据库中引用的图片数量: {}", usedFileNames.size());

            // 2. 扫描 uploads/images/ 目录
            Path imagesPath = Paths.get(uploadBasePath, "images");
            if (!Files.exists(imagesPath)) {
                log.info("图片目录不存在，跳过清理: {}", imagesPath);
                return;
            }

            // 3. 找出并删除孤立文件
            final Set<String> finalUsedFileNames = usedFileNames;
            long[] deletedCount = {0};
            long[] skipCount = {0};

            Files.walk(imagesPath)
                .filter(Files::isRegularFile)
                .filter(this::isImageFile)
                .filter(path -> isOrphanFile(path, finalUsedFileNames))
                .filter(path -> {
                    // 根据文件名中的时间戳判断是否超过7天
                    if (!isOlderThanDaysByFileName(path, ORPHAN_FILE_THRESHOLD_DAYS)) {
                        skipCount[0]++;
                        log.debug("跳过文件（未超过{}天）: {}", ORPHAN_FILE_THRESHOLD_DAYS, path.getFileName());
                        return false;
                    }
                    return true;
                })
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        deletedCount[0]++;
                        log.info("删除孤立图片: {}", path.getFileName());
                    } catch (IOException e) {
                        log.warn("删除文件失败: {}", path, e);
                    }
                });

            log.info("孤立图片清理完成，共删除 {} 个文件，跳过 {} 个文件（未超过{}天）", deletedCount[0], skipCount[0], ORPHAN_FILE_THRESHOLD_DAYS);
        } catch (Exception e) {
            log.error("清理孤立图片失败", e);
        }
    }

    /**
     * 收集数据库中所有被引用的图片文件名
     * 直接从 URL 中提取文件名，不依赖配置
     */
    private Set<String> collectUsedFileNames() {
        Set<String> fileNames = new HashSet<>();

        // 1. 从 users 表收集（用户头像）
        List<String> avatars = userMapper.selectAllAvatarUrls();
        for (String url : avatars) {
            String fileName = extractFileName(url);
            if (fileName != null) {
                fileNames.add(fileName);
                log.debug("收集用户头像: {}", fileName);
            }
        }

        // 2. 从 posts 表收集（文章封面、缩略图、内容中的内嵌图片）
        // 封面图片
        List<String> coverImages = postsMapper.selectAllCoverImages();
        for (String url : coverImages) {
            String fileName = extractFileName(url);
            if (fileName != null) {
                fileNames.add(fileName);
                log.debug("收集文章封面: {}", fileName);
            }
        }
        // 缩略图
        List<String> thumbnails = postsMapper.selectAllThumbnails();
        for (String url : thumbnails) {
            String fileName = extractFileName(url);
            if (fileName != null) {
                fileNames.add(fileName);
                log.debug("收集文章缩略图: {}", fileName);
            }
        }
        // 文章内容中的内嵌图片
        List<Posts> posts = postsMapper.selectAllPublishedPostsWithContent();
        log.info("查询到 {} 篇已发布文章用于检查内嵌图片", posts.size());
        for (Posts post : posts) {
            if (post.getContent() != null) {
                Set<String> contentImages = extractImagesFromContent(post.getContent());
                log.debug("文章 {} 找到 {} 个内嵌图片", post.getId(), contentImages.size());
                for (String imgUrl : contentImages) {
                    String fileName = extractFileName(imgUrl);
                    if (fileName != null) {
                        fileNames.add(fileName);
                        log.debug("收集文章内嵌图片 (post={}): {}", post.getId(), fileName);
                    }
                }
            }
        }

        // 3. 从 music 表收集（音乐封面）
        List<String> musicCovers = musicMapper.selectAllCoverUrls();
        for (String url : musicCovers) {
            String fileName = extractFileName(url);
            if (fileName != null) {
                fileNames.add(fileName);
                log.trace("收集音乐封面: {}", fileName);
            }
        }

        // 4. 从 carousel 表收集（轮播图）
        List<Carousel> carousels = carouselMapper.selectList(null);
        for (Carousel carousel : carousels) {
            if (carousel.getImageUrl() != null) {
                String fileName = extractFileName(carousel.getImageUrl());
                if (fileName != null) {
                    fileNames.add(fileName);
                    log.trace("收集轮播图: {}", fileName);
                }
            }
        }

        // 5. 从 resources 表收集（用户上传的资源文件，只收集图片类型）
        List<Resources> resources = resourcesMapper.selectList(null);
        for (Resources resource : resources) {
            if (resource.getFileUrl() != null && isImageUrl(resource.getFileUrl())) {
                String fileName = extractFileName(resource.getFileUrl());
                if (fileName != null) {
                    fileNames.add(fileName);
                    log.trace("收集资源文件: {}", fileName);
                }
            }
        }

        return fileNames;
    }

    /**
     * 从HTML内容中提取所有图片URL
     * @param content HTML内容
     * @return 图片URL集合
     */
    private Set<String> extractImagesFromContent(String content) {
        Set<String> images = new HashSet<>();
        if (content == null) {
            return images;
        }

        // 方法1: 匹配 <img src="..."> 格式
        Matcher imgMatcher = IMG_SRC_PATTERN.matcher(content);
        while (imgMatcher.find()) {
            String src = imgMatcher.group(1);
            if (src != null && src.contains("/uploads/images/")) {
                images.add(src);
            }
        }

        // 方法2: 匹配任意位置的 /uploads/images/ 路径
        Matcher pathMatcher = UPLOADS_IMAGE_PATTERN.matcher(content);
        while (pathMatcher.find()) {
            String path = pathMatcher.group();
            if (path != null) {
                images.add(path);
            }
        }

        return images;
    }

    /**
     * 从 URL 中提取文件名
     * 支持以下格式：
     * - http://localhost:8080/uploads/images/2025/01/09/xxx.jpg
     * - http://api.example.com/uploads/images/2025/01/09/xxx.jpg
     * - /uploads/images/2025/01/09/xxx.jpg
     * - images/2025/01/09/xxx.jpg
     * - xxx.jpg
     */
    private String extractFileName(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        // 移除查询参数和锚点
        String cleanUrl = url.split("[#?]")[0];

        // 获取路径最后一部分作为文件名
        int lastSlash = cleanUrl.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < cleanUrl.length() - 1) {
            return cleanUrl.substring(lastSlash + 1);
        } else if (cleanUrl.length() > 0) {
            return cleanUrl;
        }

        return null;
    }

    /**
     * 判断是否为图片文件
     */
    private boolean isImageFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        for (String ext : IMAGE_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断URL是否为图片URL（根据扩展名判断）
     */
    private boolean isImageUrl(String url) {
        if (url == null) {
            return false;
        }
        String fileName = extractFileName(url);
        if (fileName == null) {
            return false;
        }
        String lowerFileName = fileName.toLowerCase();
        for (String ext : IMAGE_EXTENSIONS) {
            if (lowerFileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为孤立文件（文件名不在数据库引用中）
     */
    private boolean isOrphanFile(Path path, Set<String> usedFileNames) {
        String fileName = path.getFileName().toString();
        boolean isOrphan = !usedFileNames.contains(fileName);
        if (isOrphan) {
            log.trace("发现孤立文件: {}", fileName);
        }
        return isOrphan;
    }

    /**
     * 根据文件名中的时间戳判断文件是否超过指定天数
     * 文件名格式: yyyyMMddHHmmss_uuid.extension
     */
    private boolean isOlderThanDaysByFileName(Path path, long days) {
        String fileName = path.getFileName().toString();
        Matcher matcher = FILE_NAME_PATTERN.matcher(fileName);

        if (!matcher.matches()) {
            // 文件名格式不匹配，尝试使用文件修改时间
            log.trace("文件名格式不匹配，使用修改时间判断: {}", fileName);
            return isOlderThanDaysByMtime(path, days);
        }

        try {
            String timestamp = matcher.group(1);
            LocalDateTime fileTime = LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            LocalDateTime threshold = LocalDateTime.now().minusDays(days);
            return fileTime.isBefore(threshold);
        } catch (DateTimeParseException e) {
            log.warn("解析文件名时间戳失败: {}", fileName);
            return isOlderThanDaysByMtime(path, days);
        }
    }

    /**
     * 根据文件修改时间判断是否超过指定天数（备选方案）
     */
    private boolean isOlderThanDaysByMtime(Path path, long days) {
        try {
            java.nio.file.attribute.FileTime lastModifiedTime = Files.getLastModifiedTime(path);
            long fileAge = java.time.Duration.between(lastModifiedTime.toInstant(), java.time.Instant.now()).toDays();
            return fileAge >= days;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 兼容旧方法名
     */
    private boolean isOlderThanDays(Path path, long days) {
        return isOlderThanDaysByFileName(path, days);
    }
}
