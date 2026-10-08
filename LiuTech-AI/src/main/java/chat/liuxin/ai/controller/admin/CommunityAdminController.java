package chat.liuxin.ai.controller.admin;

import chat.liuxin.ai.dto.community.CommunityPreviewRequest;
import chat.liuxin.ai.service.community.CommunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** 非流式管理响应沿用现有 AI success/message/code 约定。 */
@RestController
@RequestMapping("/ai/admin/community")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@org.springframework.validation.annotation.Validated
public class CommunityAdminController {
    private final CommunityService community;
    @PostMapping("/preview") public Map<String,Object> preview(@Valid @RequestBody CommunityPreviewRequest request) {
        return success(community.preview(request));
    }
    @GetMapping("/runs") public Map<String,Object> runs(@RequestParam(required=false) @jakarta.validation.constraints.Positive Long botId,
        @RequestParam(required=false) @jakarta.validation.constraints.Positive Long postId,@RequestParam(defaultValue="50") int limit) {
        return success(community.runs(botId,postId,limit));
    }
    @GetMapping("/tasks") public Map<String,Object> tasks(@RequestParam(required=false) @jakarta.validation.constraints.Positive Long botId,
        @RequestParam(required=false) @jakarta.validation.constraints.Positive Long postId,
        @RequestParam(required=false) String status,@RequestParam(defaultValue="50") int limit) {
        return success(community.tasks(botId,postId,status,limit));
    }
    @PostMapping("/tasks/{taskId}/retry") public Map<String,Object> retryTask(@PathVariable @jakarta.validation.constraints.Pattern(regexp="[0-9a-fA-F-]{36}") String taskId) {
        return success(community.retryTask(taskId));
    }
    @PostMapping("/tasks/{taskId}/cancel") public Map<String,Object> cancelTask(@PathVariable @jakarta.validation.constraints.Pattern(regexp="[0-9a-fA-F-]{36}") String taskId) {
        return success(community.cancelTask(taskId));
    }
    @GetMapping("/memory") public Map<String,Object> memory(@RequestParam @jakarta.validation.constraints.Positive Long botId) { return success(community.memories(botId)); }
    @DeleteMapping("/memory/{botId}") public Map<String,Object> clearMemory(@PathVariable @jakarta.validation.constraints.Positive long botId) {
        community.clearMemory(botId);return success(Map.of());
    }
    private Map<String,Object> success(Object data) { return Map.of("success",true,"message","成功","code",200,"data",data); }
}
