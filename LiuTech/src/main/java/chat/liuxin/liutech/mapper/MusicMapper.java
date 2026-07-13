package chat.liuxin.liutech.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import chat.liuxin.liutech.model.Music;

/**
 * 音乐Mapper接口
 * @author 刘鑫
 */
@Mapper
public interface MusicMapper extends BaseMapper<Music> {

    /**
     * 查询所有音乐封面URL（用于孤立图片清理）
     *
     * @return 封面URL列表
     */
    @Select("SELECT cover_url FROM music WHERE cover_url IS NOT NULL AND deleted_at IS NULL")
    List<String> selectAllCoverUrls();
}
