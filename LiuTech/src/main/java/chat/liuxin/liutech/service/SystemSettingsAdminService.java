package chat.liuxin.liutech.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import chat.liuxin.liutech.mapper.SystemSettingMapper;
import chat.liuxin.liutech.model.SystemSetting;

/**
 * 系统设置管理服务（管理端专用）
 *
 * 仅保留真正被业务消费的设置项：
 * - author.*  作者资料（首页侧边栏展示）
 * - tts.*     语音合成配置
 *
 * site.* / comment.* / upload.* 等已移除，改为硬编码或由 Spring 配置控制。
 */
@Service
@RequiredArgsConstructor
public class SystemSettingsAdminService {

    private final SystemSettingMapper systemSettingMapper;

    /** 预定义设置项：key -> [默认值, 描述, 分组] */
    private static final Map<String, String[]> PREDEFINED_SETTINGS = new LinkedHashMap<>();

    static {
        // 作者资料设置（首页侧边栏展示，Admin 可动态修改）
        PREDEFINED_SETTINGS.put("author.name", new String[]{"小鑫同学", "作者昵称（首页侧边栏展示）", "author"});
        PREDEFINED_SETTINGS.put("author.title", new String[]{"欢迎访问", "作者头衔/职位", "author"});
        PREDEFINED_SETTINGS.put("author.avatar", new String[]{"/洛天依.png", "作者头像 URL", "author"});
        PREDEFINED_SETTINGS.put("author.bio", new String[]{"专注于前端开发、后端架构和技术分享。热爱编程，喜欢探索新技术。", "作者个人简介", "author"});
        // TTS 语音设置（TtsConfigService 消费）
        PREDEFINED_SETTINGS.put("tts.enabled", new String[]{"true", "语音推理全局开关：true/false", "tts"});
        PREDEFINED_SETTINGS.put("tts.provider", new String[]{"GPT_SOVITS", "语音推理引擎：GPT_SOVITS/SILICONFLOW", "tts"});
        PREDEFINED_SETTINGS.put("tts.baseUrl", new String[]{"", "语音推理服务基础地址", "tts"});
        PREDEFINED_SETTINGS.put("tts.voiceModel", new String[]{"", "默认语音模型", "tts"});
        PREDEFINED_SETTINGS.put("tts.siliconFlowModel", new String[]{"FunAudioLLM/CosyVoice2-0.5B", "SiliconFlow TTS 模型名称", "tts"});
        PREDEFINED_SETTINGS.put("tts.siliconFlowVoiceUri", new String[]{"", "SiliconFlow 自定义音色 URI", "tts"});
        PREDEFINED_SETTINGS.put("tts.responseFormat", new String[]{"mp3", "TTS 输出音频格式", "tts"});
        PREDEFINED_SETTINGS.put("tts.sampleRate", new String[]{"44100", "TTS 输出采样率", "tts"});
        PREDEFINED_SETTINGS.put("tts.speed", new String[]{"1.0", "TTS 语速", "tts"});
    }

    /**
     * 查询所有系统设置（按 id 排序）
     */
    public List<SystemSetting> listAll() {
        return systemSettingMapper.selectList(
                new LambdaQueryWrapper<SystemSetting>().orderByAsc(SystemSetting::getId));
    }

    /**
     * 根据 key 查询单个设置
     */
    public SystemSetting getByKey(String key) {
        return systemSettingMapper.selectByKey(key);
    }

    /**
     * 更新单个设置值（不存在则创建）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateByKey(String key, String value, String description) {
        SystemSetting existing = systemSettingMapper.selectByKey(key);
        if (existing == null) {
            SystemSetting setting = new SystemSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            setting.setDescription(description);
            setting.setCreatedAt(new Date());
            setting.setUpdatedAt(new Date());
            systemSettingMapper.insert(setting);
        } else {
            existing.setSettingValue(value);
            if (description != null && !description.isBlank()) {
                existing.setDescription(description);
            }
            existing.setUpdatedAt(new Date());
            systemSettingMapper.updateById(existing);
        }
    }

    /**
     * 批量更新设置
     *
     * @param settings 每个元素包含 key、value，可选 description
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdate(List<Map<String, String>> settings) {
        for (Map<String, String> item : settings) {
            String key = item.get("key");
            String value = item.get("value");
            String description = item.get("description");
            if (key != null) {
                updateByKey(key, value, description);
            }
        }
    }

    /**
     * 按分组获取设置
     *
     * 返回格式：{ "author": [...], "tts": [...], "other": [...] }
     * 如果数据库中还没有预定义的设置项，会自动初始化默认值。
     */
    public Map<String, List<SystemSetting>> getGroupedSettings() {
        ensurePredefinedSettings();

        List<SystemSetting> all = listAll();
        Map<String, List<SystemSetting>> grouped = new LinkedHashMap<>();

        // 先按预定义分组顺序初始化空列表
        Map<String, String> groupLabels = new LinkedHashMap<>();
        groupLabels.put("author", "作者资料设置");
        groupLabels.put("tts", "语音设置");
        groupLabels.put("other", "其他");

        for (String group : groupLabels.keySet()) {
            grouped.put(group, new ArrayList<>());
        }

        // 根据 key 前缀归类
        for (SystemSetting setting : all) {
            String group = resolveGroup(setting.getSettingKey());
            grouped.computeIfAbsent(group, k -> new ArrayList<>()).add(setting);
        }

        // 移除空分组
        grouped.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        return grouped;
    }

    /**
     * 确保预定义设置项已存在于数据库中
     */
    private void ensurePredefinedSettings() {
        for (Map.Entry<String, String[]> entry : PREDEFINED_SETTINGS.entrySet()) {
            String key = entry.getKey();
            String[] meta = entry.getValue();
            if (systemSettingMapper.selectByKey(key) == null) {
                SystemSetting setting = new SystemSetting();
                setting.setSettingKey(key);
                setting.setSettingValue(meta[0]);
                setting.setDescription(meta[1]);
                setting.setCreatedAt(new Date());
                setting.setUpdatedAt(new Date());
                systemSettingMapper.insert(setting);
            }
        }
    }

    /**
     * 根据 settingKey 的前缀判断分组
     */
    private String resolveGroup(String key) {
        if (key == null) return "other";
        if (key.startsWith("author.")) return "author";
        if (key.startsWith("tts.")) return "tts";
        return "other";
    }
}
