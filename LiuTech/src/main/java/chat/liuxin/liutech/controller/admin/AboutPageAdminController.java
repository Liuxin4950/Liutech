package chat.liuxin.liutech.controller.admin;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.req.AboutPageReq;
import chat.liuxin.liutech.resp.AboutPageResp;
import chat.liuxin.liutech.service.AboutPageService;
import lombok.RequiredArgsConstructor;

/**
 * 关于页管理接口。
 */
@RestController
@RequestMapping("/admin/about")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AboutPageAdminController {

    private final AboutPageService aboutPageService;

    @GetMapping
    public Result<AboutPageResp> getAboutPage() {
        return Result.success(aboutPageService.getAboutPage());
    }

    @PutMapping
    @OperationLog(action = "update", targetType = "about_page", description = "更新关于页")
    public Result<AboutPageResp> updateAboutPage(@Valid @RequestBody AboutPageReq req) {
        return Result.success("关于页已更新", aboutPageService.updateAboutPage(req));
    }
}
