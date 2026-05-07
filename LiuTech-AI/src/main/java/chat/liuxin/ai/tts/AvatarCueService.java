package chat.liuxin.ai.tts;

import chat.liuxin.ai.agent.response.AvatarCuePayload;
import org.springframework.stereotype.Component;

/**
 * 基于文本语义的首版 Live2D cue 生成器。
 */
@Component
public class AvatarCueService {

    public AvatarCuePayload neutral(int seq, Long conversationId) {
        return AvatarCuePayload.builder()
                .seq(seq)
                .conversationId(conversationId)
                .expression("neutral")
                .motion(null)
                .intensity(0.0)
                .durationMs(0)
                .text("")
                .build();
    }

    public AvatarCuePayload fromText(int seq, Long conversationId, String text) {
        String source = text == null ? "" : text.trim();
        String normalized = source.toLowerCase();
        String expression = inferExpression(normalized);
        double intensity = inferIntensity(normalized, source.length());
        int durationMs = Math.max(1800, Math.min(5200, 1400 + source.length() * 55));

        return AvatarCuePayload.builder()
                .seq(seq)
                .conversationId(conversationId)
                .expression(expression)
                .motion(null)
                .intensity(intensity)
                .durationMs(durationMs)
                .text(source)
                .build();
    }

    private String inferExpression(String text) {
        // 伤心（强情绪优先）
        if (containsAny(text, "抱歉", "遗憾", "难过", "失败", "对不起", "可惜",
                "伤心", "委屈", "心疼", "后悔", "呜呜", "哭了", "泪目", "惋惜",
                "惨", "糟糕", "倒霉", "不幸", "痛苦", "失望", "沮丧", "消沉",
                "哎", "唉", "唉声叹气", "愁", "忧伤", "惆怅", "凄凉", "心酸")) {
            return "sad";
        }
        // 生气（强情绪优先）
        if (containsAny(text, "生气", "错误", "危险", "不应该", "拒绝", "禁止",
                "烦", "讨厌", "气死", "岂有此理", "荒唐", "离谱", "可恶",
                "愤怒", "不满", "抗议", "哼", "过分", "无语", "服了",
                "崩溃", "抓狂", "受不了", "忍无可忍", "太过分")) {
            return "angry";
        }
        // 惊讶（感叹词和意外发现）
        if (containsAny(text, "哇", "竟然", "原来", "没想到", "天哪", "不可思议",
                "居然", "真的吗", "不会吧", "不是吧", "啊", "咦", "诶",
                "震惊", "吃惊", "意外", "出乎意料", "万万没想到", "不敢相信",
                "厉害了", "了不起", "惊", "吓", "震撼")) {
            return "surprised";
        }
        // 害羞
        if (containsAny(text, "害羞", "不好意思", "悄悄", "小声", "脸红",
                "紧张", "忐忑", "矜持", "羞涩", "腼腆", "难为情", "扭捏",
                "嘿嘿", "嘻嘻", "娇羞", "含蓄", "不太好意思")) {
            return "shy";
        }
        // 思考（推理分析类）
        if (containsAny(text, "让我想想", "我看看", "分析", "思考", "推理", "判断",
                "研究", "考虑", "回忆", "对了", "嗯", "这个嘛",
                "让我看看", "让我查查", "梳理", "整理", "复盘", "推敲",
                "斟酌", "琢磨", "细想", "深思", "反思")) {
            return "thinking";
        }
        // 疑问（轻度思考）
        if (containsAny(text, "为什么", "怎么", "是什么", "什么意思", "怎么办",
                "哪个", "哪些", "多少", "几", "吗", "呢", "？", "?",
                "能否", "可否", "是否", "有没有", "怎样", "如何")) {
            return "thinking";
        }
        // 开心（兜底积极情绪）
        if (containsAny(text, "好呀", "当然", "太好了", "开心", "成功", "可以",
                "没问题", "喜欢", "棒", "厉害", "优秀", "不错", "赞",
                "感谢", "谢谢", "高兴", "快乐", "幸福", "满足", "期待",
                "加油", "冲", "耶", "好耶", "太棒", "真好", "真不错",
                "完美", "漂亮", "精彩", "牛", "强", "酷", "帅",
                "可爱", "甜", "温暖", "舒适", "享受", "满意", "欣慰")) {
            return "happy";
        }
        return "neutral";
    }

    private double inferIntensity(String text, int length) {
        double base = 0.55;
        if (containsAny(text, "！", "!", "太", "非常", "特别", "真的")) {
            base += 0.25;
        }
        if (length > 80) {
            base -= 0.1;
        }
        return Math.max(0.35, Math.min(1.0, base));
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null) return false;
        for (String needle : needles) {
            if (needle != null && !needle.isEmpty() && text.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
