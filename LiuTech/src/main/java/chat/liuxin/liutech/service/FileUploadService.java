package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.config.FileUploadConfig;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.storage.FileStorage;
import chat.liuxin.liutech.mapper.ResourcesMapper;
import chat.liuxin.liutech.mapper.PostAttachmentsMapper;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.model.Resources;
import chat.liuxin.liutech.model.PostAttachments;
import chat.liuxin.liutech.model.Images;
import chat.liuxin.liutech.resp.FileUploadResp;
import chat.liuxin.liutech.resp.ImageUploadResult;
import chat.liuxin.liutech.utils.FileUtil;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

/**
 * 文件上传服务类
 *
 * @author 刘鑫
 * @date 2025-08-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final FileUtil fileUtil;

    private final FileUploadConfig fileUploadConfig;

    private final UserMapper userMapper;

    private final ResourcesMapper resourcesMapper;

    private final PostAttachmentsMapper postAttachmentsMapper;

    private final ImagesService imagesService;

    private final FileStorage fileStorage;

    /**
     * 上传图片文件（用于TinyMCE编辑器）
     * 支持图片去重，相同内容的图片只保存一份
     *
     * @param file 图片文件
     * @param userId 用户ID
     * @return 上传结果
     */
    public FileUploadResp uploadImage(MultipartFile file, Long userId) {
        log.debug("开始上传图片 - 用户ID: {}, 文件名: {}, 大小: {} bytes",
                userId, file.getOriginalFilename(), file.getSize());

        // 验证用户是否存在
        validateUser(userId);

        // 验证文件
        validateImageFile(file);

        try {
            // 使用ImagesService进行去重上传
            ImageUploadResult uploadResult = imagesService.uploadImage(file, userId, fileUploadConfig.getImagePath());
            Images image = uploadResult.getImage();

            // 构建响应
            FileUploadResp result = new FileUploadResp();
            result.setFileName(image.getFileName());
            result.setFilePath(image.getFilePath());
            // 用 filePath 重新生成 URL，确保返回相对路径（重复图片的旧记录可能存的是完整 URL）
            result.setFileUrl(fileStorage.generateUrl(image.getFilePath()));
            result.setFileSize(image.getFileSize());
            result.setFileType("image");
            result.setExtension(image.getExtension());
            result.setUploadTime(System.currentTimeMillis());
            result.setImageId(image.getId());
            result.setIsDuplicate(uploadResult.isDuplicate());

            log.debug("图片上传成功 - 用户ID: {}, 图片ID: {}, 是否重复: {}, 访问URL: {}",
                    userId, image.getId(), result.getIsDuplicate(), image.getFileUrl());

            return result;

        } catch (IOException e) {
            log.error("图片上传失败 - 用户ID: {}, 文件名: {}", userId, file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件保存失败: " + e.getMessage());
        }
    }

    /**
     * 上传文档文件
     *
     * @param file 文档文件
     * @param userId 用户ID
     * @param description 文件描述
     * @return 上传结果
     */
    public FileUploadResp uploadDocument(MultipartFile file, Long userId, String description) {
        log.debug("开始上传文档 - 用户ID: {}, 文件名: {}, 大小: {} bytes, 描述: {}",
                userId, file.getOriginalFilename(), file.getSize(), description);

        // 验证用户是否存在
        validateUser(userId);

        // 验证文件
        validateDocumentFile(file);

        try {
            // 保存文件
            String relativePath = fileStorage.save(file.getBytes(), fileUploadConfig.getDocumentPath(), file.getOriginalFilename());

            // 生成访问URL
            String fileUrl = fileStorage.generateUrl(relativePath);

            // 构建响应
            FileUploadResp result = new FileUploadResp();
            result.setFileName(file.getOriginalFilename());
            result.setFilePath(relativePath);
            result.setFileUrl(fileUrl);
            result.setFileSize(file.getSize());
            result.setFileType("document");
            result.setExtension(fileUtil.getFileExtension(file.getOriginalFilename()));
            result.setUploadTime(System.currentTimeMillis());

            log.debug("文档上传成功 - 用户ID: {}, 文件路径: {}, 访问URL: {}",
                    userId, relativePath, fileUrl);

            return result;

        } catch (IOException e) {
            log.error("文档上传失败 - 用户ID: {}, 文件名: {}", userId, file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件保存失败: " + e.getMessage());
        }
    }

    /**
     * 上传资源文件
     *
     * @param file 资源文件
     * @param userId 用户ID
     * @param description 文件描述
     * @return 上传结果
     */
    public FileUploadResp uploadResource(MultipartFile file, Long userId, String description) {
        return uploadResource(file, userId, description, null, null, 0, 0);
    }

    /**
     * 上传资源文件（扩展版本，支持草稿附件）
     *
     * @param file 资源文件
     * @param userId 用户ID
     * @param description 文件描述
     * @param draftKey 草稿关联键（可选）
     * @param type 附件类型（可选）
     * @param downloadType 下载类型
     * @param pointsNeeded 所需积分
     * @return 上传结果
     */
    @Transactional(rollbackFor = Exception.class)
    public FileUploadResp uploadResource(MultipartFile file, Long userId, String description, String draftKey, String type, Integer downloadType, Integer pointsNeeded) {
        log.debug("开始上传资源 - 用户ID: {}, 文件名: {}, 大小: {} bytes, 描述: {}, 草稿键: {}, 类型: {}",
                userId, file.getOriginalFilename(), file.getSize(), description, draftKey, type);

        // 验证用户是否存在
        validateUser(userId);

        // 验证文件
        validateResourceFile(file);

        try {
            return saveResourceFile(file.getBytes(), file.getOriginalFilename(), file.getSize(), userId,
                    description, draftKey, type, downloadType, pointsNeeded);
        } catch (IOException e) {
            log.error("资源上传失败 - 用户ID: {}, 文件名: {}", userId, file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件保存失败: " + e.getMessage());
        }
    }

    /**
     * 保存资源文件并入库（普通上传与分片合并共用）
     * <p>
     * 幂等查重：网络超时重试、刷新页面重传等场景下，10 分钟内已上传过同名资源则直接复用，
     * 避免 CDN 掐断响应（前端误报失败）导致用户重试产生重复文件与记录。
     * 用户要上传新版同名文件时，先删除旧附件即可绕开查重。
     *
     * @param data         文件字节
     * @param fileName     文件名
     * @param fileSize     文件大小
     * @param userId       用户ID
     * @param description  文件描述
     * @param draftKey     草稿关联键
     * @param type         附件类型
     * @param downloadType 下载类型
     * @param pointsNeeded 所需积分
     * @return 上传结果
     */
    @Transactional(rollbackFor = Exception.class)
    public FileUploadResp saveResourceFile(byte[] data, String fileName, long fileSize, Long userId,
                                           String description, String draftKey, String type,
                                           Integer downloadType, Integer pointsNeeded) throws IOException {
        Resources duplicate = resourcesMapper.selectRecentDuplicate(userId, fileName);
        if (duplicate != null) {
            log.info("检测到重复上传，复用已有资源 - 用户ID: {}, 文件名: {}, 资源ID: {}",
                    userId, fileName, duplicate.getId());
            FileUploadResp result = new FileUploadResp();
            result.setFileName(duplicate.getName());
            result.setFileUrl(duplicate.getFileUrl());
            result.setFileSize(fileSize);
            result.setFileType("resource");
            result.setExtension(fileUtil.getFileExtension(fileName));
            result.setUploadTime(System.currentTimeMillis());
            result.setResourceId(duplicate.getId());
            result.setIsDuplicate(true);
            // 复用已有资源时也创建草稿附件关联（幂等），保证保存文章时能按 draftKey 绑定，
            // 否则页面显示了附件但数据库无关联，保存后文章详情查不到附件
            if (draftKey != null && !draftKey.trim().isEmpty()) {
                PostAttachments existing = postAttachmentsMapper.selectByDraftKeyAndResourceId(draftKey, duplicate.getId());
                if (existing == null) {
                    PostAttachments attachment = new PostAttachments();
                    attachment.setDraftKey(draftKey);
                    attachment.setResourceId(duplicate.getId());
                    attachment.setType(type != null ? type : "resource");
                    postAttachmentsMapper.insert(attachment);
                    result.setAttachmentId(attachment.getId());
                    log.debug("查重命中创建草稿附件关联 - 草稿键: {}, 资源ID: {}, 附件ID: {}",
                            draftKey, duplicate.getId(), attachment.getId());
                } else {
                    result.setAttachmentId(existing.getId());
                }
            }
            return result;
        }

        // 保存文件
        String relativePath = fileStorage.save(data, fileUploadConfig.getResourcePath(), fileName);

        // 生成访问URL
        String fileUrl = fileStorage.generateUrl(relativePath);

        // 创建资源记录
        Resources resource = new Resources();
        resource.setName(fileName);
        resource.setDescription(description);
        resource.setFileUrl(fileUrl);
        resource.setUploaderId(userId);
        int normalizedDownloadType = normalizeDownloadType(downloadType);
        resource.setDownloadType(normalizedDownloadType);
        resource.setPointsNeeded(normalizePointsNeeded(normalizedDownloadType, pointsNeeded));

        // 保存到数据库
        resourcesMapper.insert(resource);
        Long resourceId = resource.getId();

        Long attachmentId = null;
        // 如果提供了草稿键，创建附件关联记录
        if (draftKey != null && !draftKey.trim().isEmpty()) {
            PostAttachments attachment = new PostAttachments();
            attachment.setDraftKey(draftKey);
            attachment.setResourceId(resourceId);
            attachment.setType(type != null ? type : "resource");

            postAttachmentsMapper.insert(attachment);
            attachmentId = attachment.getId();

            log.debug("创建草稿附件关联 - 草稿键: {}, 资源ID: {}, 附件ID: {}, 类型: {}",
                    draftKey, resourceId, attachmentId, type);
        }

        // 构建响应
        FileUploadResp result = new FileUploadResp();
        result.setFileName(fileName);
        result.setFilePath(relativePath);
        result.setFileUrl(fileUrl);
        result.setFileSize(fileSize);
        result.setFileType("resource");
        result.setExtension(fileUtil.getFileExtension(fileName));
        result.setUploadTime(System.currentTimeMillis());
        result.setResourceId(resourceId);
        result.setAttachmentId(attachmentId);

        log.debug("资源上传成功 - 用户ID: {}, 文件路径: {}, 访问URL: {}, 资源ID: {}, 附件ID: {}",
                userId, relativePath, fileUrl, resourceId, attachmentId);

        return result;
    }

    /** 分片大小上限（5MB）：单片请求约 9 秒内完成，规避 CDN 边缘节点空闲超时掐断响应 */
    private static final long CHUNK_MAX_SIZE = 5 * 1024 * 1024;

    /**
     * 分片上传：接收单片文件（临时存本地，合并后统一入库）
     *
     * @param file       单片文件
     * @param userId     用户ID
     * @param uploadId   分片任务标识（客户端生成，同一次上传各片一致）
     * @param chunkIndex 分片序号（从 0 开始）
     * @param totalChunks 分片总数
     * @param fileName   原始文件名
     */
    public void uploadResourceChunk(MultipartFile file, Long userId, String uploadId,
                                    Integer chunkIndex, Integer totalChunks, String fileName) {
        validateUser(userId);

        if (uploadId == null || uploadId.trim().isEmpty() || uploadId.length() > 64) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分片标识不合法");
        }
        if (chunkIndex == null || chunkIndex < 0 || totalChunks == null || totalChunks < 1 || chunkIndex >= totalChunks) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分片参数不合法");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分片不能为空");
        }
        if (file.getSize() > CHUNK_MAX_SIZE) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "单片大小不能超过 5MB");
        }
        if (!fileUtil.isAllowedResourceType(fileName)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "不支持的资源格式，支持的格式: " + String.join(", ", fileUploadConfig.getAllowedResourceTypes()));
        }

        try {
            Path dir = Paths.get(fileUploadConfig.getBasePath(), "tmp", uploadId);
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(chunkIndex + ".part").toFile());
        } catch (IOException e) {
            log.error("分片保存失败 - 用户ID: {}, uploadId: {}, chunkIndex: {}", userId, uploadId, chunkIndex, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "分片保存失败");
        }
    }

    /**
     * 分片合并：校验完整性后按序拼接，走与普通上传一致的保存逻辑（含幂等查重）
     *
     * @param userId       用户ID
     * @param uploadId     分片任务标识
     * @param totalChunks  分片总数
     * @param fileName     原始文件名
     * @param description  文件描述
     * @param draftKey     草稿关联键
     * @param type         附件类型
     * @param downloadType 下载类型
     * @param pointsNeeded 所需积分
     * @return 上传结果
     */
    public FileUploadResp mergeResourceChunks(Long userId, String uploadId, Integer totalChunks, String fileName,
                                              String description, String draftKey, String type,
                                              Integer downloadType, Integer pointsNeeded) {
        validateUser(userId);

        if (uploadId == null || uploadId.trim().isEmpty() || uploadId.length() > 64) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分片标识不合法");
        }
        if (totalChunks == null || totalChunks < 1 || totalChunks > 1000) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分片总数不合法");
        }
        if (!fileUtil.isAllowedResourceType(fileName)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "不支持的资源格式，支持的格式: " + String.join(", ", fileUploadConfig.getAllowedResourceTypes()));
        }

        Path dir = Paths.get(fileUploadConfig.getBasePath(), "tmp", uploadId);
        if (!Files.isDirectory(dir)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分片不存在，请重新上传");
        }

        try {
            // 校验分片完整性并统计总大小
            long totalSize = 0;
            for (int i = 0; i < totalChunks; i++) {
                Path part = dir.resolve(i + ".part");
                if (!Files.isRegularFile(part)) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "分片不完整，请重新上传");
                }
                totalSize += Files.size(part);
            }
            if (totalSize == 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件为空");
            }
            if (totalSize > fileUploadConfig.getMaxFileSize()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源文件大小不能超过 100MB");
            }

            // 按序合并到内存
            byte[] data = new byte[(int) totalSize];
            int offset = 0;
            for (int i = 0; i < totalChunks; i++) {
                byte[] part = Files.readAllBytes(dir.resolve(i + ".part"));
                System.arraycopy(part, 0, data, offset, part.length);
                offset += part.length;
            }

            // 清理临时分片目录
            deleteRecursively(dir);

            return saveResourceFile(data, fileName, totalSize, userId, description, draftKey, type, downloadType, pointsNeeded);
        } catch (IOException e) {
            log.error("分片合并失败 - 用户ID: {}, uploadId: {}", userId, uploadId, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "分片合并失败: " + e.getMessage());
        }
    }

    /** 递归删除目录（分片临时目录清理） */
    private void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // 尽力清理，忽略单文件失败
                        }
                    });
        }
    }

    /**
     * 验证用户是否存在
     *
     * @param userId 用户ID
     */
    private void validateUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Users user = userMapper.selectById(userId);
        if (user == null || user.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    /**
     * 验证图片文件
     *
     * @param file 文件
     */
    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }

        // 检查文件类型
        if (!fileUtil.isAllowedImageType(file.getOriginalFilename())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "不支持的图片格式，支持的格式: " + String.join(", ", fileUploadConfig.getAllowedImageTypes()));
        }

        // 检查文件大小
        if (!fileUtil.isValidFileSize(file.getSize(), fileUploadConfig.getMaxImageSize())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "图片文件大小不能超过 " + (fileUploadConfig.getMaxImageSize() / 1024 / 1024) + "MB");
        }
    }

    /**
     * 验证文档文件
     *
     * @param file 文件
     */
    private void validateDocumentFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }

        // 检查文件类型
        if (!fileUtil.isAllowedDocumentType(file.getOriginalFilename())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "不支持的文档格式，支持的格式: " + String.join(", ", fileUploadConfig.getAllowedDocumentTypes()));
        }

        // 检查文件大小
        if (!fileUtil.isValidFileSize(file.getSize(), fileUploadConfig.getMaxFileSize())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "文档文件大小不能超过 " + (fileUploadConfig.getMaxFileSize() / 1024 / 1024) + "MB");
        }
    }

    /**
     * 验证资源文件
     *
     * @param file 文件
     */
    private void validateResourceFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }

        // 检查文件类型
        if (!fileUtil.isAllowedResourceType(file.getOriginalFilename())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "不支持的资源格式，支持的格式: " + String.join(", ", fileUploadConfig.getAllowedResourceTypes()));
        }

        // 检查文件大小
        if (!fileUtil.isValidFileSize(file.getSize(), fileUploadConfig.getMaxFileSize())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "资源文件大小不能超过 " + (fileUploadConfig.getMaxFileSize() / 1024 / 1024) + "MB");
        }
    }

    /**
     * 查询草稿附件列表
     *
     * @param draftKey 草稿关联键
     * @param userId 用户ID
     * @return 附件列表
     */
    public java.util.List<java.util.Map<String, Object>> getDraftAttachments(String draftKey, Long userId) {
        log.debug("查询草稿附件 - 用户ID: {}, 草稿键: {}", userId, draftKey);

        // 验证用户
        validateUser(userId);

        // 查询草稿附件
        return postAttachmentsMapper.selectDraftAttachments(draftKey, userId);
    }

    /**
     * 查询文章附件列表
     *
     * @param postId 文章ID
     * @param userId 用户ID
     * @return 附件列表
     */
    public java.util.List<java.util.Map<String, Object>> getPostAttachments(Long postId, Long userId) {
        log.debug("查询文章附件 - 用户ID: {}, 文章ID: {}", userId, postId);

        // 验证用户
        validateUser(userId);

        // 查询文章附件
        return postAttachmentsMapper.selectPostAttachments(postId, userId);
    }

    /**
     * 创建外部链接资源（不包含文件上传）
     *
     * @param name 资源名称
     * @param description 资源描述
     * @param externalLink 外部链接
     * @param purchasedNote 购买后说明
     * @param userId 用户ID
     * @param draftKey 草稿键（可选）
     * @param type 附件类型（可选）
     * @param downloadType 下载类型
     * @param pointsNeeded 所需积分
     * @return 上传结果
     */
    @Transactional(rollbackFor = Exception.class)
    public FileUploadResp createExternalLinkResource(String name, String description, String externalLink, String purchasedNote,
                                                       Long userId, String draftKey, String type, Integer downloadType, Integer pointsNeeded) {
        log.debug("创建外部链接资源 - 用户ID: {}, 名称: {}, 链接: {}, 草稿键: {}, 类型: {}",
                userId, name, externalLink, draftKey, type);

        // 验证用户是否存在
        validateUser(userId);

        // 验证参数
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源名称不能为空");
        }

        if (externalLink == null || externalLink.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "外部链接不能为空");
        }

        // 创建资源记录
        Resources resource = new Resources();
        resource.setName(name);
        resource.setDescription(description);
        resource.setFileUrl(null); // 外部链接类型没有文件
        resource.setExternalLink(externalLink);
        resource.setResourceType("link"); // 外部链接类型
        resource.setPurchasedNote(purchasedNote);
        resource.setUploaderId(userId);
        int normalizedDownloadType = normalizeDownloadType(downloadType);
        resource.setDownloadType(normalizedDownloadType);
        resource.setPointsNeeded(normalizePointsNeeded(normalizedDownloadType, pointsNeeded));

        // 保存到数据库
        resourcesMapper.insert(resource);
        Long resourceId = resource.getId();

        Long attachmentId = null;
        // 如果提供了草稿键，创建附件关联记录
        if (draftKey != null && !draftKey.trim().isEmpty()) {
            PostAttachments attachment = new PostAttachments();
            attachment.setDraftKey(draftKey);
            attachment.setResourceId(resourceId);
            attachment.setType(type != null ? type : "resource");

            postAttachmentsMapper.insert(attachment);
            attachmentId = attachment.getId();

            log.debug("创建草稿附件关联 - 草稿键: {}, 资源ID: {}, 附件ID: {}, 类型: {}",
                    draftKey, resourceId, attachmentId, type);
        }

        // 构建响应
        FileUploadResp result = new FileUploadResp();
        result.setFileName(name);
        result.setFilePath(null);
        result.setFileUrl(externalLink);
        result.setFileSize(0L);
        result.setFileType("resource");
        result.setExtension(null);
        result.setUploadTime(System.currentTimeMillis());
        result.setResourceId(resourceId);
        result.setAttachmentId(attachmentId);

        log.debug("外部链接资源创建成功 - 用户ID: {}, 资源ID: {}, 附件ID: {}", userId, resourceId, attachmentId);

        return result;
    }

    // 更新资源元信息（下载类型、所需积分）
    @Transactional(rollbackFor = Exception.class)
    public void updateResourceMeta(Long resourceId, Long userId, Integer downloadType, Integer pointsNeeded) {
        log.debug("更新资源元信息 - 用户ID: {}, 资源ID: {}, downloadType: {}, pointsNeeded: {}", userId, resourceId, downloadType, pointsNeeded);

        // 校验用户
        validateUser(userId);

        // 查询资源
        Resources resource = resourcesMapper.selectById(resourceId);
        if (resource == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "资源不存在");
        }

        // 权限校验：必须是上传者本人
        if (resource.getUploaderId() == null || !resource.getUploaderId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权修改该资源");
        }

        // 合法性校验与赋值
        int normalizedDownloadType = normalizeDownloadType(downloadType);
        resource.setDownloadType(normalizedDownloadType);
        resource.setPointsNeeded(normalizePointsNeeded(normalizedDownloadType, pointsNeeded));

        // 更新
        resourcesMapper.updateById(resource);
    }

    private int normalizeDownloadType(Integer downloadType) {
        if (downloadType == null) {
            return Resources.DOWNLOAD_TYPE_FREE;
        }
        if (downloadType != Resources.DOWNLOAD_TYPE_FREE && downloadType != Resources.DOWNLOAD_TYPE_PAID) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "下载类型参数错误");
        }
        return downloadType;
    }

    private BigDecimal normalizePointsNeeded(int downloadType, Integer pointsNeeded) {
        if (downloadType == Resources.DOWNLOAD_TYPE_FREE) {
            return BigDecimal.ZERO;
        }
        int normalizedPoints = pointsNeeded == null ? 1 : Math.max(1, pointsNeeded);
        return new BigDecimal(normalizedPoints);
    }

    /**
     * 更新外部链接资源的说明信息
     *
     * @param resourceId 资源ID
     * @param userId 用户ID
     * @param purchasedNote 购买后说明
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePurchasedNote(Long resourceId, Long userId, String purchasedNote) {
        log.debug("更新购买后说明 - 用户ID: {}, 资源ID: {}", userId, resourceId);

        // 校验用户
        validateUser(userId);

        // 查询资源
        Resources resource = resourcesMapper.selectById(resourceId);
        if (resource == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "资源不存在");
        }

        // 权限校验：必须是上传者本人
        if (resource.getUploaderId() == null || !resource.getUploaderId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权修改该资源");
        }

        resource.setPurchasedNote(purchasedNote);
        resourcesMapper.updateById(resource);
    }

    /**
     * 删除附件
     *
     * @param resourceId 资源ID
     * @param userId 用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAttachment(Long resourceId, Long userId) {
        log.debug("删除附件 - 用户ID: {}, 资源ID: {}", userId, resourceId);

        // 验证用户
        validateUser(userId);

        // 查询资源信息
        Resources resource = resourcesMapper.selectById(resourceId);
        if (resource == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件不存在");
        }

        // 验证权限（只能删除自己上传的附件）
        if (!resource.getUploaderId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限删除此附件");
        }

        try {
            // 删除物理文件
            if (resource.getFileUrl() != null) {
                String relativePath = fileUtil.extractRelativePath(resource.getFileUrl());
                if (relativePath != null) {
                    fileStorage.delete(relativePath);
                }
            }

            // 删除数据库记录
            resourcesMapper.deleteById(resourceId);

            // 删除附件关联记录
            postAttachmentsMapper.deleteByResourceId(resourceId);

            log.debug("附件删除成功 - 用户ID: {}, 资源ID: {}", userId, resourceId);

        } catch (Exception e) {
            log.error("删除附件失败 - 用户ID: {}, 资源ID: {}", userId, resourceId, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "删除附件失败: " + e.getMessage());
        }
    }
}
