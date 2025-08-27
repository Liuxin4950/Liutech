package chat.liuxin.liutech.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 缓存配置类
 * 使用Spring内置的ConcurrentMapCacheManager实现简单的内存缓存
 * 
 * @author liuxin
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 配置缓存管理器
     * 使用ConcurrentMapCacheManager实现内存缓存
     * 
     * @return CacheManager
     */
    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        
        // 预定义缓存名称
        cacheManager.setCacheNames(java.util.Arrays.asList(
            "hotPosts",        // 热门文章缓存
            "latestPosts",     // 最新文章缓存
            "hotTags",         // 热门标签缓存
            "categories",      // 分类列表缓存
            "announcements",   // 公告列表缓存
            "userStats"        // 用户统计缓存
        ));
        
        // 允许空值缓存
        cacheManager.setAllowNullValues(true);
        
        return cacheManager;
    }
}