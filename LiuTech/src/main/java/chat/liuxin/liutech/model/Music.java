package chat.liuxin.liutech.model;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 音乐实体
 * @TableName music
 */
@Data
@TableName("music")
public class Music {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 歌曲名
     */
    private String title;

    /**
     * 艺术家
     */
    private String artist;

    /**
     * 封面图URL
     */
    private String coverUrl;

    /**
     * 完整音频URL
     */
    private String fullAudioUrl;

    /**
     * 人声音频URL(模型对口型)
     */
    private String vocalUrl;

    /**
     * 时长(秒)
     */
    private Integer duration;

    /**
     * 排序权重
     */
    private Integer sortOrder;

    /**
     * 状态: 1=启用, 0=禁用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;
}
