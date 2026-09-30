package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AiUserDataClientTest {
    private AiUserDataClient client;
    private MockRestServiceServer server() {
        client = new AiUserDataClient();
        ReflectionTestUtils.setField(client, "aiServiceUrl", "http://ai.test");
        ReflectionTestUtils.setField(client, "internalToken", "local-test-only");
        return MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(client, "restTemplate")).build();
    }

    @Test void permanentCleanupRequiresMatchingConfirmation() {
        var server = server();
        server.expect(requestTo("http://ai.test/ai/internal/users/7/permanent-data"))
                .andExpect(header("X-LiuTech-Internal-Token", "local-test-only"))
                .andRespond(withSuccess("{\"userId\":7,\"conversationsDeleted\":1,\"messagesDeleted\":2,\"permanentlyPurged\":true}", MediaType.APPLICATION_JSON));
        assertDoesNotThrow(() -> client.purgeUser(7L));
        server.verify();
    }

    @Test void legacyOrWrongUserResponseCannotCompleteTask() {
        var server = server();
        server.expect(requestTo("http://ai.test/ai/internal/users/7/permanent-data"))
                .andRespond(withSuccess("{\"userId\":7,\"conversationsDeleted\":0,\"messagesDeleted\":0}", MediaType.APPLICATION_JSON));
        assertThrows(BusinessException.class, () -> client.purgeUser(7L));
        server.verify();
    }
}
