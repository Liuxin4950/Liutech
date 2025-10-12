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

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.TagResp;
import chat.liuxin.liutech.service.TagsService;
import chat.liuxin.liutech.utils.ValidationUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 标签控制器
 * 提供文章标签相关的REST API接口
 *
 * @author liuxin
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
        log.info("查询所有标签");

        List<TagResp> tags = tagsService.getAllTagsWithPostCount();
        log.info("查询标签成功 - 数量: {}", tags.size());

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
        log.info("查询标签详情 - ID: {}", id);

        TagResp tag = tagsService.getTagByIdWithPostCount(id);
        if (tag == null) {
            log.warn("标签不存在 - ID: {}", id);
            return Result.fail(ErrorCode.TAG_NOT_FOUND);
        }

        log.info("查询标签详情成功 - 名称: {}, 文章数量: {}", tag.getName(), tag.getPostCount());
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

        log.info("查询热门标签 - 限制数量: {}", limit);

        List<TagResp> tags = tagsService.getHotTags(limit);
        log.info("查询热门标签成功 - 数量: {}", tags.size());

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
        log.info("查询文章标签 - 文章ID: {}", postId);

        List<TagResp> tags = tagsService.getTagsByPostId(postId);
        log.info("查询文章标签成功 - 文章ID: {}, 标签数量: {}", postId, tags.size());

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
        log.info("搜索标签 - 关键词: {}", name);

        if (name == null || name.trim().isEmpty()) {
            log.warn("搜索关键词为空");
            return Result.fail(ErrorCode.PARAMS_ERROR.getCode(), "搜索关键词不能为空");
        }

        List<TagResp> tags = tagsService.getTagsByName(name.trim());
        log.info("搜索标签成功 - 关键词: {}, 结果数量: {}", name, tags.size());

        return Result.success("查询成功", tags);
    }

    /**
     * 创建标签（需要登录）
     *
     * @param tag 标签信息
     * @return 创建结果
     */
    @PostMapping
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public Result<TagResp> createTag(@RequestBody TagResp tag) {
        log.info("创建标签 - 名称: {}", tag.getName());
        
        ValidationUtil.validateNotNull(tag, "标签信息");
        ValidationUtil.validateNotBlank(tag.getName(), "标签名称");
        
        try {
            boolean success = tagsService.save(tag);
            if (success) {
                log.info("标签创建成功 - 名称: {}", tag.getName());
                return Result.success("标签创建成功", tag);
            } else {
                log.warn("标签创建失败 - 名称: {}", tag.getName());
                return Result.fail(ErrorCode.TAG_CREATE_FAILED, "标签创建失败");
            }
        } catch (Exception e) {
            log.error("标签创建异常 - 名称: {}, 错误:", tag.getName(), e);
            
            // 检查是否是重复名称错误
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry") && e.getMessage().contains("tags.name")) {
                return Result.fail(ErrorCode.TAG_CREATE_FAILED, "标签名称已存在，请使用其他名称");
            }
            
            return Result.fail(ErrorCode.TAG_CREATE_FAILED, "系统错误，请稍后重试");
        }
    }
}
