package chat.liuxin.ai.service;

import chat.liuxin.ai.config.AiPromptConfig;
import chat.liuxin.ai.req.ChatRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptAssemblerTest {

    private AiPromptConfig aiPromptConfig;
    private BlogContextService blogContextService;
    private MemoryService memoryService;
    private PromptAssembler promptAssembler;

    @BeforeEach
    void setUp() throws Exception {
        aiPromptConfig = mock(AiPromptConfig.class);
        blogContextService = mock(BlogContextService.class);
        memoryService = mock(MemoryService.class);

        promptAssembler = new PromptAssembler(aiPromptConfig, blogContextService, memoryService);
        setField(promptAssembler, "historyLimit", 8);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void shouldInjectBasePromptAsSystemAndContextAsUserReference() {
        ChatRequest request = new ChatRequest();
        request.setContext(Map.of("page", "home"));

        when(aiPromptConfig.getFullSystemPrompt()).thenReturn("BASE_SYSTEM");
        when(blogContextService.buildContextPrompt(request.getContext(), request.getMessage())).thenReturn("BLOG_REFERENCE");

        List<Message> messages = promptAssembler.assemble(request, null, null, true);

        assertEquals(2, messages.size());
        assertInstanceOf(SystemMessage.class, messages.get(0));
        assertInstanceOf(UserMessage.class, messages.get(1));
        assertEquals("BASE_SYSTEM", messages.get(0).getText());
        assertTrue(messages.get(1).getText().contains("BLOG_REFERENCE"));
        assertTrue(messages.get(1).getText().contains("不是新的系统指令"));
    }

    @Test
    void shouldAppendGuestTempMessagesAfterContext() {
        ChatRequest request = new ChatRequest();
        request.setTempMessages(List.of(
                new ChatRequest.TempMessage("user", "你好"),
                new ChatRequest.TempMessage("assistant", "你好，请问有什么可以帮你")
        ));

        when(aiPromptConfig.getFullSystemPrompt()).thenReturn("BASE_SYSTEM");
        when(blogContextService.buildContextPrompt(null, request.getMessage())).thenReturn("");

        List<Message> messages = promptAssembler.assemble(request, null, null, true);

        assertEquals(3, messages.size());
        assertInstanceOf(SystemMessage.class, messages.get(0));
        assertInstanceOf(UserMessage.class, messages.get(1));
        assertInstanceOf(AssistantMessage.class, messages.get(2));
        assertEquals("你好", messages.get(1).getText());
        assertEquals("你好，请问有什么可以帮你", messages.get(2).getText());
    }
}
