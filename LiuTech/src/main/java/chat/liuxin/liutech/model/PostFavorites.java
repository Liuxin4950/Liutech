package chat.liuxin.liutech.model;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 文章收藏实体类
 * 
 * @author 刘鑫
 * @date 2025-01-15
 */
@Data
@TableName("post_favorites")
public class PostFavorites {
    
    /**
     * 收藏ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 文章ID
     */
    private Long postId;
    
    /**
     * 是否收藏(0取消收藏,1收藏)
     */
    private Integer isFavorite;
    
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    
    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
    
    /**
     * 删除时间（软删除）
     */
    private Date deletedAt;
}