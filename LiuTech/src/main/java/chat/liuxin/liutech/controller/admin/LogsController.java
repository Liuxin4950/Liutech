package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.LogResp;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理端操作日志控制器
 * 提供系统操作日志查询接口
 *
 * @author 刘鑫
 * @note 当前为简化实现，后续可扩展为完整的操作日志系统
 */
@RestController
@RequestMapping("/admin/logs")
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasRole('ADMIN')")
public class LogsController extends BaseAdminController {

    /**
     * 分页查询操作日志列表
     *
     * @param page 页码，默认1
     * @param size 每页大小，默认10
     * @param operator 操作人（可选）
     * @param action 操作类型（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @return 分页操作日志列表
     */
    @GetMapping
    public Result<PageResp<LogResp>> getLogList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        try {
            // 验证分页参数
            if (page < 1) page = 1;
            if (size < 1) size = 10;
            if (size > 100) size = 100;

            // 获取日志列表（当前为模拟数据）
            List<LogResp> logs = getMockLogs(page, size, operator, action);

            // 查询总数（当前为模拟数据）
            long total = getMockLogCount(operator, action);

            // 构建分页结果
            PageResp<LogResp> result = new PageResp<>();
            result.setRecords(logs);
            result.setTotal(total);
            result.setCurrent((long) page);
            result.setSize((long) size);
            result.setPages((long) Math.ceil((double) total / size));
            result.setHasNext((long) page < result.getPages());
            result.setHasPrevious((long) page > 1);

            return Result.success(result);
        } catch (Exception e) {
            return handleException(e, "查询操作日志列表");
        }
    }

    /**
     * 获取日志详情
     *
     * @param id 日志ID
     * @return 日志详情
     */
    @GetMapping("/{id}")
    public Result<LogResp> getLogById(@PathVariable Long id) {
        try {
            LogResp log = LogResp.builder()
                    .id(id)
                    .operator("admin")
                    .action("query")
                    .target("日志查询")
                    .description("查询日志详情")
                    .ip("127.0.0.1")
                    .createdAt("2025-01-30 12:00:00")
                    .build();

            return Result.success(log);
        } catch (Exception e) {
            return handleException(e, "查询日志详情");
        }
    }

    /**
     * 获取操作类型列表
     *
     * @return 操作类型列表
     */
    @GetMapping("/actions")
    public Result<List<String>> getActionTypes() {
        List<String> actions = new ArrayList<>();
        actions.add("登录");
        actions.add("登出");
        actions.add("创建");
        actions.add("更新");
        actions.add("删除");
        actions.add("恢复");
        actions.add("发布");
        actions.add("下线");
        actions.add("批量操作");
        actions.add("系统设置");
        return Result.success(actions);
    }

    /**
     * 获取模拟日志数据
     */
    private List<LogResp> getMockLogs(int page, int size, String operator, String action) {
        List<LogResp> logs = new ArrayList<>();

        String[] actions = {"登录", "创建文章", "更新文章", "删除文章", "恢复文章", "发布文章", "更新用户", "删除用户", "创建分类", "创建标签"};
        String[] targets = {"系统", "文章管理", "用户管理", "分类管理", "标签管理"};
        String[] descriptions = {
                "管理员登录系统",
                "创建了新文章",
                "更新了文章内容",
                "删除了文章",
                "恢复了已删除的文章",
                "发布了文章",
                "更新了用户信息",
                "删除了用户",
                "创建了新分类",
                "创建了新标签"
        };

        int offset = (page - 1) * size;
        for (int i = 0; i < size; i++) {
            int idx = (offset + i) % actions.length;
            int hour = 8 + (offset + i) % 12;
            logs.add(LogResp.builder()
                    .id((long) (offset + i + 1))
                    .operator("admin")
                    .action(actions[idx])
                    .target(targets[idx % targets.length])
                    .description(descriptions[idx])
                    .ip("192.168.1." + (100 + (offset + i) % 50))
                    .createdAt(String.format("2025-01-%02d %02d:30:00", 1 + (offset + i) % 29, hour))
                    .build());
        }

        return logs;
    }

    /**
     * 获取模拟日志总数
     */
    private long getMockLogCount(String operator, String action) {
        return 100L; // 模拟总共100条日志
    }
}
