package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.ImagesMapper;
import chat.liuxin.liutech.model.Images;
import chat.liuxin.liutech.utils.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import chat.liuxin.liutech.resp.ImageUploadResult;

/**
 * 图片服务
 * 实现图片上传去重功能
 * @author 刘鑫
 * @date 2025-01-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImagesService {

    private final ImagesMapper imagesMapper;

    private final FileUtil fileUtil;

    private final ImageCompressService imageCompressService;

    /**
     * 上传图片（带去重）
     * 如果相同内容的图片已存在，则增加引用计数并返回已有URL
     *
     * @param file 上传的文件
     * @param uploaderId 上传用户ID
     * @param subPath 子路径（如：images）
     * @return 图片信息
     * @throws IOException IO异常
     */
    @Transactional(rollbackFor = Exception.class)
    public ImageUploadResult uploadImage(MultipartFile file, Long uploaderId, String subPath) throws IOException {
        String fileHash = fileUtil.calculateFileHash(file);
        log.debug("计算文件哈希: {}", fileHash);

        // 2. 查询是否已存在相同哈希的图片
        Images existingImage = imagesMapper.selectByHash(fileHash);
        if (existingImage != null) {
            log.debug("发现重复图片，哈希: {}，已有记录ID: {}", fileHash, existingImage.getId());
            return new ImageUploadResult(existingImage, true);
        }

        // 3. 压缩图片后保存
        byte[] compressedBytes = imageCompressService.compress(file.getBytes(), file.getOriginalFilename());
        String relativePath;
        long fileSize;
        if (compressedBytes != null) {
            // 压缩成功，保存压缩版
            relativePath = fileUtil.saveFile(compressedBytes, subPath, file.getOriginalFilename());
            fileSize = compressedBytes.length;
            log.debug("图片已压缩: {}KB -> {}KB", file.getSize() / 1024, fileSize / 1024);
        } else {
            // 无需压缩（GIF、小图片等），原样保存
            relativePath = fileUtil.saveFile(file, subPath);
            fileSize = file.getSize();
        }
        String fileUrl = fileUtil.generateFileUrl(relativePath);

        // 4. 创建新记录
        Images newImage = new Images();
        newImage.setFileName(file.getOriginalFilename());
        newImage.setFileUrl(fileUrl);
        newImage.setFilePath(relativePath);
        newImage.setFileHash(fileHash);
        newImage.setFileSize(fileSize);
        newImage.setMimeType(file.getContentType());
        newImage.setExtension(fileUtil.getFileExtension(file.getOriginalFilename()));
        newImage.setUploaderId(uploaderId);
        newImage.setUsageCount(0);
        newImage.setStatus(1);

        // 尝试获取图片尺寸
        try {
            int[] dimensions = getImageDimensions(file);
            if (dimensions != null) {
                newImage.setWidth(dimensions[0]);
                newImage.setHeight(dimensions[1]);
            }
        } catch (Exception e) {
            log.warn("获取图片尺寸失败: {}", e.getMessage());
        }

        imagesMapper.insert(newImage);
        log.debug("新图片保存成功，ID: {}，路径: {}", newImage.getId(), relativePath);

        return new ImageUploadResult(newImage, false);
    }

    /**
     * 增加图片引用计数
     *
     * @param imageId 图片ID
     * @param delta 增量（可为负数）
     * @return 影响行数
     */
    public int incrementUsageCount(Long imageId, int delta) {
        return imagesMapper.incrementUsageCount(imageId, delta);
    }

    /**
     * 根据ID获取图片
     *
     * @param id 图片ID
     * @return 图片记录，不存在返回null
     */
    public Images getById(Long id) {
        return imagesMapper.selectById(id);
    }

    /**
     * 根据哈希获取图片
     *
     * @param fileHash 文件哈希
     * @return 图片记录，不存在返回null
     */
    public Images getByHash(String fileHash) {
        return imagesMapper.selectByHash(fileHash);
    }

    /**
     * 根据URL获取图片记录
     *
     * @param url 图片URL
     * @return 图片记录，不存在或已删除返回null
     */
    public Images getImageByUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        String relativePath = fileUtil.extractRelativePath(url);
        if (relativePath == null) {
            return null;
        }
        LambdaQueryWrapper<Images> query = new LambdaQueryWrapper<>();
        query.eq(Images::getFilePath, relativePath)
             .isNull(Images::getDeletedAt)
             .eq(Images::getStatus, 1);
        return imagesMapper.selectOne(query);
    }

    /**
     * 根据URL增减图片引用计数
     *
     * @param url 图片URL
     * @param delta 增量（可为负数）
     * @return 影响行数
     */
    public int incrementImageUsageCountByUrl(String url, int delta) {
        String relativePath = normalizeToRelativePath(url);
        if (relativePath == null) {
            return 0;
        }
        LambdaQueryWrapper<Images> query = new LambdaQueryWrapper<>();
        query.eq(Images::getFilePath, relativePath)
                .isNull(Images::getDeletedAt)
                .eq(Images::getStatus, 1);
        Images image = imagesMapper.selectOne(query);
        if (image == null) {
            return 0;
        }
        return imagesMapper.incrementUsageCount(image.getId(), delta);
    }

    /**
     * 根据URL减少图片引用计数（便捷方法）
     *
     * @param url 图片URL
     * @return 影响行数
     */
    public int decrementImageUsageCountByUrl(String url) {
        return incrementImageUsageCountByUrl(url, -1);
    }

    private String normalizeToRelativePath(String fileUrlOrRelativePath) {
        if (fileUrlOrRelativePath == null || fileUrlOrRelativePath.isEmpty()) {
            return null;
        }
        String relativePath = fileUtil.extractRelativePath(fileUrlOrRelativePath);
        if (relativePath != null && !relativePath.isEmpty()) {
            return relativePath;
        }
        if (fileUrlOrRelativePath.contains("://")) {
            return null;
        }
        String value = fileUrlOrRelativePath.trim();
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.startsWith("uploads/")) {
            value = value.substring("uploads/".length());
        }
        return value.isEmpty() ? null : value;
    }

    /**
     * 尝试获取图片尺寸
     * 使用简单的文件头解析，不依赖额外的图片处理库
     *
     * @param file 图片文件
     * @return 宽度和高度数组，解析失败返回null
     */
    private int[] getImageDimensions(MultipartFile file) {
        try {
            byte[] header = file.getBytes();
            if (header.length < 24) {
                return null;
            }

            // PNG文件头: 89 50 4E 47 0D 0A 1A 0A
            if (header.length >= 8 && header[0] == (byte) 0x89 && header[1] == (byte) 0x50) {
                return getPngDimensions(header);
            }

            // JPEG文件头: FF D8 FF
            if (header.length >= 3 && header[0] == (byte) 0xFF && header[1] == (byte) 0xD8) {
                return getJpegDimensions(header);
            }

            // GIF文件头: 47 49 46 38
            if (header.length >= 6 && header[0] == (byte) 0x47 && header[3] == (byte) 0x38) {
                return getGifDimensions(header);
            }

            return null;
        } catch (Exception e) {
            log.debug("解析图片尺寸失败: {}", e.getMessage());
            return null;
        }
    }

    private int[] getPngDimensions(byte[] header) {
        // PNG: 宽度在第16-19字节，高度在第20-23字节（Big Endian）
        try {
            int width = ((header[16] & 0xFF) << 24) |
                        ((header[17] & 0xFF) << 16) |
                        ((header[18] & 0xFF) << 8) |
                        (header[19] & 0xFF);
            int height = ((header[20] & 0xFF) << 24) |
                         ((header[21] & 0xFF) << 16) |
                         ((header[22] & 0xFF) << 8) |
                         (header[23] & 0xFF);
            return new int[]{width, height};
        } catch (Exception e) {
            return null;
        }
    }

    private int[] getJpegDimensions(byte[] header) {
        // JPEG: 搜索FF C0或FF C2段获取SOF信息
        try {
            int pos = 2;
            while (pos < header.length - 1) {
                if (header[pos] != (byte) 0xFF) {
                    pos++;
                    continue;
                }
                int marker = header[pos + 1] & 0xFF;
                // SOF0, SOF1, SOF2 markers
                if (marker == 0xC0 || marker == 0xC1 || marker == 0xC2) {
                    // 段长度(2字节) + 精度(1字节) + 高度(2字节) + 宽度(2字节)
                    if (pos + 9 < header.length) {
                        int height = ((header[pos + 5] & 0xFF) << 8) | (header[pos + 6] & 0xFF);
                        int width = ((header[pos + 7] & 0xFF) << 8) | (header[pos + 8] & 0xFF);
                        return new int[]{width, height};
                    }
                }
                // 跳过段长度
                if (pos + 3 < header.length) {
                    int segmentLength = ((header[pos + 2] & 0xFF) << 8) | (header[pos + 3] & 0xFF);
                    pos += 2 + segmentLength;
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private int[] getGifDimensions(byte[] header) {
        // GIF: 宽度在第6-7字节（Little Endian），高度在第8-9字节
        try {
            int width = (header[6] & 0xFF) | ((header[7] & 0xFF) << 8);
            int height = (header[8] & 0xFF) | ((header[9] & 0xFF) << 8);
            return new int[]{width, height};
        } catch (Exception e) {
            return null;
        }
    }
}
