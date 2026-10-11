package chat.liuxin.liutech.req;

import jakarta.validation.constraints.*;

/** AI 服务只上报身份快照和路由结果，不允许上报任意描述、请求体或错误原文。 */
public record AuditOperationReq(
        @NotNull @Positive Long operatorId,
        @NotBlank @Size(max = 100) String operator,
        @NotBlank @Pattern(regexp = "admin") String operatorRole,
        @NotBlank @Size(max = 240) @Pattern(regexp = "(?:/ai/admin|/admin)/[a-zA-Z0-9/_-]+") String path,
        @NotBlank @Pattern(regexp = "POST|PUT|PATCH|DELETE") String method,
        @NotNull @Min(0) @Max(1) Integer status,
        Integer failureCode,
        @Size(max = 50) String ip,
        @Size(max = 500) String userAgent) {}
