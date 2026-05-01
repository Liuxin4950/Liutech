package chat.liuxin.ai.agent.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ArticleResultsPayload {
    private String source;
    private String query;
    private String reason;
    private List<ArticleResultItem> items;
}
