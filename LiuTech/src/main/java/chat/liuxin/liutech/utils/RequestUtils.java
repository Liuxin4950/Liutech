package chat.liuxin.liutech.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * HTTP 请求工具类
 * 提供请求相关的公共方法，供 Filter / Aspect / Controller 复用。
 *
 * @author 刘鑫
 */
public final class RequestUtils {

    private RequestUtils() {}

    /**
     * 获取客户端真实 IP 地址（兼容多层反向代理）。
     *
     * 优先级：X-Forwarded-For（取第一个） > X-Real-IP > Proxy-Client-IP > WL-Proxy-Client-IP > getRemoteAddr()
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        String ip = request.getHeader("X-Forwarded-For");
        if (isUsableIp(ip)) {
            // 多次反向代理后会有多个 IP，第一个才是真实 IP
            return ip.split(",")[0].trim();
        }

        ip = request.getHeader("X-Real-IP");
        if (isUsableIp(ip)) {
            return ip;
        }

        ip = request.getHeader("Proxy-Client-IP");
        if (isUsableIp(ip)) {
            return ip;
        }

        ip = request.getHeader("WL-Proxy-Client-IP");
        if (isUsableIp(ip)) {
            return ip;
        }

        ip = request.getHeader("HTTP_CLIENT_IP");
        if (isUsableIp(ip)) {
            return ip;
        }

        ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        if (isUsableIp(ip)) {
            return ip.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    private static boolean isUsableIp(String ip) {
        return ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip);
    }
}
