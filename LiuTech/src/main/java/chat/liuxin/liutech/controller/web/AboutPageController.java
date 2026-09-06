package chat.liuxin.liutech.controller.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.AboutPageResp;
import chat.liuxin.liutech.service.AboutPageService;
import lombok.RequiredArgsConstructor;

/**
 * 关于页公开接口。
 */
@RestController
@RequestMapping("/about")
@RequiredArgsConstructor
public class AboutPageController {

    private final AboutPageService aboutPageService;

    @GetMapping
    public Result<AboutPageResp> getAboutPage() {
        return Result.success(aboutPageService.getAboutPage());
    }
}
