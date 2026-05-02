package chat.liuxin.ai.agent.application;

import chat.liuxin.ai.agent.request.AgentChatRequest;
import org.springframework.stereotype.Component;

@Component
public class AgentIntentClassifier {

    private static final String[] ARTICLE_TOPICS = {
            "spring boot", "spring ai", "typescript", "javascript", "docker", "kubernetes",
            "nginx", "mysql", "redis", "java", "vue", "react", "jwt", "agent", "ai", "vite", "maven"
    };

    public AgentIntent classify(AgentChatRequest request) {
        String message = request == null || request.getMessage() == null
                ? ""
                : request.getMessage().trim().toLowerCase();

        if (containsAny(message,
                "我是谁", "我是啥身份", "我是什么身份", "我的身份", "我是什么角色", "我的角色",
                "是否登录", "有没有登录", "登录了吗", "我是管理员", "我是不是管理员", "权限")) {
            return AgentIntent.IDENTITY;
        }
        if (containsAny(message, "下架", "下线", "撤下", "取消发布")) {
            return AgentIntent.OFFLINE_POST;
        }
        if (containsAny(message, "保存草稿", "创建草稿", "生成草稿")) {
            return AgentIntent.CREATE_DRAFT;
        }
        if (containsAny(message, "发布", "上线")
                && !containsAny(message, "不发布", "不要发布", "无需发布", "只保存草稿")) {
            return AgentIntent.PUBLISH_POST;
        }
        if (containsAny(message, "写一篇", "帮我写", "写博客", "写文章", "润色", "扩写", "摘要", "标题")) {
            return AgentIntent.WRITE_ARTICLE;
        }
        if (containsAny(message, "搜索", "查找", "找一下", "找找")) {
            return AgentIntent.SEARCH_ARTICLES;
        }
        if (containsAny(message, "推荐", "类似文章", "相关文章", "有什么文章", "想看", "想学", "学习", "了解")) {
            return AgentIntent.RECOMMEND_ARTICLES;
        }
        if (isArticleDiscoveryQuestion(message)) {
            return AgentIntent.SEARCH_ARTICLES;
        }
        if (containsAny(message, "总结", "概括", "讲了什么")) {
            return AgentIntent.SUMMARIZE;
        }
        return AgentIntent.CHAT;
    }

    private boolean isArticleDiscoveryQuestion(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (containsAny(text, "文章", "博客", "教程", "内容")
                && containsAny(text, "有", "有没有", "哪些", "什么", "相关")) {
            return true;
        }
        for (String topic : ARTICLE_TOPICS) {
            if (text.contains(topic)
                    && (containsAny(text, "有", "有没有", "文章", "相关", "推荐")
                    || text.length() <= topic.length() + 6)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
