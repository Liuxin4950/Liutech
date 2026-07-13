package chat.liuxin.liutech.controller.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.TagResp;
import chat.liuxin.liutech.service.TagsService;
import lombok.extern.slf4j.Slf4j;

/**
 * 标签控制器
 * 提供文章标签相关的REST API接口
 *
 * @author 刘鑫
 */
@Slf4j
@RestController
@RequestMapping("/tags")
public class TagsController {

    @Autowired
    private TagsService tagsService;

    /**
     * 查询所有标签（包含文章数量）
     *
     * @return 标签列表
     */
    @GetMapping
    public Result<List<TagResp>> getAllTags() {

        List<TagResp> tags = tagsService.getAllTagsWithPostCount();

        return Result.success("查询成功", tags);
    }

    /**
     * 根据ID查询标签详情（包含文章数量）
     *
     * @param id 标签ID
     * @return 标签详情
     */
    @GetMapping("/{id}")
    public Result<TagResp> getTagById(@PathVariable Long id) {

        TagResp tag = tagsService.getTagByIdWithPostCount(id);
        if (tag == null) {
            log.warn("标签不存在 - ID: {}", id);
            return Result.fail(ErrorCode.TAG_NOT_FOUND);
        }

        return Result.success("查询成功", tag);
    }

    /**
     * 查询热门标签
     * 根据文章数量排序
     *
     * @param limit 限制数量，默认20
     * @return 热门标签列表
     */
    @GetMapping("/hot")
    public Result<List<TagResp>> getHotTags(
            @RequestParam(defaultValue = "20") Integer limit) {

        List<TagResp> tags = tagsService.getHotTags(limit);

        return Result.success("查询成功", tags);
    }

    /**
     * 根据文章ID查询标签列表
     *
     * @param postId 文章ID
     * @return 标签列表
     */
    @GetMapping("/post/{postId}")
    public Result<List<TagResp>> getTagsByPostId(@PathVariable Long postId) {

        List<TagResp> tags = tagsService.getTagsByPostId(postId);

        return Result.success("查询成功", tags);
    }

    /**
     * 根据标签名字搜索标签
     *
     * @param name 标签名字（支持模糊搜索）
     * @return 标签列表
     */
    @GetMapping("/search")
    public Result<List<TagResp>> searchTagsByName(@RequestParam String name) {

        try {
            List<TagResp> tags = tagsService.getTagsByName(name);
            return Result.success("查询成功", tags);
        } catch (BusinessException e) {
            return Result.fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("搜索标签失败: {}", e.getMessage(), e);
            return Result.fail(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 创建标签
     * @param tagResp 标签信息
     * @return 创建结果
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "create", targetType = "tag", description = "创建标签")
    public Result<Boolean> createTag(@RequestBody TagResp tagResp) {
        try {
            boolean result = tagsService.save(tagResp);
            return Result.success(result);
        } catch (BusinessException e) {
            return Result.fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("创建标签失败: {}", e.getMessage(), e);
            return Result.fail(ErrorCode.TAG_CREATE_FAILED);
        }
    }
}
