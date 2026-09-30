package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.HomeDashboardResp;
import chat.liuxin.liutech.resp.SiteStatsResp;
import chat.liuxin.liutech.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class HomeController {
    private final HomeService homeService;

    @GetMapping
    public Result<String> home() {
        return Result.success("欢迎访问刘鑫的技术博客！", "欢迎访问刘鑫的技术博客！");
    }

    @GetMapping("/dashboard")
    public Result<HomeDashboardResp> getDashboardData() {
        return Result.success("获取成功", homeService.getDashboardData());
    }

    @GetMapping("/stats")
    public Result<SiteStatsResp> getStats() {
        return Result.success("获取成功", homeService.getStats());
    }
}
