package chat.liuxin.ai.common.client;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthIntrospectionClientTest {

    @Test
    void shouldParseAndCacheSuccessfulIntrospection() throws Exception {
        BackendApiTransport transport = mock(BackendApiTransport.class);
        JsonNode root = new ObjectMapper().readTree("{\"code\":200,\"data\":{\"userId\":9,\"username\":\"u\",\"role\":\"user\"}}");
        when(transport.introspect("token")).thenReturn(root);
        when(transport.extractData(root)).thenReturn(root.get("data"));
        AuthIntrospectionClient client = new AuthIntrospectionClient(transport);

        assertEquals(9L, client.introspect("token").userId());
        assertEquals("u", client.introspect("token").username());
        verify(transport, times(1)).introspect("token");
    }

    @Test
    void incompleteResponseShouldBeRejected() throws Exception {
        BackendApiTransport transport = mock(BackendApiTransport.class);
        JsonNode root = new ObjectMapper().readTree("{\"code\":200,\"data\":{\"userId\":9}}");
        when(transport.introspect("token")).thenReturn(root);
        when(transport.extractData(root)).thenReturn(root.get("data"));

        assertThrows(BackendApiTransport.InvalidUserTokenException.class,
                () -> new AuthIntrospectionClient(transport).introspect("token"));
    }
}
