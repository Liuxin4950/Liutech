package chat.liuxin.liutech.controller.admin;

import lombok.RequiredArgsConstructor;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.DashboardResp;
import chat.liuxin.liutech.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端仪表盘控制器
 * 提供仪表盘统计数据接口
 *
 * @author 刘鑫
 */
@RestController
@RequestMapping("/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class DashboardController extends BaseAdminController {

    private final DashboardService dashboardService;

    /**
     * 获取仪表盘统计数据
     *
     * @return 仪表盘统计数据
     */
    @GetMapping("/stats")
    public Result<DashboardResp> getDashboardStats() {
        return Result.success(dashboardService.getDashboardStats());
    }
}
