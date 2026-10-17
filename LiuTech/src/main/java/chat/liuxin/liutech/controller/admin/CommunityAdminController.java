package chat.liuxin.liutech.controller.admin;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.CommunityBot;
import chat.liuxin.liutech.model.CommunityEvent;
import chat.liuxin.liutech.model.CommunityKnowledge;
import chat.liuxin.liutech.model.CommunitySettings;
import chat.liuxin.liutech.model.Comments;
import chat.liuxin.liutech.req.CommunityReq;
import chat.liuxin.liutech.resp.CommunityResp;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.service.CommunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 管理端社区 AI 控制器
 * 提供社区角色的配置、资料、评论对话、待派发事件与参与设置等管理接口
 */
@RestController
@RequestMapping("/admin/community")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CommunityAdminController {

    private final CommunityService communityService;

    /** 查询社区角色列表 */
    @GetMapping("/bots")
    public Result<List<CommunityBot>> bots() {
        return Result.success(communityService.bots());
    }

    /** 分页查询某角色的评论列表 */
    @GetMapping("/bots/{id}/comments")
    public Result<PageResp<Comments>> comments(@PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(communityService.botCommentsForAdmin(id, page, size));
    }

    /** 查询评论对话线程 */
    @GetMapping("/comments/{commentId}/thread")
    public Result<CommunityResp.Thread> thread(@PathVariable Long commentId) {
        return Result.success(communityService.commentThreadForAdmin(commentId));
    }

    /** 撤回 AI 评论及其回复分支 */
    @DeleteMapping("/comments/{commentId}")
    @OperationLog(action = "delete", targetType = "community-comment", description = "撤回 AI 评论及回复分支")
    public Result<Void> withdraw(@PathVariable Long commentId) {
        communityService.withdrawComment(commentId);
        return Result.success();
    }

    /** 查询待派发事件 */
    @GetMapping("/events")
    public Result<List<CommunityEvent>> pendingEvents(@RequestParam(required = false) Long botId,
            @RequestParam(required = false) Long postId) {
        return Result.success(communityService.pendingEvents(botId, postId));
    }

    /** 取消待派发事件 */
    @DeleteMapping("/events/{eventId}")
    @OperationLog(action = "delete", targetType = "community-event", description = "取消社区待派发事件")
    public Result<CommunityResp.Cancelled> cancelEvent(@PathVariable Long eventId) {
        return Result.success(communityService.cancelPendingEvent(eventId));
    }

    /** 创建社区角色 */
    @PostMapping("/bots")
    @OperationLog(action = "create", targetType = "community-bot", description = "创建社区角色")
    public Result<CommunityBot> create(@Valid @RequestBody CommunityReq.Bot req) {
        return Result.success(communityService.saveBot(null, req));
    }

    /** 修改社区角色 */
    @PutMapping("/bots/{id}")
    @OperationLog(action = "update", targetType = "community-bot", description = "修改社区角色")
    public Result<CommunityBot> update(@PathVariable Long id, @Valid @RequestBody CommunityReq.Bot req) {
        return Result.success(communityService.saveBot(id, req));
    }

    /** 删除社区角色 */
    @DeleteMapping("/bots/{id}")
    @OperationLog(action = "delete", targetType = "community-bot", description = "删除社区角色")
    public Result<Void> delete(@PathVariable Long id) {
        communityService.deleteBot(id);
        return Result.success();
    }

    /** 查询角色资料列表 */
    @GetMapping("/bots/{id}/knowledge")
    public Result<List<CommunityKnowledge>> knowledge(@PathVariable Long id) {
        return Result.success(communityService.knowledge(id));
    }

    /** 创建角色资料 */
    @PostMapping("/bots/{id}/knowledge")
    @OperationLog(action = "create", targetType = "community-knowledge", description = "创建角色资料")
    public Result<CommunityKnowledge> addKnowledge(@PathVariable Long id, @Valid @RequestBody CommunityReq.Knowledge req) {
        return Result.success(communityService.saveKnowledge(id, null, req));
    }

    /** 修改角色资料 */
    @PutMapping("/bots/{id}/knowledge/{knowledgeId}")
    @OperationLog(action = "update", targetType = "community-knowledge", description = "修改角色资料")
    public Result<CommunityKnowledge> updateKnowledge(@PathVariable Long id, @PathVariable Long knowledgeId,
            @Valid @RequestBody CommunityReq.Knowledge req) {
        return Result.success(communityService.saveKnowledge(id, knowledgeId, req));
    }

    /** 删除角色资料 */
    @DeleteMapping("/bots/{id}/knowledge/{knowledgeId}")
    @OperationLog(action = "delete", targetType = "community-knowledge", description = "删除角色资料")
    public Result<Void> deleteKnowledge(@PathVariable Long id, @PathVariable Long knowledgeId) {
        communityService.deleteKnowledge(id, knowledgeId);
        return Result.success();
    }

    /** 查询社区互动设置 */
    @GetMapping("/settings")
    public Result<CommunitySettings> settings() {
        return Result.success(communityService.settings());
    }

    /** 修改社区互动设置 */
    @PutMapping("/settings")
    @OperationLog(action = "update", targetType = "community-settings", description = "修改社区互动设置")
    public Result<CommunitySettings> saveSettings(@Valid @RequestBody CommunityReq.Settings req) {
        return Result.success(communityService.saveSettings(req));
    }

    /** 查询文章社区开关 */
    @GetMapping("/posts/{id}/enabled")
    public Result<CommunityReq.Enabled> enabled(@PathVariable Long id) {
        return Result.success(new CommunityReq.Enabled(communityService.postEnabled(id)));
    }

    /** 修改文章社区开关 */
    @PutMapping("/posts/{id}/enabled")
    @OperationLog(action = "update", targetType = "community-post", description = "修改文章社区开关")
    public Result<Void> enabled(@PathVariable Long id, @Valid @RequestBody CommunityReq.Enabled req) {
        communityService.setPostEnabled(id, req.enabled());
        return Result.success();
    }

    /** 邀请角色评论文章 */
    @PostMapping("/posts/{id}/invite")
    @OperationLog(action = "create", targetType = "community-post", description = "邀请角色评论文章")
    public Result<CommunityResp.Queued> invite(@PathVariable Long id, @Valid @RequestBody CommunityReq.Invite req) {
        return Result.success(communityService.invite(id, req.botIds()));
    }

    /** 为近期公开文章补评 */
    @PostMapping("/backfill")
    @OperationLog(action = "create", targetType = "community-post", description = "为近期公开文章补评")
    public Result<CommunityResp.Backfill> backfill(@Valid @RequestBody CommunityReq.Backfill req) {
        return Result.success(communityService.backfill(req));
    }
}
