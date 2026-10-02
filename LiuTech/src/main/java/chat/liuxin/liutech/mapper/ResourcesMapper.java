package chat.liuxin.liutech.mapper;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import chat.liuxin.liutech.model.Resources;
import chat.liuxin.liutech.resp.ResourceResp;

/**
 * 资源表 Mapper 接口
 * @author 刘鑫
 * @date 2025-01-08
 */
@Mapper
public interface ResourcesMapper extends BaseMapper<Resources> {

    /**
     * 根据ID查询资源详情（包含上传者信息）
     * @param id 资源ID
     * @return 资源详情
     */
    ResourceResp selectResourceById(@Param("id") Long id);

    /**
     * 管理端分页查询资源列表（包含上传者信息）
     * @param offset 偏移量
     * @param limit 限制数量
     * @param name 资源名称（可选，模糊搜索）
     * @param resourceType 资源类型（可选）
     * @param downloadType 下载类型（可选）
     * @param includeDeleted 是否包含已删除资源
     * @return 资源列表
     */
    List<ResourceResp> selectResourcesForAdmin(@Param("offset") Integer offset,
                                               @Param("limit") Integer limit,
                                               @Param("name") String name,
                                               @Param("resourceType") String resourceType,
                                               @Param("downloadType") Integer downloadType,
                                               @Param("includeDeleted") Boolean includeDeleted);

    /**
     * 管理端查询资源总数
     * @param name 资源名称（可选，模糊搜索）
     * @param resourceType 资源类型（可选）
     * @param downloadType 下载类型（可选）
     * @param includeDeleted 是否包含已删除资源
     * @return 总数
     */
    Integer countResourcesForAdmin(@Param("name") String name,
                                   @Param("resourceType") String resourceType,
                                   @Param("downloadType") Integer downloadType,
                                   @Param("includeDeleted") Boolean includeDeleted);

    /**
     * 恢复已删除的资源
     * @param id 资源ID
     * @return 影响的行数
     */
    int restoreResourceById(@Param("id") Long id);

    /**
     * 根据ID列表物理删除资源
     * @param ids 资源ID列表
     * @return 影响的行数
     */
    int permanentDeleteByIds(@Param("ids") List<Long> ids);

    /**
     * 查重候选：同用户、同名、同草稿、时间窗口内未删除的文件资源；内容一致性由服务层校验
     *
     * @param userId   用户ID
     * @param fileName 文件名
     * @param draftKey 同一上传草稿任务
     * @param since 时间窗口起点，与应用写入 created_at 使用相同 Date 映射
     * @return 候选资源列表，按最新优先
     */
    List<Resources> selectRecentUploadCandidates(@Param("userId") Long userId,
                                                @Param("fileName") String fileName,
                                                @Param("draftKey") String draftKey,
                                                @Param("since") Date since);
}
