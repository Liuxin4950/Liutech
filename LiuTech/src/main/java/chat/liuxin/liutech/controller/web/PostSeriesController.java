package chat.liuxin.liutech.controller.web;

import lombok.RequiredArgsConstructor;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.PostSeriesResp;
import chat.liuxin.liutech.service.PostSeriesService;
import lombok.extern.slf4j.Slf4j;

/**
 * 文章系列控制器（用户前台）
 * 提供系列列表、详情等公开接口；系列内文章目录复用 /posts?seriesId=xx。
 *
 * @author 刘鑫
 */
@Slf4j
@RestController
@RequestMapping("/series")
@RequiredArgsConstructor
public class PostSeriesController {

    private final PostSeriesService postSeriesService;

    /** 查询所有系列（含已发布文章数，公开） */
    @GetMapping
    public Result<List<PostSeriesResp>> getSeriesList() {
        return Result.success("查询成功", postSeriesService.getAllSeriesWithPostCount());
    }

    /** 根据ID查询系列详情（公开） */
    @GetMapping("/{id}")
    public Result<PostSeriesResp> getSeriesById(@PathVariable Long id) {
        PostSeriesResp series = postSeriesService.getSeriesDetail(id);
        if (series == null) {
            return Result.fail(ErrorCode.SERIES_NOT_FOUND);
        }
        return Result.success("查询成功", series);
    }

    /** 创建系列（管理员） */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "create", targetType = "series", description = "创建系列: #series.name", targetName = "#series.name")
    public Result<Boolean> createSeries(@RequestBody PostSeriesResp series) {
        return Result.success(postSeriesService.save(series));
    }
}
