package chat.liuxin.liutech.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import chat.liuxin.liutech.model.Music;

/**
 * 音乐Mapper接口
 * @author liuxin
 */
@Mapper
public interface MusicMapper extends BaseMapper<Music> {
}
