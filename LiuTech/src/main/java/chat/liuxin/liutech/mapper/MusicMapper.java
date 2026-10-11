package chat.liuxin.liutech.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import chat.liuxin.liutech.model.Music;

/**
 * 音乐Mapper接口
 * @author 刘鑫
 */
@Mapper
public interface MusicMapper extends BaseMapper<Music> {

    @Select("SELECT id, title, artist, cover_url, full_audio_url, vocal_url, duration, sort_order, status, deleted_at, created_at, updated_at FROM music WHERE id = #{id} FOR UPDATE")
    Music selectForUpdate(Long id);

    @Select("SELECT id, title, artist, cover_url, full_audio_url, vocal_url, duration, sort_order, status, deleted_at, created_at, updated_at FROM music WHERE deleted_at IS NULL ORDER BY id FOR UPDATE")
    List<Music> selectAllForUpdate();

    /** 音乐管理没有回收站；文件清理与真实物理删除保持一致。 */
    @Delete("DELETE FROM music WHERE id = #{id}")
    int deletePermanently(Long id);

    /**
     * 查询所有音乐封面URL（用于孤立图片清理）
     *
     * @return 封面URL列表
     */
    @Select("SELECT cover_url FROM music WHERE cover_url IS NOT NULL AND deleted_at IS NULL")
    List<String> selectAllCoverUrls();
}
