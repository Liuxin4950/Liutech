package chat.liuxin.ai.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WritingToolEventSinkTest {
    @Test
    void repeatedToolCallsHaveUniqueIdsAndCanCompleteOutOfOrder() {
        List<Map<String, Object>> events = new ArrayList<>();
        WritingToolEventSink sink = new WritingToolEventSink((type, payload) -> {
            assertEquals("activity", type);
            events.add(payload);
        });
        String first = sink.fireStart("admin.listCategories", "读取现有分类", null);
        String second = sink.fireStart("admin.listCategories", "读取现有分类", null);
        assertNotEquals(first, second);
        sink.complete(second, "第二次读取完成");
        sink.complete(first, "第一次读取完成");
        assertEquals(List.of(first, second, second, first), events.stream().map(event -> event.get("activityId")).toList());
        assertEquals(List.of("running", "running", "completed", "completed"), events.stream().map(event -> event.get("status")).toList());
        assertEquals("reading_categories", events.getFirst().get("stage"));
        assertTrue(((Number) events.getLast().get("durationMs")).longValue() >= 0);
        sink.complete(first, "重复结束应被忽略");
        assertEquals(4, events.size());
    }

    @Test
    void cancellationFinishesEveryRunningOperationOnceAndDoesNotExposeInternalErrors() {
        List<Map<String, Object>> events = new ArrayList<>();
        WritingToolEventSink sink = new WritingToolEventSink((type, payload) -> events.add(payload));
        sink.modelRoundStarted();
        sink.fireStart("admin.listCategories", "读取分类", null);
        sink.fireError("admin.listCategories", "读取分类", "SQL access denied password=secret");
        sink.finishRunning("cancelled", "本轮已停止");
        sink.finishRunning("cancelled", "本轮已停止");
        assertEquals(4, events.size());
        assertEquals("failed", events.get(2).get("status"));
        assertEquals("cancelled", events.get(3).get("status"));
        assertFalse(events.toString().contains("password"));
        assertFalse(events.toString().contains("secret"));
    }

    @Test
    void preparingContentParametersIsObservedBeforeActualEditExecution() {
        List<Map<String, Object>> events = new ArrayList<>();
        WritingToolEventSink sink = new WritingToolEventSink((type, payload) -> events.add(payload));
        sink.modelRoundStarted();
        sink.preparingContentEdit();
        sink.preparingContentEdit();
        sink.modelRoundCompleted();
        String tool = sink.fireStart("admin.editArticleContent", "准备正文局部修改", "2 处");
        sink.complete(tool, "2 处修改待采纳");
        assertEquals(6, events.size());
        assertEquals("thinking", events.getFirst().get("stage"));
        assertEquals("editing_content", events.get(2).get("stage"));
        assertEquals("editing_content", events.get(4).get("stage"));
        assertNotEquals(events.get(2).get("activityId"), events.get(4).get("activityId"));
    }
}
