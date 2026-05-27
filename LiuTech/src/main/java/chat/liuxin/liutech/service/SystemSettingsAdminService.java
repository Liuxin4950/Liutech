package chat.liuxin.liutech.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import chat.liuxin.liutech.mapper.SystemSettingMapper;
import chat.liuxin.liutech.model.SystemSetting;

/**
 * 系统设置管理服务（管理端专用）
 *
 * 在 SystemSettingService 的基础上提供管理端 CRUD 和分组查询能力。
 * 预定义常用设置项，首次访问时自动初始化默认值。
 */
@Service
public class SystemSettingsAdminService {

    @Autowired
    private SystemSettingMapper systemSettingMapper;

    /** 预定义设置项：key -> [默认值, 描述, 分组] */
    private static final Map<String, String[]> PREDEFINED_SETTINGS = new LinkedHashMap<>();

    static {
        // 站点基本设置
        PREDEFINED_SETTINGS.put("site.name", new String[]{"LiuTech", "站点名称", "site"});
        PREDEFINED_SETTINGS.put("site.description", new String[]{"", "站点描述（SEO description）", "site"});
        PREDEFINED_SETTINGS.put("site.keywords", new String[]{"", "SEO 关键词（逗号分隔）", "site"});
        PREDEFINED_SETTINGS.put("site.logo_url", new String[]{"", "站点 Logo URL", "site"});
        PREDEFINED_SETTINGS.put("site.favicon_url", new String[]{"", "Favicon URL", "site"});
        PREDEFINED_SETTINGS.put("site.footer_text", new String[]{"", "页脚文本", "site"});
        // 备案信息
        PREDEFINED_SETTINGS.put("site.icp_number", new String[]{"", "ICP 备案号", "filing"});
        PREDEFINED_SETTINGS.put("site.analytics_code", new String[]{"", "统计代码（如 Google Analytics）", "filing"});
        // 评论设置
        PREDEFINED_SETTINGS.put("comment.need_review", new String[]{"true", "评论是否需要审核（true/false）", "comment"});
        // 上传设置
        PREDEFINED_SETTINGS.put("upload.max_size_mb", new String[]{"100", "上传文件最大大小（MB）", "upload"});
        // 作者资料设置
        PREDEFINED_SETTINGS.put("author.name", new String[]{"小鑫同学", "作者昵称（首页侧边栏展示）", "author"});
        PREDEFINED_SETTINGS.put("author.title", new String[]{"欢迎访问", "作者头衔/职位", "author"});
        PREDEFINED_SETTINGS.put("author.avatar", new String[]{"/洛天依.png", "作者头像 URL", "author"});
        PREDEFINED_SETTINGS.put("author.bio", new String[]{"专注于前端开发、后端架构和技术分享。热爱编程，喜欢探索新技术。", "作者个人简介", "author"});
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
    @Transactional
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
    @Transactional
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
     * 返回格式：{ "site": [...], "comment": [...], ... }
     * 如果数据库中还没有预定义的设置项，会自动初始化默认值。
     */
    public Map<String, List<SystemSetting>> getGroupedSettings() {
        ensurePredefinedSettings();

        List<SystemSetting> all = listAll();
        Map<String, List<SystemSetting>> grouped = new LinkedHashMap<>();

        // 先按预定义分组顺序初始化空列表
        Map<String, String> groupLabels = new LinkedHashMap<>();
        groupLabels.put("site", "站点基本设置");
        groupLabels.put("filing", "备案与统计");
        groupLabels.put("comment", "评论设置");
        groupLabels.put("upload", "上传设置");
        groupLabels.put("tts", "语音设置");
        groupLabels.put("author", "作者资料设置");
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
        // site.icp_number 和 site.analytics_code 归入 filing（必须在 site. 前缀判断之前）
        if ("site.icp_number".equals(key) || "site.analytics_code".equals(key)) return "filing";
        if (key.startsWith("site.")) return "site";
        if (key.startsWith("comment.")) return "comment";
        if (key.startsWith("upload.")) return "upload";
        if (key.startsWith("tts.")) return "tts";
        if (key.startsWith("author.")) return "author";
        return "other";
    }
}
