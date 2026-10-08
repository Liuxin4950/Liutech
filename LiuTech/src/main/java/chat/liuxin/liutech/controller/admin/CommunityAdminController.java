package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.*;
import chat.liuxin.liutech.req.CommunityReq;
import chat.liuxin.liutech.resp.CommunityResp;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.service.CommunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/admin/community")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CommunityAdminController {
    private final CommunityService service;
    @GetMapping("/bots") public Result<List<CommunityBot>> bots() { return Result.success(service.bots()); }
    @GetMapping("/bots/{id}/comments")
    public Result<PageResp<Comments>> comments(@PathVariable Long id,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        return Result.success(service.botCommentsForAdmin(id,page,size));
    }
    @GetMapping("/comments/{commentId}/thread")
    public Result<CommunityResp.Thread> thread(@PathVariable Long commentId) {
        return Result.success(service.commentThreadForAdmin(commentId));
    }
    @DeleteMapping("/comments/{commentId}") @OperationLog(action="delete",targetType="community-comment",description="撤回 AI 评论及回复分支")
    public Result<Void> withdraw(@PathVariable Long commentId) {
        service.withdrawComment(commentId);return Result.success();
    }
    @GetMapping("/events")
    public Result<List<CommunityEvent>> pendingEvents(@RequestParam(required=false) Long botId,
        @RequestParam(required=false) Long postId) {
        return Result.success(service.pendingEvents(botId,postId));
    }
    @DeleteMapping("/events/{eventId}") @OperationLog(action="delete",targetType="community-event",description="取消社区待派发事件")
    public Result<CommunityResp.Cancelled> cancelEvent(@PathVariable Long eventId) {
        return Result.success(service.cancelPendingEvent(eventId));
    }
    @PostMapping("/bots") @OperationLog(action="create",targetType="community-bot",description="创建社区角色")
    public Result<CommunityBot> create(@Valid @RequestBody CommunityReq.Bot req) { return Result.success(service.saveBot(null,req)); }
    @PutMapping("/bots/{id}") @OperationLog(action="update",targetType="community-bot",description="修改社区角色")
    public Result<CommunityBot> update(@PathVariable Long id,@Valid @RequestBody CommunityReq.Bot req) { return Result.success(service.saveBot(id,req)); }
    @DeleteMapping("/bots/{id}") @OperationLog(action="delete",targetType="community-bot",description="删除社区角色")
    public Result<Void> delete(@PathVariable Long id) { service.deleteBot(id); return Result.success(); }
    @GetMapping("/bots/{id}/knowledge") public Result<List<CommunityKnowledge>> knowledge(@PathVariable Long id) { return Result.success(service.knowledge(id)); }
    @PostMapping("/bots/{id}/knowledge") @OperationLog(action="create",targetType="community-knowledge",description="创建角色资料")
    public Result<CommunityKnowledge> addKnowledge(@PathVariable Long id,@Valid @RequestBody CommunityReq.Knowledge req) { return Result.success(service.saveKnowledge(id,null,req)); }
    @PutMapping("/bots/{id}/knowledge/{knowledgeId}") @OperationLog(action="update",targetType="community-knowledge",description="修改角色资料")
    public Result<CommunityKnowledge> updateKnowledge(@PathVariable Long id,@PathVariable Long knowledgeId,@Valid @RequestBody CommunityReq.Knowledge req) { return Result.success(service.saveKnowledge(id,knowledgeId,req)); }
    @DeleteMapping("/bots/{id}/knowledge/{knowledgeId}") @OperationLog(action="delete",targetType="community-knowledge",description="删除角色资料")
    public Result<Void> deleteKnowledge(@PathVariable Long id,@PathVariable Long knowledgeId) { service.deleteKnowledge(id,knowledgeId); return Result.success(); }
    @GetMapping("/settings") public Result<CommunitySettings> settings() { return Result.success(service.settings()); }
    @PutMapping("/settings") @OperationLog(action="update",targetType="community-settings",description="修改社区互动设置")
    public Result<CommunitySettings> settings(@Valid @RequestBody CommunityReq.Settings req) { return Result.success(service.saveSettings(req)); }
    @GetMapping("/posts/{id}/enabled")
    public Result<CommunityReq.Enabled> enabled(@PathVariable Long id) { return Result.success(new CommunityReq.Enabled(service.postEnabled(id))); }
    @PutMapping("/posts/{id}/enabled") @OperationLog(action="update",targetType="community-post",description="修改文章社区开关")
    public Result<Void> enabled(@PathVariable Long id,@Valid @RequestBody CommunityReq.Enabled req) { service.setPostEnabled(id,req.enabled()); return Result.success(); }
    @PostMapping("/posts/{id}/invite") @OperationLog(action="create",targetType="community-post",description="邀请角色评论文章")
    public Result<CommunityResp.Queued> invite(@PathVariable Long id,@Valid @RequestBody CommunityReq.Invite req) {
        return Result.success(service.invite(id,req.botIds()));
    }
    @PostMapping("/backfill") @OperationLog(action="create",targetType="community-post",description="为近期公开文章补评")
    public Result<CommunityResp.Backfill> backfill(@Valid @RequestBody CommunityReq.Backfill req) {
        return Result.success(service.backfill(req));
    }
}
