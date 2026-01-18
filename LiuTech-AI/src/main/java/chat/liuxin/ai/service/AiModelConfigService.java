package chat.liuxin.ai.service;

import chat.liuxin.ai.dto.ModelConfigDTO;
import chat.liuxin.ai.dto.ModelConfigRequest;
import chat.liuxin.ai.dto.ModelUsageStats;
import chat.liuxin.ai.entity.AiModelConfig;
import chat.liuxin.ai.mapper.AiChatMessageMapper;
import chat.liuxin.ai.mapper.AiModelConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AI模型配置服务
 * 提供模型配置的完整 CRUD 功能
 *
 * 主要职责：
 * 1. 管理AI模型配置：添加、编辑、删除、启用/禁用模型
 * 2. 默认模型管理：设置和获取用户前端使用的默认模型
 * 3. 使用统计分析：统计今日各模型的使用次数
 *
 * 业务位置：
 * 位于AI服务层，为管理后台提供模型管理功能
 *
 * 核心功能点：
 * 1. 模型配置的增删改查
 * 2. 设置默认模型（保证只有一个默认）
 * 3. 启用/禁用模型控制
 * 4. 今日模型使用统计查询
 *
 * @author 刘鑫
 * @since 2025-01-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelConfigService {

    private final AiModelConfigMapper modelConfigMapper;
    private final AiChatMessageMapper chatMessageMapper;

    /**
     * 获取所有模型配置列表
     *
     * @return 模型配置列表
     */
    public List<ModelConfigDTO> getAllModels() {
        log.debug("获取所有模型配置列表");
        List<AiModelConfig> configs = modelConfigMapper.selectList(null);
        return configs.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有启用的模型
     *
     * @return 启用的模型列表
     */
    public List<ModelConfigDTO> getEnabledModels() {
        log.debug("获取所有启用的模型");
        List<AiModelConfig> configs = modelConfigMapper.selectEnabledModels();
        return configs.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取默认模型
     *
     * @return 默认模型，可能为空
     */
    public Optional<ModelConfigDTO> getDefaultModel() {
        log.debug("获取默认模型");
        return modelConfigMapper.selectDefaultModel()
                .map(this::toDTO);
    }

    /**
     * 根据模型名称获取配置
     *
     * @param modelName 模型名称
     * @return 模型配置，不存在时返回 Optional.empty()
     */
    public Optional<ModelConfigDTO> getModelByName(String modelName) {
        log.debug("根据模型名称获取配置，模型名称: {}", modelName);
        AiModelConfig config = modelConfigMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiModelConfig>()
                        .eq(AiModelConfig::getModelName, modelName)
        );
        return Optional.ofNullable(config).map(this::toDTO);
    }

    /**
     * 根据ID获取模型配置
     *
     * @param id 模型ID
     * @return 模型配置
     */
    public ModelConfigDTO getModelById(Long id) {
        log.debug("根据ID获取模型配置，ID: {}", id);
        AiModelConfig config = modelConfigMapper.selectById(id);
        if (config == null) {
            log.warn("模型配置不存在，ID: {}", id);
            throw new RuntimeException("模型配置不存在");
        }
        return toDTO(config);
    }

    /**
     * 添加新模型配置
     *
     * @param request 模型配置请求
     * @return 创建的模型配置
     */
    public ModelConfigDTO addModel(ModelConfigRequest request) {
        log.info("添加新模型配置，模型名称: {}", request.getModelName());

        // 检查模型名称是否已存在
        AiModelConfig existing = modelConfigMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiModelConfig>()
                        .eq(AiModelConfig::getModelName, request.getModelName())
        );
        if (existing != null) {
            log.warn("模型名称已存在: {}", request.getModelName());
            throw new RuntimeException("模型名称已存在");
        }

        AiModelConfig config = new AiModelConfig();
        config.setModelName(request.getModelName());
        config.setDisplayName(request.getDisplayName());
        config.setProvider(request.getProvider());
        config.setIsEnabled(request.getIsEnabled());
        config.setIsDefault(false);
        config.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        config.setMaxTokens(request.getMaxTokens());
        config.setTemperature(request.getTemperature());
        config.setDescription(request.getDescription());

        modelConfigMapper.insert(config);
        log.info("新模型配置添加成功，ID: {}, 模型名称: {}", config.getId(), config.getModelName());
        return toDTO(config);
    }

    /**
     * 更新模型配置
     *
     * @param id      模型ID
     * @param request 模型配置请求
     * @return 更新后的模型配置
     */
    public ModelConfigDTO updateModel(Long id, ModelConfigRequest request) {
        log.info("更新模型配置，ID: {}, 模型名称: {}", id, request.getModelName());

        AiModelConfig config = modelConfigMapper.selectById(id);
        if (config == null) {
            log.warn("模型配置不存在，ID: {}", id);
            throw new RuntimeException("模型配置不存在");
        }

        // 检查模型名称是否与其他模型冲突
        AiModelConfig existing = modelConfigMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiModelConfig>()
                        .eq(AiModelConfig::getModelName, request.getModelName())
                        .ne(AiModelConfig::getId, id)
        );
        if (existing != null) {
            log.warn("模型名称已被其他模型使用: {}", request.getModelName());
            throw new RuntimeException("模型名称已被其他模型使用");
        }

        config.setModelName(request.getModelName());
        config.setDisplayName(request.getDisplayName());
        config.setProvider(request.getProvider());
        config.setIsEnabled(request.getIsEnabled());
        config.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        config.setMaxTokens(request.getMaxTokens());
        config.setTemperature(request.getTemperature());
        config.setDescription(request.getDescription());

        modelConfigMapper.updateById(config);
        log.info("模型配置更新成功，ID: {}", id);
        return toDTO(config);
    }

    /**
     * 删除模型配置
     *
     * @param id 模型ID
     */
    public void deleteModel(Long id) {
        log.info("删除模型配置，ID: {}", id);

        AiModelConfig config = modelConfigMapper.selectById(id);
        if (config == null) {
            log.warn("模型配置不存在，ID: {}", id);
            throw new RuntimeException("模型配置不存在");
        }

        // 不允许删除默认模型
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            log.warn("不能删除默认模型，ID: {}", id);
            throw new RuntimeException("不能删除默认模型，请先设置其他模型为默认");
        }

        modelConfigMapper.deleteById(id);
        log.info("模型配置删除成功，ID: {}", id);
    }

    /**
     * 设置默认模型
     * 先取消所有模型的默认状态，然后设置指定模型为默认
     *
     * @param id 模型ID
     */
    @Transactional
    public void setDefaultModel(Long id) {
        log.info("设置默认模型，ID: {}", id);

        AiModelConfig config = modelConfigMapper.selectById(id);
        if (config == null) {
            log.warn("模型配置不存在，ID: {}", id);
            throw new RuntimeException("模型配置不存在");
        }

        // 确保模型是启用的
        if (!Boolean.TRUE.equals(config.getIsEnabled())) {
            log.warn("不能将禁用的模型设置为默认，ID: {}", id);
            throw new RuntimeException("不能将禁用的模型设置为默认");
        }

        // 取消所有模型的默认状态
        modelConfigMapper.clearAllDefault();

        // 设置指定模型为默认
        modelConfigMapper.setAsDefault(id);
        log.info("默认模型设置成功，ID: {}, 模型名称: {}", id, config.getModelName());
    }

    /**
     * 切换模型启用状态
     *
     * @param id       模型ID
     * @param enabled  是否启用
     */
    public void toggleEnabled(Long id, boolean enabled) {
        log.info("切换模型启用状态，ID: {}, 启用: {}", id, enabled);

        AiModelConfig config = modelConfigMapper.selectById(id);
        if (config == null) {
            log.warn("模型配置不存在，ID: {}", id);
            throw new RuntimeException("模型配置不存在");
        }

        // 禁用默认模型时需要检查
        if (!enabled && Boolean.TRUE.equals(config.getIsDefault())) {
            log.warn("不能禁用默认模型，ID: {}", id);
            throw new RuntimeException("不能禁用默认模型，请先设置其他模型为默认");
        }

        config.setIsEnabled(enabled);
        modelConfigMapper.updateById(config);
        log.info("模型启用状态切换成功，ID: {}, 启用: {}", id, enabled);
    }

    /**
     * 获取今天模型使用统计
     *
     * @return 模型使用统计列表
     */
    public List<ModelUsageStats> getTodayModelUsage() {
        log.debug("获取今天模型使用统计");

        List<Map<String, Object>> results = chatMessageMapper.selectTodayModelUsage();
        List<ModelUsageStats> stats = results.stream()
                .map(row -> ModelUsageStats.builder()
                        .model((String) row.get("model"))
                        .usageCount(((Number) row.get("usageCount")).longValue())
                        .build())
                .collect(Collectors.toList());

        log.info("今日模型使用统计查询完成，模型数量: {}", stats.size());
        return stats;
    }

    /**
     * 将实体转换为 DTO
     *
     * @param entity 实体对象
     * @return DTO 对象
     */
    private ModelConfigDTO toDTO(AiModelConfig entity) {
        ModelConfigDTO dto = new ModelConfigDTO();
        dto.setId(entity.getId());
        dto.setModelName(entity.getModelName());
        dto.setDisplayName(entity.getDisplayName());
        dto.setProvider(entity.getProvider());
        dto.setIsEnabled(entity.getIsEnabled());
        dto.setIsDefault(entity.getIsDefault());
        dto.setSortOrder(entity.getSortOrder());
        dto.setMaxTokens(entity.getMaxTokens());
        dto.setTemperature(entity.getTemperature());
        dto.setDescription(entity.getDescription());
        return dto;
    }
}
