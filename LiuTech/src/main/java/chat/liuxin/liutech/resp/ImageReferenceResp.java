package chat.liuxin.liutech.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片引用来源响应（管理端反向溯源）
 * 描述一张图片被哪些文章/头像/轮播/音乐封面/系列封面引用
 *
 * @author 刘鑫
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageReferenceResp {

    /**
     * 引用来源类型
     * post_cover/post_thumbnail/post_content/user_avatar/carousel/music_cover/series_cover
     */
    private String sourceType;

    /**
     * 来源记录ID（文章ID/用户ID/轮播ID/音乐ID/系列ID）
     */
    private Long sourceId;

    /**
     * 来源标题（文章标题/用户名/轮播标题/歌曲名/系列名）
     */
    private String sourceTitle;

    /**
     * 引用字段描述（文章封面/文章缩略图/文章正文/用户头像/轮播图/音乐封面/系列封面）
     */
    private String sourceField;
}
