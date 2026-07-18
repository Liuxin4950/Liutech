package chat.liuxin.liutech.controller.admin;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
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
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.PostSeriesResp;
import chat.liuxin.liutech.service.PostSeriesService;
import chat.liuxin.liutech.utils.UserUtils;
import chat.liuxin.liutech.utils.ValidationUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理端文章系列控制器
 * 需要管理员权限（类级 @PreAuthorize 保证认证，异常由 GlobalExceptionHandler 统一兜底）。
 *
 * @author 刘鑫
 */
@Slf4j
@RestController
@RequestMapping("/admin/series")
@PreAuthorize("hasRole('ADMIN')")
public class PostSeriesAdminController extends BaseAdminController {

    @Autowired
    private PostSeriesService postSeriesService;

    @Autowired
    private UserUtils userUtils;

    /** 分页查询系列列表 */
    @GetMapping
    public Result<PageResp<PostSeriesResp>> getSeriesList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "false") Boolean includeDeleted) {
        return Result.success(postSeriesService.getSeriesListForAdmin(page, size, name, includeDeleted));
    }

    /** 根据ID查询系列详情 */
    @GetMapping("/{id}")
    public Result<PostSeriesResp> getSeriesById(@PathVariable Long id) {
        ValidationUtil.validateId(id, "系列ID");
        return checkResourceExists(postSeriesService.getSeriesDetail(id), ErrorCode.SERIES_NOT_FOUND);
    }

    /** 创建系列 */
    @PostMapping
    @OperationLog(action = "create", targetType = "series", description = "创建系列: #series.name", targetName = "#series.name")
    public Result<String> createSeries(@RequestBody PostSeriesResp series) {
        ValidationUtil.validateNotNull(series, "系列信息");
        return handleOperationResult(postSeriesService.save(series), "系列创建成功", "系列创建");
    }

    /** 更新系列 */
    @PutMapping("/{id}")
    @OperationLog(action = "update", targetType = "series", description = "更新系列: #series.name", targetName = "#series.name")
    public Result<String> updateSeries(@PathVariable Long id, @RequestBody PostSeriesResp series) {
        ValidationUtil.validateId(id, "系列ID");
        ValidationUtil.validateNotNull(series, "系列信息");
        series.setId(id);
        return handleOperationResult(postSeriesService.updateById(series), "系列更新成功", "系列更新");
    }

    /** 删除系列（软删除） */
    @DeleteMapping("/{id}")
    @OperationLog(action = "delete", targetType = "series", description = "删除系列", targetName = "#id")
    public Result<String> deleteSeries(@PathVariable Long id) {
        ValidationUtil.validateId(id, "系列ID");
        return handleOperationResult(postSeriesService.removeByIds(List.of(id)), "系列删除成功", "系列删除");
    }

    /** 批量删除系列 */
    @PostMapping("/batch")
    @OperationLog(action = "delete", targetType = "series", description = "批量删除系列")
    public Result<String> batchDeleteSeries(@RequestBody List<Long> ids) {
        ValidationUtil.validateNotEmpty(ids, "系列ID列表");
        return handleOperationResult(postSeriesService.removeByIds(ids), "批量删除系列成功", "批量删除系列");
    }

    /** 恢复已删除的系列 */
    @PutMapping("/{id}/restore")
    @OperationLog(action = "restore", targetType = "series", description = "恢复系列", targetName = "#id")
    public Result<String> restoreSeries(@PathVariable Long id) {
        ValidationUtil.validateId(id, "系列ID");
        return handleOperationResult(postSeriesService.restoreSeries(id), "系列恢复成功", "系列恢复");
    }

    /** 彻底删除系列（物理删除） */
    @DeleteMapping("/{id}/permanent")
    @OperationLog(action = "delete", targetType = "series", description = "彻底删除系列", targetName = "#id")
    public Result<String> permanentDeleteSeries(@PathVariable Long id) {
        ValidationUtil.validateId(id, "系列ID");
        return handleOperationResult(postSeriesService.permanentDeleteSeries(id), "系列彻底删除成功", "系列彻底删除");
    }

    /** 批量彻底删除系列（物理删除） */
    @PostMapping("/batch/permanent")
    @OperationLog(action = "delete", targetType = "series", description = "批量彻底删除系列", targetName = "#ids")
    public Result<String> batchPermanentDeleteSeries(@RequestBody List<Long> ids) {
        ValidationUtil.validateNotEmpty(ids, "系列ID列表");
        return handleOperationResult(postSeriesService.batchPermanentDeleteSeries(ids), "批量彻底删除系列成功", "批量彻底删除系列");
    }

    /** 拖拽排序：批量更新系列内文章排序 */
    @PutMapping("/{id}/posts-order")
    @OperationLog(action = "update", targetType = "series", description = "更新系列文章排序")
    public Result<String> updatePostsOrder(@PathVariable Long id, @RequestBody List<Map<String, Object>> items) {
        ValidationUtil.validateId(id, "系列ID");
        return handleOperationResult(
                postSeriesService.batchUpdateSeriesSort(id, items, userUtils.getCurrentUserId()),
                "系列文章排序更新成功", "系列文章排序更新");
    }
}
