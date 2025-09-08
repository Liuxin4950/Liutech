package chat.liuxin.liutech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import chat.liuxin.liutech.model.ResourceDownloads;

/**
 * 资源下载记录 Mapper 接口
 * 
 * @author 刘鑫
 * @date 2025-01-15
 */
@Mapper
public interface ResourceDownloadsMapper extends BaseMapper<ResourceDownloads> {
    
    /**
     * 查询用户是否已购买某资源
     * 
     * @param userId 用户ID
     * @param resourceId 资源ID
     * @return 购买记录数量
     */
    int countUserPurchase(@Param("userId") Long userId, @Param("resourceId") Long resourceId);
}