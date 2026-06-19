package chat.liuxin.liutech.service;

import java.util.Date;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.mapper.SystemSettingMapper;
import chat.liuxin.liutech.model.SystemSetting;

/**
 * 系统设置服务
 *
 * 说明：
 * - 这里的设置面向“全局配置”（不是用户偏好）。
 * - value 统一按字符串存储；业务层按需解析成 boolean/int/json。
 */
@Service
@RequiredArgsConstructor
 extends ServiceImpl<SystemSettingMapper, SystemSetting> {

    private final SystemSettingMapper systemSettingMapper;

    public String getValue(String key) {
        SystemSetting setting = systemSettingMapper.selectByKey(key);
        return setting == null ? null : setting.getSettingValue();
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String v = getValue(key);
        if (v == null) return defaultValue;
        String s = v.trim().toLowerCase();
        if (s.isEmpty()) return defaultValue;
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }

    @Transactional
    public void upsert(String key, String value, String description) {
        SystemSetting existing = systemSettingMapper.selectByKey(key);
        if (existing == null) {
            SystemSetting setting = new SystemSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            setting.setDescription(description);
            setting.setCreatedAt(new Date());
            setting.setUpdatedAt(new Date());
            this.save(setting);
            return;
        }

        existing.setSettingValue(value);
        if (description != null && !description.isBlank()) {
            existing.setDescription(description);
        }
        existing.setUpdatedAt(new Date());
        this.updateById(existing);
    }
}

