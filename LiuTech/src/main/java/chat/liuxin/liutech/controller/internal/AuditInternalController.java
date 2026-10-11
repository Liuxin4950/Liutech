package chat.liuxin.liutech.controller.internal;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.req.AuditOperationReq;
import chat.liuxin.liutech.service.LogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 公网 Nginx 已屏蔽 /internal/**，内部令牌过滤器负责验证受信任调用方。 */
@RestController
@RequestMapping("/internal/audit")
@RequiredArgsConstructor
public class AuditInternalController {
    private final LogService logService;

    @PostMapping("/operations")
    public Result<Void> record(@Valid @RequestBody AuditOperationReq request) {
        logService.acceptAiOperation(request);
        return Result.success();
    }
}
