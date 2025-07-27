package chat.liuxin.liutech.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

import lombok.Data;

/**
 * 基础ID实体类
 * 只包含主键ID字段
 */
@Data
public abstract class IdEntity {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
}