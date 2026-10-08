package chat.liuxin.liutech.controller.internal;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.CommunityEvent;
import chat.liuxin.liutech.req.CommunityReq;
import chat.liuxin.liutech.resp.CommunityResp;
import chat.liuxin.liutech.service.CommunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 仅受 InternalServiceTokenFilter 保护；不要求真人 JWT，不建立管理员身份。 */
@RestController
@RequestMapping("/internal/community")
@RequiredArgsConstructor
public class CommunityInternalController {
    private final CommunityService service;
    @GetMapping("/context") public Result<CommunityResp.Context> context(@RequestParam Long botId,@RequestParam Long postId,
            @RequestParam(required=false) Long commentId) { return Result.success(service.context(botId,postId,commentId)); }
    @PostMapping("/events/claim") public Result<List<CommunityEvent>> claim(@Valid @RequestBody CommunityReq.Claim req) { return Result.success(service.claim(req)); }
    @PostMapping("/events/{id}/ack") public Result<Void> ack(@PathVariable Long id,@Valid @RequestBody CommunityReq.Ack req) { service.ack(id,req.leaseToken()); return Result.success(); }
    @GetMapping("/events/{id}") public Result<CommunityEvent> event(@PathVariable Long id) { return Result.success(service.eventForWorker(id)); }
    @PostMapping("/tasks/cancel") public Result<CommunityResp.Cancelled> cancel(@Valid @RequestBody CommunityReq.Cancel req) { return Result.success(service.cancelTask(req)); }
    @PostMapping("/attempts") public Result<CommunityResp.Attempt> attempt(@Valid @RequestBody CommunityReq.Attempt req) { return Result.success(service.authorizeAttempt(req)); }
    @PostMapping("/comments") public Result<CommunityResp.Published> publish(@Valid @RequestBody CommunityReq.Publish req) { return Result.success(service.publish(req)); }
    @PostMapping("/visibility") public Result<CommunityResp.Visibility> visibility(@Valid @RequestBody CommunityReq.Visibility req) { return Result.success(service.visibility(req)); }
    @PostMapping("/metadata") public Result<CommunityResp.Metadata> metadata(@Valid @RequestBody CommunityReq.Metadata req) { return Result.success(service.metadata(req)); }
}
