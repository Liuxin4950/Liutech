package chat.liuxin.ai.service;

import chat.liuxin.ai.entity.AiConversation;
import chat.liuxin.ai.mapper.AiChatMessageMapper;
import chat.liuxin.ai.mapper.AiConversationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MemoryServiceTest {
    private AiChatMessageMapper messages;
    private AiConversationMapper conversations;
    private MemoryService service;

    @BeforeEach void setup() {
        messages = mock(AiChatMessageMapper.class);
        conversations = mock(AiConversationMapper.class);
        service = new MemoryService(messages, conversations, mock(chat.liuxin.ai.mapper.AiUserStateMapper.class));
    }

    @Test void rejectedWritesMustHaveNoSideEffectsIncludingErrorPlaceholder() {
        AiConversation victim = new AiConversation();
        victim.setId(1L);
        victim.setUserId("victim");
        when(conversations.selectForUpdate(1L)).thenReturn(victim);
        assertThrows(ResponseStatusException.class,
                () -> service.saveUserMessage("attacker", 1L, "input", "model", null));
        assertThrows(ResponseStatusException.class,
                () -> service.saveAssistantMessage("attacker", 1L, null, "model", 3, null));
        verifyNoInteractions(messages);
        verify(conversations, never()).incrementMessageCount(anyLong());
    }

    @Test void zeroHistoryMustStillValidateOwner() {
        AiConversation victim = new AiConversation();
        victim.setUserId("victim");
        when(conversations.selectById(1L)).thenReturn(victim);
        assertThrows(ResponseStatusException.class,
                () -> service.listLastMessagesAsPromptMessages("attacker", 1L, 0));
        verifyNoInteractions(messages);
    }

    @Test void pageOffsetUsesClampedSizeAndDoesNotOverflow() {
        when(conversations.selectVisiblePage(anyString(), anyLong(), anyInt())).thenReturn(List.of());
        service.listConversations("owner", null, 2, 500);
        verify(conversations).selectVisiblePage("owner", 100L, 100);
        service.listConversations("owner", null, Integer.MAX_VALUE, 500);
        verify(conversations).selectVisiblePage("owner", 214748364600L, 100);
        service.listConversations("owner", null, -10, -10);
        verify(conversations).selectVisiblePage("owner", 0L, 1);
    }
}
