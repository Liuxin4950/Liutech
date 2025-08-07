package chat.liuxin.liutech.controller.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Tags;
import chat.liuxin.liutech.service.TagsService;
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
    public Result<List<Tags>> getAllTags() {
        log.info("查询所有标签");
        
        List<Tags> tags = tagsService.getAllTagsWithPostCount();
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
    public Result<Tags> getTagById(@PathVariable Long id) {
        log.info("查询标签详情 - ID: {}", id);
        
        Tags tag = tagsService.getTagByIdWithPostCount(id);
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
    public Result<List<Tags>> getHotTags(
            @RequestParam(defaultValue = "20") Integer limit) {
        
        log.info("查询热门标签 - 限制数量: {}", limit);
        
        List<Tags> tags = tagsService.getHotTags(limit);
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
    public Result<List<Tags>> getTagsByPostId(@PathVariable Long postId) {
        log.info("查询文章标签 - 文章ID: {}", postId);
        
        List<Tags> tags = tagsService.getTagsByPostId(postId);
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
    public Result<List<Tags>> searchTagsByName(@RequestParam String name) {
        log.info("搜索标签 - 关键词: {}", name);
        
        if (name == null || name.trim().isEmpty()) {
            log.warn("搜索关键词为空");
            return Result.fail(ErrorCode.PARAMS_ERROR.getCode(), "搜索关键词不能为空");
        }
        
        List<Tags> tags = tagsService.getTagsByName(name.trim());
        log.info("搜索标签成功 - 关键词: {}, 结果数量: {}", name, tags.size());
        
        return Result.success("搜索成功", tags);
    }
}