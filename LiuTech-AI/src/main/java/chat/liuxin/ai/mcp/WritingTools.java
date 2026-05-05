package chat.liuxin.ai.mcp;

import chat.liuxin.ai.client.BlogApiClient;
import chat.liuxin.ai.dto.CategoryDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 写作专用 MCP 工具。
 *
 * 使用公开 API 获取分类和标签，不需要管理员 token。
 * AI 在写作流程中通过这些工具实时获取博客分类/标签数据并自主选择。
 */
@Slf4j
@Component
public class WritingTools {

    private final BlogApiClient blogApiClient;

    public WritingTools(BlogApiClient blogApiClient) {
        this.blogApiClient = blogApiClient;
    }

    @Tool(description = "获取博客所有分类列表（含ID和名称），用于为文章选择最合适的分类。返回分类ID、名称和描述。")
    public List<CategoryDTO> listCategories() {
        log.debug("写作工具调用: listCategories");
        return blogApiClient.getAllCategories();
    }

    @Tool(description = "获取博客所有标签列表（含ID和名称），用于为文章选择最合适的标签（1-6个）。返回标签ID和名称。")
    public List<Object> listTags() {
        log.debug("写作工具调用: listTags");
        // 复用公开 API: GET /tags
        return blogApiClient.getAllTags();
    }
}
