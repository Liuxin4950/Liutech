package chat.liuxin.ai.common.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Web 请求工具
 */
public final class WebUtils {

    private WebUtils() {}

    /**
     * 判断是否 SSE 请求：Accept 含 text/event-stream 且 URI 含 /stream。
     * 用途：SSE 响应已开始流式输出后，异常处理器不能再写 JSON，否则破坏 event-stream 格式。
     */
    public static boolean isSseRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String uri = request.getRequestURI();
        return accept != null && accept.contains("text/event-stream")
                && uri != null && uri.contains("/stream");
    }
}
