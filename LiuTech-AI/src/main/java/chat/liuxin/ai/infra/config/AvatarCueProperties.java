package chat.liuxin.ai.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Live2D 表情提示配置。
 *
 * <p>将 AvatarCueService 中硬编码的情绪关键词提取为可配置项。
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.ai.avatar-cue")
public class AvatarCueProperties {

    /** 情绪表达式与对应关键词映射。key 为 expression 名称，value 为触发关键词列表 */
    private Map<String, List<String>> emotionKeywords = Map.of(
            "sad", List.of("抱歉", "遗憾", "难过", "失败", "对不起", "可惜",
                    "伤心", "委屈", "心疼", "后悔", "呜呜", "哭了", "泪目", "惋惜",
                    "惨", "糟糕", "倒霉", "不幸", "痛苦", "失望", "沮丧", "消沉",
                    "哎", "唉", "唉声叹气", "愁", "忧伤", "惆怅", "凄凉", "心酸"),
            "angry", List.of("生气", "错误", "危险", "不应该", "拒绝", "禁止",
                    "烦", "讨厌", "气死", "岂有此理", "荒唐", "离谱", "可恶",
                    "愤怒", "不满", "抗议", "哼", "过分", "无语", "服了",
                    "崩溃", "抓狂", "受不了", "忍无可忍", "太过分"),
            "surprised", List.of("哇", "竟然", "原来", "没想到", "天哪", "不可思议",
                    "居然", "真的吗", "不会吧", "不是吧", "啊", "咦", "诶",
                    "震惊", "吃惊", "意外", "出乎意料", "万万没想到", "不敢相信",
                    "厉害了", "了不起", "惊", "吓", "震撼"),
            "shy", List.of("害羞", "不好意思", "悄悄", "小声", "脸红",
                    "紧张", "忐忑", "矜持", "羞涩", "腼腆", "难为情", "扭捏",
                    "嘿嘿", "嘻嘻", "娇羞", "含蓄", "不太好意思"),
            "thinking", List.of("让我想想", "我看看", "分析", "思考", "推理", "判断",
                    "研究", "考虑", "回忆", "对了", "嗯", "这个嘛",
                    "让我看看", "让我查查", "梳理", "整理", "复盘", "推敲",
                    "斟酌", "琢磨", "细想", "深思", "反思",
                    "为什么", "怎么", "是什么", "什么意思", "怎么办",
                    "哪个", "哪些", "多少", "几", "吗", "呢", "？", "?",
                    "能否", "可否", "是否", "有没有", "怎样", "如何"),
            "happy", List.of("好呀", "当然", "太好了", "开心", "成功", "可以",
                    "没问题", "喜欢", "棒", "厉害", "优秀", "不错", "赞",
                    "感谢", "谢谢", "高兴", "快乐", "幸福", "满足", "期待",
                    "加油", "冲", "耶", "好耶", "太棒", "真好", "真不错",
                    "完美", "漂亮", "精彩", "牛", "强", "酷", "帅",
                    "可爱", "甜", "温暖", "舒适", "享受", "满意", "欣慰")
    );

    /** 情绪表达式的优先级顺序（靠前的优先匹配） */
    private List<String> emotionPriority = List.of("sad", "angry", "surprised", "shy", "thinking", "happy");

    /** 强度调节关键词（匹配时增加基础强度） */
    private List<String> intensityBoosters = List.of("！", "!", "太", "非常", "特别", "真的");

    /** 基础强度值 */
    private double baseIntensity = 0.55;

    /** 匹配强度增幅 */
    private double intensityBoost = 0.25;

    /** 长文本强度衰减阈值（字符数） */
    private int longTextThreshold = 80;

    /** 长文本强度衰减值 */
    private double longTextPenalty = 0.1;

    /** 最小强度 */
    private double minIntensity = 0.35;

    /** 最大强度 */
    private double maxIntensity = 1.0;

    /** 最小持续时间（毫秒） */
    private int minDurationMs = 1800;

    /** 最大持续时间（毫秒） */
    private int maxDurationMs = 5200;

    /** 持续时间基础值 */
    private int durationBaseMs = 1400;

    /** 每字符持续时间增量 */
    private int durationPerCharMs = 55;
}
