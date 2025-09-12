package chat.liuxin.liutech.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import chat.liuxin.liutech.model.PostAttachments;

/**
 * 文章附件表 Mapper 接口
 * @author 刘鑫
 * @date 2025-01-08
 */
public interface PostAttachmentsMapper extends BaseMapper<PostAttachments> {
    
    /**
     * 根据草稿键查询附件列表
     * @param draftKey 草稿键
     * @return 附件列表
     */
    List<PostAttachments> selectByDraftKey(@Param("draftKey") String draftKey);
    
    /**
     * 根据文章ID查询附件列表
     * @param postId 文章ID
     * @return 附件列表
     */
    List<PostAttachments> selectByPostId(@Param("postId") Long postId);
    
    /**
     * 将草稿附件绑定到文章
     * @param draftKey 草稿键
     * @param postId 文章ID
     * @return 更新行数
     */
    int bindDraftToPost(@Param("draftKey") String draftKey, @Param("postId") Long postId);
    
    /**
     * 查询草稿附件详细信息（关联 Resources 表）
     * @param draftKey 草稿键
     * @param userId 用户ID
     * @return 附件详细信息列表
     */
    List<java.util.Map<String, Object>> selectDraftAttachments(@Param("draftKey") String draftKey, @Param("userId") Long userId);
    
    /**
     * 查询文章附件详细信息（关联 Resources 表，限制上传者）
     * @param postId 文章ID
     * @param userId 用户ID
     * @return 附件详细信息列表
     */
    List<java.util.Map<String, Object>> selectPostAttachments(@Param("postId") Long postId, @Param("userId") Long userId);

    /**
     * 查询文章附件详细信息（公开，不限制上传者，供前台详情展示）
     * @param postId 文章ID
     * @return 附件详细信息列表
     */
    List<java.util.Map<String, Object>> selectPostAttachmentsPublic(@Param("postId") Long postId);
    
    /**
     * 根据资源ID删除附件关联记录
     * @param resourceId 资源ID
     * @return 删除行数
     */
    int deleteByResourceId(@Param("resourceId") Long resourceId);

    /**
     * 根据文章ID删除附件关联记录
     * @param postId 文章ID
     * @return 删除行数
     */
    int deleteByPostId(@Param("postId") Long postId);
}