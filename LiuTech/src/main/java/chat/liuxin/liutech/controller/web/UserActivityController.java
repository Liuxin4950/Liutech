package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.common.*;
import chat.liuxin.liutech.resp.*;
import chat.liuxin.liutech.service.*;
import chat.liuxin.liutech.utils.UserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserActivityController {
    private final UserAchievementService achievementService;
    private final UserActivityService activityService;
    private final UserUtils userUtils;
    private Long currentUserId() {
        Long id = userUtils.getCurrentUserId();
        if (id == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return id;
    }
    @GetMapping("/achievements")
    public Result<List<AchievementResp>> achievements() { return Result.success(achievementService.list(currentUserId())); }
    @PostMapping("/achievements/{code}/claim")
    public Result<AchievementClaimResp> claim(@PathVariable String code) { return Result.success(achievementService.claim(currentUserId(), code)); }
    @GetMapping("/activities")
    public Result<PageResp<UserActivityResp>> activities(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return Result.success(activityService.list(currentUserId(), page, size));
    }
}
