package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 缓存管理控制器
 * 提供管理员手动清除缓存的能力，用于直接改库等绕过 Service 层的场景
 *
 * @author 刘鑫
 */
@RestController
@RequestMapping("/admin/cache")
@PreAuthorize("hasRole('ADMIN')")
public class CacheAdminController extends BaseAdminController {

    @Autowired
    private CacheManager cacheManager;

    /**
     * 清除所有缓存
     */
    @PostMapping("/clear")
    public Result<Map<String, Object>> clearAllCaches() {
        int cleared = 0;
        for (String name : cacheManager.getCacheNames()) {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
                cleared++;
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("clearedCaches", cleared);
        data.put("cacheNames", cacheManager.getCacheNames());
        return Result.success(data);
    }

    /**
     * 清除指定缓存
     * @param name 缓存名称（如 postList、hotTags、categories 等）
     */
    @PostMapping("/clear/{name}")
    public Result<String> clearCache(@PathVariable String name) {
        var cache = cacheManager.getCache(name);
        if (cache == null) {
            return Result.fail(chat.liuxin.liutech.common.ErrorCode.PARAMS_ERROR,
                    "缓存 '" + name + "' 不存在");
        }
        cache.clear();
        return Result.success("缓存 '" + name + "' 已清除");
    }

    /**
     * 查看所有缓存名称
     */
    @GetMapping("/names")
    public Result<Iterable<String>> getCacheNames() {
        return Result.success(cacheManager.getCacheNames());
    }
}
