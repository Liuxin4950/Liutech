package chat.liuxin.liutech.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import chat.liuxin.liutech.model.Music;

/**
 * 音乐Mapper接口
 * @author liuxin
 */
@Mapper
public interface MusicMapper extends BaseMapper<Music> {

    /**
     * 获取启用的音乐列表(按排序)
     * @return 音乐列表
     */
    @Select("SELECT * FROM music WHERE status = 1 ORDER BY sort_order ASC, id ASC")
    List<Music> selectEnabledMusicList();

    /**
     * 根据ID获取启用的音乐
     * @param id 音乐ID
     * @return 音乐
     */
    @Select("SELECT * FROM music WHERE id = #{id} AND status = 1")
    Music selectEnabledById(@Param("id") Long id);
}
