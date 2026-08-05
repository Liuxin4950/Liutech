package chat.liuxin.liutech.service;

import chat.liuxin.liutech.utils.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 图片引用计数服务
 * 统一负责图片引用计数的增减与对账统计，业务方（文章/用户头像/音乐封面等）只传 URL 列表：
 * - 新增引用：addReferences
 * - 移除引用：removeReferences
 * - 编辑替换：syncReferences（按旧/新差量同步）
 * - 对账重算：countByPath（URL 列表 → 逻辑路径 → 次数）
 * <p>
 * 路径口径统一走 {@link FileUtil#normalizeToRelativePath}，杜绝各处各自实现导致计数不一致。
 *
 * @author 刘鑫
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageReferenceService {

    private final ImagesService imagesService;

    private final FileUtil fileUtil;

    /**
     * 新增一批图片引用（每 URL 计 1 次，同一 URL 出现多次则累加）
     *
     * @param urls 图片 URL 列表
     */
    public void addReferences(List<String> urls) {
        applyDelta(countUrls(urls));
    }

    /**
     * 移除一批图片引用
     *
     * @param urls 图片 URL 列表
     */
    public void removeReferences(List<String> urls) {
        Map<String, Integer> counts = countUrls(urls);
        Map<String, Integer> negative = new HashMap<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            negative.put(entry.getKey(), -entry.getValue());
        }
        applyDelta(negative);
    }

    /**
     * 编辑场景：按旧引用与新引用的差量同步计数
     *
     * @param oldUrls 编辑前引用列表
     * @param newUrls 编辑后引用列表
     */
    public void syncReferences(List<String> oldUrls, List<String> newUrls) {
        Map<String, Integer> oldCounts = countUrls(oldUrls);
        Map<String, Integer> newCounts = countUrls(newUrls);
        Set<String> allUrls = new HashSet<>(oldCounts.keySet());
        allUrls.addAll(newCounts.keySet());

        Map<String, Integer> deltas = new HashMap<>();
        for (String url : allUrls) {
            int delta = newCounts.getOrDefault(url, 0) - oldCounts.getOrDefault(url, 0);
            if (delta != 0) {
                deltas.put(url, delta);
            }
        }
        applyDelta(deltas);
    }

    /**
     * 对账统计：URL 列表 → 逻辑路径 → 出现次数（按路径归并，同一路径的多种 URL 写法算同一图片）
     *
     * @param urls URL 列表
     * @return 逻辑路径 → 次数
     */
    public Map<String, Integer> countByPath(List<String> urls) {
        Map<String, Integer> counts = new HashMap<>();
        if (urls == null || urls.isEmpty()) {
            return counts;
        }
        for (String url : urls) {
            if (url == null || url.trim().isEmpty()) {
                continue;
            }
            String path = fileUtil.normalizeToRelativePath(url);
            if (path == null) {
                continue;
            }
            counts.merge(path, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * 按 URL 原文计数（同一 URL 多次出现则累加）
     */
    private Map<String, Integer> countUrls(List<String> urls) {
        Map<String, Integer> counts = new HashMap<>();
        if (urls == null || urls.isEmpty()) {
            return counts;
        }
        for (String url : urls) {
            if (url == null || url.trim().isEmpty()) {
                continue;
            }
            counts.merge(url, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * 应用带符号的引用计数增量（负值表示减少）
     */
    private void applyDelta(Map<String, Integer> deltas) {
        if (deltas == null || deltas.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Integer> entry : deltas.entrySet()) {
            Integer delta = entry.getValue();
            if (delta == null || delta == 0) {
                continue;
            }
            imagesService.incrementImageUsageCountByUrl(entry.getKey(), delta);
        }
    }
}
