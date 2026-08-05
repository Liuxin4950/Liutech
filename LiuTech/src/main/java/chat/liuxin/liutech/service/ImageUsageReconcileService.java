package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.CarouselMapper;
import chat.liuxin.liutech.mapper.ImagesMapper;
import chat.liuxin.liutech.mapper.MusicMapper;
import chat.liuxin.liutech.mapper.PostSeriesMapper;
import chat.liuxin.liutech.mapper.PostsMapper;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.Images;
import chat.liuxin.liutech.model.Posts;
import chat.liuxin.liutech.resp.ImageUsageReconcileResp;
import chat.liuxin.liutech.utils.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
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

    private final PostSeriesMapper postSeriesMapper;

    private final FileUtil fileUtil;

    private final ImageReferenceService imageReferenceService;

    @Scheduled(cron = "${image.reconcile.cron:0 55 2 * * ?}", zone = "${image.reconcile.zone:Asia/Shanghai}")
    @Transactional(rollbackFor = Exception.class)
    public ImageUsageReconcileResp reconcileUsageCount() {
        int resetRows = valueOrZero(imagesMapper.resetUsageCount());

        // 收集所有引用图片的 URL（头像/封面/轮播/系列/文章封面缩略图正文），统一走 ImageReferenceService 统计口径
        List<String> allUrls = new ArrayList<>();
        allUrls.addAll(userMapper.selectAllAvatarUrls());
        allUrls.addAll(musicMapper.selectAllCoverUrls());
        allUrls.addAll(carouselMapper.selectAllImageUrls());
        allUrls.addAll(postSeriesMapper.selectAllCoverUrls());

        List<Posts> posts = postsMapper.selectAllPostsWithContent();
        for (Posts post : posts) {
            if (post == null) {
                continue;
            }
            if (StringUtils.hasText(post.getCoverImage())) {
                allUrls.add(post.getCoverImage());
            }
            if (StringUtils.hasText(post.getThumbnail())) {
                allUrls.add(post.getThumbnail());
            }
            allUrls.addAll(fileUtil.extractImageUrls(post.getContent()));
        }
        Map<String, Integer> countsByPath = imageReferenceService.countByPath(allUrls);

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

        log.debug("图片 usage_count 对账完成：resetRows={}, referencedPaths={}, updatedImages={}, missingImages={}",
                resetRows, countsByPath.size(), updatedImages, missingImages);

        return new ImageUsageReconcileResp(resetRows, countsByPath.size(), updatedImages, missingImages);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}

