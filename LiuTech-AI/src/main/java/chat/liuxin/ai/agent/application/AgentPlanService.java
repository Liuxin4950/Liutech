package chat.liuxin.ai.agent.application;

import chat.liuxin.ai.agent.response.AgentPlanStep;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentPlanService {

    public List<AgentPlanStep> buildPlan(AgentIntent intent, boolean admin) {
        return switch (intent) {
            case IDENTITY -> List.of(
                    new AgentPlanStep("auth", "读取登录态和角色", "completed"),
                    new AgentPlanStep("respond", "按真实身份回答", "running"));
            case SEARCH_ARTICLES -> List.of(
                    new AgentPlanStep("understand", "理解搜索意图", "completed"),
                    new AgentPlanStep("search", "查询真实文章", "running"),
                    new AgentPlanStep("respond", "整理可点击结果", "pending"));
            case RECOMMEND_ARTICLES -> List.of(
                    new AgentPlanStep("context", "读取当前上下文", "completed"),
                    new AgentPlanStep("recommend", "获取推荐文章", "running"),
                    new AgentPlanStep("respond", "返回推荐理由和文章卡片", "pending"));
            case WRITE_ARTICLE -> List.of(
                    new AgentPlanStep("understand", "理解写作目标", "completed"),
                    new AgentPlanStep("context", "读取当前草稿", "running"),
                    new AgentPlanStep("taxonomy", "匹配分类和标签", "pending"),
                    new AgentPlanStep("html", "生成富文本 HTML", "pending"),
                    new AgentPlanStep("apply", "等待你选择如何应用", "pending"));
            case CREATE_DRAFT -> List.of(
                    new AgentPlanStep("draft", "生成结构化文章草稿", "running"),
                    new AgentPlanStep("html", "整理 TinyMCE HTML 正文", "pending"),
                    new AgentPlanStep("confirm", "等待管理员确认创建草稿", admin ? "pending" : "blocked"),
                    new AgentPlanStep("save", "保存为草稿", "pending"));
            case PUBLISH_POST, OFFLINE_POST -> List.of(
                    new AgentPlanStep("locate", "确认目标文章", "running"),
                    new AgentPlanStep("confirm", "等待管理员确认操作", admin ? "pending" : "blocked"),
                    new AgentPlanStep("execute", "执行状态变更", "pending"));
            case SUMMARIZE -> List.of(
                    new AgentPlanStep("context", "读取文章上下文", "running"),
                    new AgentPlanStep("summary", "生成总结", "pending"));
            default -> List.of(new AgentPlanStep("chat", "自然对话", "running"));
        };
    }
}
