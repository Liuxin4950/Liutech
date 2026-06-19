package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.CarouselMapper;
import chat.liuxin.liutech.mapper.ImagesMapper;
import chat.liuxin.liutech.mapper.MusicMapper;
import chat.liuxin.liutech.mapper.PostsMapper;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.Images;
import chat.liuxin.liutech.model.Posts;
import chat.liuxin.liutech.resp.ImageUsageReconcileResp;
import chat.liuxin.liutech.utils.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageUsageReconcileService {

    private final ImagesMapper imagesMapper;

    private final PostsMapper postsMapper;

    private final UserMapper userMapper;

    private final CarouselMapper carouselMapper;

    private final MusicMapper musicMapper;

    private final FileUtil fileUtil;

    @Transactional(rollbackFor = Exception.class)
    public ImageUsageReconcileResp reconcileUsageCount() {
        int resetRows = valueOrZero(imagesMapper.resetUsageCount());

        Map<String, Integer> countsByPath = new HashMap<>();
        addUrls(countsByPath, userMapper.selectAllAvatarUrls());
        addUrls(countsByPath, musicMapper.selectAllCoverUrls());
        addUrls(countsByPath, carouselMapper.selectAllImageUrls());

        List<Posts> posts = postsMapper.selectAllPostsWithContent();
        for (Posts post : posts) {
            if (post == null) {
                continue;
            }
            addUrl(countsByPath, post.getCoverImage());
            addUrl(countsByPath, post.getThumbnail());
            List<String> contentUrls = fileUtil.extractImageUrls(post.getContent());
            addUrls(countsByPath, contentUrls);
        }

        List<String> paths = new ArrayList<>(countsByPath.keySet());
        int updatedImages = 0;
        int missingImages = 0;

        int batchSize = 500;
        for (int start = 0; start < paths.size(); start += batchSize) {
            int end = Math.min(start + batchSize, paths.size());
            List<String> batch = paths.subList(start, end);

            LambdaQueryWrapper<Images> query = new LambdaQueryWrapper<>();
            query.in(Images::getFilePath, batch)
                    .isNull(Images::getDeletedAt)
                    .eq(Images::getStatus, 1);
            List<Images> images = imagesMapper.selectList(query);

            Map<String, Images> imageByPath = new HashMap<>();
            for (Images img : images) {
                if (img != null && StringUtils.hasText(img.getFilePath())) {
                    imageByPath.put(img.getFilePath(), img);
                }
            }

            for (String path : batch) {
                Images img = imageByPath.get(path);
                if (img == null) {
                    missingImages++;
                    continue;
                }
                Integer count = countsByPath.get(path);
                if (count == null || count <= 0) {
                    continue;
                }
                imagesMapper.incrementUsageCount(img.getId(), count);
                updatedImages++;
            }
        }

        log.info("图片 usage_count 对账完成：resetRows={}, referencedPaths={}, updatedImages={}, missingImages={}",
                resetRows, countsByPath.size(), updatedImages, missingImages);

        return new ImageUsageReconcileResp(resetRows, countsByPath.size(), updatedImages, missingImages);
    }

    private void addUrls(Map<String, Integer> countsByPath, List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        for (String url : urls) {
            addUrl(countsByPath, url);
        }
    }

    private void addUrl(Map<String, Integer> countsByPath, String url) {
        String path = normalizeToRelativePath(url);
        if (!StringUtils.hasText(path)) {
            return;
        }
        countsByPath.merge(path, 1, (oldValue, delta) ->
                (oldValue == null ? 0 : oldValue) + (delta == null ? 0 : delta));
    }

    private String normalizeToRelativePath(String fileUrlOrRelativePath) {
        if (!StringUtils.hasText(fileUrlOrRelativePath)) {
            return null;
        }

        String relativePath = fileUtil.extractRelativePath(fileUrlOrRelativePath);
        if (StringUtils.hasText(relativePath)) {
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
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}

