package chat.liuxin.liutech.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import chat.liuxin.liutech.resp.PurchasedResourceResp;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import chat.liuxin.liutech.model.ResourceDownloads;
import chat.liuxin.liutech.resp.DownloadLogResp;

/**
 * 资源下载记录 Mapper 接口
 *
 * @author 刘鑫
 * @date 2025-01-15
 */
@Mapper
public interface ResourceDownloadsMapper extends BaseMapper<ResourceDownloads> {
    @org.apache.ibatis.annotations.Delete({"<script>DELETE FROM download_logs WHERE resource_id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>"})
    int permanentDeleteByResourceIds(@Param("ids") List<Long> ids);

    IPage<PurchasedResourceResp> selectUserPurchases(Page<PurchasedResourceResp> page, @Param("userId") Long userId);

    /**
     * 查询用户是否已购买某资源
     *
     * @param userId 用户ID
     * @param resourceId 资源ID
     * @return 购买记录数量
     */
    int countUserPurchase(@Param("userId") Long userId, @Param("resourceId") Long resourceId);

    /**
     * 管理端分页查询下载记录（包含用户名和资源名称）
     * @param offset 偏移量
     * @param limit 限制数量
     * @param userId 用户ID（可选）
     * @param resourceId 资源ID（可选）
     * @return 下载记录列表
     */
    List<DownloadLogResp> selectDownloadLogsForAdmin(@Param("offset") long offset,
                                                     @Param("limit") Integer limit,
                                                     @Param("userId") Long userId,
                                                     @Param("resourceId") Long resourceId);

    /**
     * 管理端查询下载记录总数
     * @param userId 用户ID（可选）
     * @param resourceId 资源ID（可选）
     * @return 总数
     */
    Integer countDownloadLogsForAdmin(@Param("userId") Long userId,
                                      @Param("resourceId") Long resourceId);
}