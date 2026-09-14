package chat.liuxin.ai.controller.internal;

import chat.liuxin.ai.dto.UserDataPurgeRequest;
import chat.liuxin.ai.dto.UserDataPurgeResult;
import chat.liuxin.ai.service.MemoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 主服务彻底删除用户前调用的 AI 数据清理接口。 */
@Slf4j
@RestController
@RequestMapping("/ai/internal/users")
@RequiredArgsConstructor
public class UserDataPurgeController {

    private final MemoryService memoryService;

    @DeleteMapping("/{userId}/data")
    public UserDataPurgeResult purgeOne(@PathVariable Long userId) {
        UserDataPurgeResult result = purge(userId);
        log.info("AI 用户数据清理完成: {}", result);
        return result;
    }

    @PostMapping("/purge")
    public List<UserDataPurgeResult> purgeBatch(@Valid @RequestBody UserDataPurgeRequest request) {
        List<UserDataPurgeResult> results = request.getUserIds().stream().distinct().map(this::purge).toList();
        log.info("AI 用户数据批量清理完成: users={}", results.size());
        return results;
    }

    private UserDataPurgeResult purge(Long userId) {
        MemoryService.PurgeCounts counts = memoryService.clearAllMemory(String.valueOf(userId));
        return new UserDataPurgeResult(userId, counts.conversationsDeleted(), counts.messagesDeleted());
    }
}
