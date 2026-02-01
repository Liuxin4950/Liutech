package chat.liuxin.liutech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import chat.liuxin.liutech.model.SystemSetting;

/**
 * 系统设置 Mapper
 */
@Mapper
public interface SystemSettingMapper extends BaseMapper<SystemSetting> {

    @Select("SELECT * FROM system_settings WHERE setting_key = #{key} LIMIT 1")
    SystemSetting selectByKey(String key);
}

