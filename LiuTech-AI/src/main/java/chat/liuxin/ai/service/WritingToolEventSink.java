package chat.liuxin.ai.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BiConsumer;

/** 请求作用域的真实写作活动；工具、准备与模型轮次共用同一协议，不推测模型内部思维。 */
public final class WritingToolEventSink {
    public static final String CONTEXT_KEY = "writingToolEventSink";

    private final BiConsumer<String, Map<String, Object>> eventSender;
    private final Map<String, Activity> active = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentLinkedDeque<String>> toolActivities = new ConcurrentHashMap<>();
    private String modelActivity;
    private String preparingContentActivity;
    private long failures;

    public WritingToolEventSink(BiConsumer<String, Map<String, Object>> eventSender) {
        this.eventSender = eventSender;
    }

    public synchronized String start(String stage, String message) {
        return start(stage, message, null, null);
    }

    public synchronized String fireStart(String toolName, String displayName, String inputSummary) {
        String id = start(stageForTool(toolName), "AI 正在" + displayName, toolName, inputSummary);
        toolActivities.computeIfAbsent(toolName, ignored -> new ConcurrentLinkedDeque<>()).addLast(id);
        return id;
    }

    /** 兼容现有工具入口；新调用方应使用返回的 activityId 精确配对。 */
    public synchronized void fireSuccess(String toolName, String displayName, String resultSummary) {
        String id = pollTool(toolName);
        if (id != null) complete(id, resultSummary == null ? displayName + "完成" : resultSummary);
    }

    public synchronized void fireError(String toolName, String displayName, String ignoredTechnicalError) {
        String id = pollTool(toolName);
        if (id != null) fail(id, displayName + "失败，本轮修改未应用");
    }

    public synchronized void complete(String activityId, String message) {
        finish(activityId, "completed", message);
    }

    public synchronized void fail(String activityId, String message) {
        finish(activityId, "failed", message);
    }

    public synchronized long failureCount() { return failures; }

    /** 参数转换/范围校验可能在工具方法发 start 前失败；如实记录失败的请求校验。 */
    public synchronized void rejectedTool(String toolName, String message) {
        String id = start(stageForTool(toolName), "正在校验写作工具请求", toolName, null);
        fail(id, message);
    }

    public synchronized void finishRunning(String status, String message) {
        for (String id : active.keySet().toArray(String[]::new)) finish(id, status, message);
        toolActivities.clear();
        modelActivity = null;
        preparingContentActivity = null;
    }

    public synchronized void modelRoundStarted() {
        modelActivity = start("thinking", "AI 正在理解草稿并准备回复");
    }

    /** 原生工具流已出现正文编辑工具名，才展示参数准备；此时尚未修改草稿。 */
    public synchronized void preparingContentEdit() {
        if (preparingContentActivity != null) return;
        if (modelActivity != null) complete(modelActivity, "AI 已开始准备正文修改");
        modelActivity = null;
        preparingContentActivity = start("editing_content", "AI 正在准备正文局部修改");
    }

    public synchronized void modelRoundCompleted() {
        if (modelActivity != null) complete(modelActivity, "模型本轮响应已返回");
        if (preparingContentActivity != null) complete(preparingContentActivity, "正文修改参数已生成");
        modelActivity = null;
        preparingContentActivity = null;
    }

    private String start(String stage, String message, String toolName, String inputSummary) {
        String id = UUID.randomUUID().toString();
        Activity activity = new Activity(id, stage, toolName, System.currentTimeMillis(), System.nanoTime());
        active.put(id, activity);
        Map<String, Object> payload = payload(activity, "running", message);
        if (inputSummary != null && !inputSummary.isBlank()) payload.put("inputSummary", safeSummary(inputSummary));
        eventSender.accept("activity", payload);
        return id;
    }

    private void finish(String id, String status, String message) {
        Activity activity = active.remove(id);
        if (activity == null) return;
        if ("failed".equals(status)) failures++;
        if (activity.toolName != null) {
            var queue = toolActivities.get(activity.toolName);
            if (queue != null) queue.remove(id);
        }
        Map<String, Object> payload = payload(activity, status, message);
        payload.put("finishedAt", System.currentTimeMillis());
        payload.put("durationMs", Math.max(0L, (System.nanoTime() - activity.startedNanos) / 1_000_000L));
        eventSender.accept("activity", payload);
    }

    private Map<String, Object> payload(Activity activity, String status, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("activityId", activity.id);
        payload.put("stage", activity.stage);
        payload.put("status", status);
        payload.put("message", safeSummary(message));
        payload.put("startedAt", activity.startedAt);
        if (activity.toolName != null) payload.put("toolName", activity.toolName);
        return payload;
    }

    private String pollTool(String toolName) {
        var queue = toolActivities.get(toolName);
        return queue == null ? null : queue.pollFirst();
    }

    private static String safeSummary(String message) {
        if (message == null || message.isBlank()) return "本轮操作状态已更新";
        return message.length() > 200 ? message.substring(0, 200) + "…" : message;
    }

    private static String stageForTool(String toolName) {
        if (toolName.endsWith("listCategories")) return "reading_categories";
        if (toolName.endsWith("listTags")) return "reading_tags";
        if (toolName.endsWith("getArticleDetail")) return "reading_article";
        if (toolName.endsWith("editArticleContent")) return "editing_content";
        return "updating_fields";
    }

    private record Activity(String id, String stage, String toolName, long startedAt, long startedNanos) {}
}
