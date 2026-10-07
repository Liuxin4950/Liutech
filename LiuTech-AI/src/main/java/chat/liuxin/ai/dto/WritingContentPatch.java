package chat.liuxin.ai.dto;

import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/** 正文局部建议。所有锚点均来自本轮原稿；不包含未修改的正文。 */
public record WritingContentPatch(String baseRevision, List<Edit> edits) {
    public WritingContentPatch {
        edits = List.copyOf(edits);
    }

    public record Edit(
            @ToolParam(description = "从本轮原稿逐字复制的完整 HTML 段落或相邻段落，必须唯一匹配") String before,
            @ToolParam(description = "替换后的完整 HTML 片段；空字符串表示删除该片段") String after) { }
}
