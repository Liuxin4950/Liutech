package chat.liuxin.liutech.model;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 系统设置实体
 *
 * 设计目标：
 * 1) 让管理端可以动态配置一些“非业务核心、但需要热更新”的参数；
 * 2) 统一从数据库读取，避免频繁改配置文件、重新部署；
 * 3) 以 key-value 为主，value 用字符串存储，必要时可存 JSON。
 */
@Data
@TableName("system_settings")
public class SystemSetting {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 配置键（全局唯一）
     * 例如：tts.baseUrl、tts.enabled
     */
    private String settingKey;

    /**
     * 配置值（字符串）
     */
    private String settingValue;

    /**
     * 配置说明（便于管理端展示）
     */
    private String description;

    private Date createdAt;

    private Date updatedAt;
}

