package chat.liuxin.ai.common.client;

import chat.liuxin.ai.dto.PostDetailDTO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 锁定 AI 实际消费的主服务文章响应字段。 */
class BlogApiClientContractTest {

    @Test
    void shouldParseRequiredPostDetailFieldsFromMainServiceEnvelope() throws Exception {
        BackendApiTransport transport = mock(BackendApiTransport.class);
        JsonNode root = new ObjectMapper().readTree("""
                {"code":200,"message":"ok","data":{
                  "id":12,"title":"标题","content":"正文","summary":"摘要",
                  "viewCount":3,"likeCount":2,"commentCount":1,"createdAt":"2026-09-13 12:00:00",
                  "category":{"name":"Java"},"author":{"username":"liuxin"},
                  "tags":[{"name":"Spring"}]
                }}
                """);
        when(transport.getJson("/posts/12")).thenReturn(root);
        when(transport.extractData(root)).thenReturn(root.get("data"));

        PostDetailDTO dto = new BlogApiClient(transport).getPostDetail(12L);

        assertEquals("标题", dto.getTitle());
        assertEquals("正文", dto.getContent());
        assertEquals("Java", dto.getCategoryName());
        assertEquals("liuxin", dto.getAuthorName());
        assertEquals("Spring", dto.getTags().getFirst());
    }
}
