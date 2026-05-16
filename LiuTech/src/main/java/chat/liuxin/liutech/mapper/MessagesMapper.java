package chat.liuxin.liutech.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import chat.liuxin.liutech.model.Messages;

/**
 * 留言Mapper接口
 */
@Mapper
public interface MessagesMapper extends BaseMapper<Messages> {

    /**
     * 物理删除留言（绕过 @TableLogic 逻辑删除拦截）
     */
    @Delete("DELETE FROM messages WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    /**
     * 按 ID 查询留言（不过滤已删除记录，用于恢复操作）
     */
    @Select("SELECT * FROM messages WHERE id = #{id}")
    Messages selectUnfilteredById(@Param("id") Long id);

    /**
     * 软删除留言（绕过 @TableLogic，直接设置 deleted_at）
     */
    @Update("UPDATE messages SET deleted_at = NOW(), updated_at = NOW() WHERE id = #{id}")
    int softDeleteById(@Param("id") Long id);

    /**
     * 恢复已删除留言（绕过 @TableLogic，直接清除 deleted_at）
     */
    @Update("UPDATE messages SET deleted_at = NULL, updated_at = NOW() WHERE id = #{id}")
    int restoreById(@Param("id") Long id);
}
