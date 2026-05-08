package chat.liuxin.ai.controller.admin;

import chat.liuxin.ai.aspect.OperationLog;
import chat.liuxin.ai.dto.ModelConfigDTO;
import chat.liuxin.ai.dto.ModelConfigRequest;
import chat.liuxin.ai.dto.ModelUsageStats;
import chat.liuxin.ai.service.AiModelConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * AI模型管理控制器（管理员专用）
 * 提供模型配置的完整 CRUD API
 *
 * @author 刘鑫
 * @since 2025-01-18
 *
 * 功能说明：
 * 1. 模型配置管理：添加、编辑、删除、启用/禁用模型
 * 2. 默认模型设置：设置用户前端使用的默认模型
 * 3. 使用统计：查看今日各模型的使用次数
 *
 * 路由说明：
 * - GET    /admin/models/list        获取所有模型配置
 * - GET    /admin/models/enabled     获取所有启用的模型
 * - GET    /admin/models/default     获取默认模型
 * - GET    /admin/models/{id}        根据ID获取模型
 * - POST   /admin/models            添加新模型
 * - PUT    /admin/models/{id}        更新模型配置
 * - DELETE /admin/models/{id}        删除模型配置
 * - PUT    /admin/models/{id}/default 设置为默认模型
 * - PUT    /admin/models/{id}/toggle 切换启用状态
 * - GET    /admin/models/usage/today 获取今日使用统计
 */
@Slf4j
@RestController
@RequestMapping({"/admin/models", "/ai/admin/models"})
@PreAuthorize("hasRole('ADMIN')")
@Validated
@RequiredArgsConstructor
public class AiModelAdminController {

    private final AiModelConfigService modelConfigService;

    /**
     * 获取所有模型配置列表
     *
     * @return 模型配置列表
     */
    @GetMapping("/list")
    public List<ModelConfigDTO> getAllModels() {
        log.info("管理员获取所有模型配置列表");
        return modelConfigService.getAllModels();
    }

    /**
     * 获取所有启用的模型
     *
     * @return 启用的模型列表
     */
    @GetMapping("/enabled")
    public List<ModelConfigDTO> getEnabledModels() {
        log.info("管理员获取所有启用的模型");
        return modelConfigService.getEnabledModels();
    }

    /**
     * 获取默认模型
     *
     * @return 默认模型配置
     */
    @GetMapping("/default")
    public Optional<ModelConfigDTO> getDefaultModel() {
        log.info("管理员获取默认模型");
        return modelConfigService.getDefaultModel();
    }

    /**
     * 根据ID获取模型配置
     *
     * @param id 模型ID
     * @return 模型配置
     */
    @GetMapping("/{id}")
    public ModelConfigDTO getModelById(@PathVariable Long id) {
        log.info("管理员获取模型配置，ID: {}", id);
        return modelConfigService.getModelById(id);
    }

    /**
     * 添加新模型配置
     *
     * @param request 模型配置请求
     * @return 创建的模型配置
     */
    @PostMapping
    @OperationLog(action = "create", targetType = "ai_model", description = "添加AI模型")
    public ModelConfigDTO addModel(@Valid @RequestBody ModelConfigRequest request) {
        log.info("管理员添加新模型，模型名称: {}", request.getModelName());
        return modelConfigService.addModel(request);
    }

    /**
     * 更新模型配置
     *
     * @param id      模型ID
     * @param request 模型配置请求
     * @return 更新后的模型配置
     */
    @PutMapping("/{id}")
    @OperationLog(action = "update", targetType = "ai_model", description = "更新AI模型")
    public ModelConfigDTO updateModel(
            @PathVariable Long id,
            @Valid @RequestBody ModelConfigRequest request) {
        log.info("管理员更新模型，ID: {}, 模型名称: {}", id, request.getModelName());
        return modelConfigService.updateModel(id, request);
    }

    /**
     * 删除模型配置
     *
     * @param id 模型ID
     */
    @DeleteMapping("/{id}")
    @OperationLog(action = "delete", targetType = "ai_model", description = "删除AI模型")
    public void deleteModel(@PathVariable Long id) {
        log.info("管理员删除模型，ID: {}", id);
        modelConfigService.deleteModel(id);
    }

    /**
     * 设置默认模型
     * 业务说明：先取消所有模型的默认状态，然后将指定模型设置为默认
     *
     * @param id 模型ID
     */
    @PutMapping("/{id}/default")
    @OperationLog(action = "update", targetType = "ai_model", description = "设置默认AI模型")
    public void setDefaultModel(@PathVariable Long id) {
        log.info("管理员设置默认模型，ID: {}", id);
        modelConfigService.setDefaultModel(id);
    }

    /**
     * 切换模型启用状态
     *
     * @param id      模型ID
     * @param enabled 是否启用
     */
    @PutMapping("/{id}/toggle")
    @OperationLog(action = "update", targetType = "ai_model", description = "切换AI模型启用状态")
    public void toggleEnabled(
            @PathVariable Long id,
            @RequestParam boolean enabled) {
        log.info("管理员切换模型启用状态，ID: {}, 启用: {}", id, enabled);
        modelConfigService.toggleEnabled(id, enabled);
    }

    /**
     * 获取今天模型使用统计
     *
     * @return 模型使用统计列表
     */
    @GetMapping("/usage/today")
    public List<ModelUsageStats> getTodayModelUsage() {
        log.info("管理员获取今日模型使用统计");
        return modelConfigService.getTodayModelUsage();
    }
}
