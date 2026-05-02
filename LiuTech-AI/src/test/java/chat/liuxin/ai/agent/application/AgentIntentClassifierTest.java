package chat.liuxin.ai.agent.application;

import chat.liuxin.ai.agent.request.AgentChatRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentIntentClassifierTest {

    private final AgentIntentClassifier classifier = new AgentIntentClassifier();

    @Test
    void shouldClassifyAdminWriteIntents() {
        AgentChatRequest request = new AgentChatRequest();
        request.setMessage("帮我写一篇 Spring AI Agent 的文章并保存草稿");

        assertEquals(AgentIntent.CREATE_DRAFT, classifier.classify(request));
    }

    @Test
    void shouldClassifySearchAndStatusIntents() {
        AgentChatRequest identity = new AgentChatRequest();
        identity.setMessage("我是啥身份");
        assertEquals(AgentIntent.IDENTITY, classifier.classify(identity));

        AgentChatRequest search = new AgentChatRequest();
        search.setMessage("找一下 JWT 相关的文章");
        assertEquals(AgentIntent.SEARCH_ARTICLES, classifier.classify(search));

        AgentChatRequest topicQuestion = new AgentChatRequest();
        topicQuestion.setMessage("有java的吗");
        assertEquals(AgentIntent.SEARCH_ARTICLES, classifier.classify(topicQuestion));

        AgentChatRequest topicRecommendation = new AgentChatRequest();
        topicRecommendation.setMessage("我在学习docker你有推荐的吗");
        assertEquals(AgentIntent.RECOMMEND_ARTICLES, classifier.classify(topicRecommendation));

        AgentChatRequest publish = new AgentChatRequest();
        publish.setMessage("发布这篇文章");
        assertEquals(AgentIntent.PUBLISH_POST, classifier.classify(publish));

        AgentChatRequest draftOnly = new AgentChatRequest();
        draftOnly.setMessage("请创建草稿，只保存草稿，不发布");
        assertEquals(AgentIntent.CREATE_DRAFT, classifier.classify(draftOnly));

        AgentChatRequest offline = new AgentChatRequest();
        offline.setMessage("下架这篇文章");
        assertEquals(AgentIntent.OFFLINE_POST, classifier.classify(offline));
    }
}
