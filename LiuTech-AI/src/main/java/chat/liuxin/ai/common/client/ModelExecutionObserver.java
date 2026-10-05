package chat.liuxin.ai.common.client;

import java.util.List;

/** 真实执行活动观察接口；不传递模型思维，也不依赖 SSE 或具体业务。 */
public interface ModelExecutionObserver {
    default void modelRoundStarted() {}
    default void modelToolsPrepared(List<String> names) {}
    default void modelRoundCompleted() {}
    default long failureCount() { return 0; }
    default void rejectedTool(String name, String message) {}
    default void toolStarted(String name, String input) {}
    default void toolCompleted(String name) {}
    default void toolResultDelivered(String name, String delivered, boolean truncated) {}
}
