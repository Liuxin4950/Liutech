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
     * 拉全部模型配置(启用+禁用),供管理后台管理页展示。
     */
    public List<ModelConfigDTO> getAllModels() {
        log.debug("获取所有模型配置列表");
        List<AiModelConfig> configs = modelConfigMapper.selectList(null);
        return configs.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 只拉启用中的模型,前台模型下拉框和默认模型解析都用这个入口。
     */
    public List<ModelConfigDTO> getEnabledModels() {
        log.debug("获取所有启用的模型");
        List<AiModelConfig> configs = modelConfigMapper.selectEnabledModels();
        return configs.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 拿当前默认模型;数据库无默认时返回 Optional.empty,由上层决定兜底策略。
     */
    public Optional<ModelConfigDTO> getDefaultModel() {
        log.debug("获取默认模型");
        return modelConfigMapper.selectDefaultModel()
                .map(this::toDTO);
    }

    /**
     * 按模型名精确查配置,主要给参数策略解析用(拿 temperature/maxTokens 默认值等)。
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
     * 按主键查模型;不存在直接抛 RuntimeException(管理后台单条编辑场景使用)。
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
     * 新增模型配置。isDefault 强制置 false,防止绕过 {@link #setDefaultModel} 出现多默认。
     * 模型名冲突时抛异常。
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
     * 全量更新模型配置。isDefault 不在这里改,须走 {@link #setDefaultModel}。
     * 改名时若与其他记录冲突抛异常。
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
     * 物理删除模型配置。默认模型受保护,必须先把默认切走再删。
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
     * 切换默认模型。事务内先清掉所有 isDefault 再置本条,保证全表唯一默认;
     * 目标模型必须处于启用状态,否则抛异常。
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
     * 启用或禁用模型。禁用默认模型受保护,必须先把默认切走。
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
     * 从聊天消息表按模型分组统计当日调用次数,供管理后台仪表盘展示。
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

    /** 实体转 DTO,字段一对一映射。 */
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
