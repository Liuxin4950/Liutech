package chat.liuxin.liutech.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 缓存配置类
 * 使用 Caffeine 替代 ConcurrentMapCacheManager，支持 TTL 自动过期
 *
 * TTL 策略：
 * - postList / hotPosts / latestPosts: 5 分钟（文章内容变更频繁）
 * - hotTags / allTags: 10 分钟
 * - categories: 15 分钟（分类很少变动）
 * - postSeries: 15 分钟（系列很少变动）
 * - announcements: 10 分钟
 * - userStats / aboutPage: 10 分钟
 *
 * @author 刘鑫
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(Arrays.asList(
                // 文章相关 — 5 分钟过期，最多 200 条
                buildCache("postList", 5, 200),
                buildCache("hotPosts", 5, 50),
                buildCache("latestPosts", 5, 50),
                // 标签 — 10 分钟，最多 100 条
                buildCache("hotTags", 10, 100),
                buildCache("allTags", 10, 10),
                // 分类 — 15 分钟，变动极少
                buildCache("categories", 15, 50),
                // 系列 - 15 分钟，变动少
                buildCache("postSeries", 15, 50),
                // 公告 — 10 分钟
                buildCache("announcements", 10, 50),
                // 用户统计与关于页 — 10 分钟
                buildCache("userStats", 10, 20),
                buildCache("aboutPage", 10, 2)
        ));
        return cacheManager;
    }

    /**
     * 构建单个 Caffeine 缓存实例
     *
     * @param name        缓存名称
     * @param ttlMinutes  过期时间（分钟）
     * @param maxSize     最大条目数
     */
    private CaffeineCache buildCache(String name, int ttlMinutes, int maxSize) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumSize(maxSize)
                .recordStats()
                .build());
    }
}
