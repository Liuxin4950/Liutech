package chat.liuxin.ai.controller;

import chat.liuxin.ai.dto.ModelConfigDTO;
import chat.liuxin.ai.service.AiModelConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * AI模型公开控制器
 * 提供给用户前端使用的公开API（无需管理员权限）
 *
 * @author 刘鑫
 * @since 2025-01-18
 *
 * 功能说明：
 * 获取默认模型：用户前端自动使用管理员设置的默认模型进行AI对话
 *
 * 路由说明：
 * - GET /ai/models/default 获取默认模型名称
 */
@Slf4j
@RestController
@RequestMapping("/ai/models")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AiModelController {

    private final AiModelConfigService modelConfigService;

    /**
     * 获取默认模型
     *
     * 业务说明：返回管理员设置的默认模型名称，供用户前端使用
     * 如果未设置默认模型，返回系统默认模型 "zai-org/GLM-4.6"
     *
     * @return 默认模型的模型名称
     */
    @GetMapping("/default")
    public String getDefaultModel() {
        log.info("用户前端获取默认模型");
        Optional<ModelConfigDTO> defaultModel = modelConfigService.getDefaultModel();
        if (defaultModel.isEmpty()) {
            log.warn("未设置默认模型，返回系统默认模型: zai-org/GLM-4.6");
            return "zai-org/GLM-4.6";
        }
        log.info("返回默认模型: {}", defaultModel.get().getModelName());
        return defaultModel.get().getModelName();
    }
}
