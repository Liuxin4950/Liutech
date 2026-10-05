package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.CommunityResp;
import chat.liuxin.liutech.service.CommunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/community")
@RequiredArgsConstructor
public class CommunityController {
    private final CommunityService service;
    @GetMapping("/bots") public Result<List<CommunityResp.BotInfo>> bots() { return Result.success(service.publicBots()); }
}
