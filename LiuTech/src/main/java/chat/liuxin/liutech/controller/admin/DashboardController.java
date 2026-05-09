package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.DashboardResp;
import chat.liuxin.liutech.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
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
public class DashboardController extends BaseAdminController {

    @Autowired
    private DashboardService dashboardService;

    /**
     * 获取仪表盘统计数据
     *
     * @return 仪表盘统计数据
     */
    @GetMapping("/stats")
    public Result<DashboardResp> getDashboardStats() {
        try {
            DashboardResp resp = dashboardService.getDashboardStats();
            return Result.success(resp);
        } catch (Exception e) {
            return handleException(e, "获取仪表盘统计数据");
        }
    }
}
