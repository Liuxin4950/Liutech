package chat.liuxin.ai.common.client;

import java.util.function.BooleanSupplier;

/** 服务端入口绑定的执行策略，不随工具结果或模型指令改变。 */
public record ModelExecutionPolicy(boolean failOnToolError, ModelExecutionObserver observer,
                                   BooleanSupplier completedResult, boolean finalRoundOnly) {
    public ModelExecutionPolicy(boolean failOnToolError, ModelExecutionObserver observer, BooleanSupplier completedResult) {
        this(failOnToolError, observer, completedResult, false);
    }
    public static final String CONTEXT_KEY = "modelExecutionPolicy";
    public boolean allowsEmptyCompletion() { return completedResult != null && completedResult.getAsBoolean(); }
}
