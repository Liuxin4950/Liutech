package chat.liuxin.ai.mapper;

import chat.liuxin.ai.entity.AiModelConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/**
 * AI模型配置 Mapper
 * 提供模型配置的数据库操作
 *
 * @author 刘鑫
 * @since 2025-01-18
 */
@Mapper
public interface AiModelConfigMapper extends BaseMapper<AiModelConfig> {

    /**
     * 查询所有启用的模型，按排序顺序
     *
     * @return 启用的模型列表
     */
    @Select("SELECT * FROM ai_model_config WHERE is_enabled = 1 ORDER BY sort_order ASC")
    List<AiModelConfig> selectEnabledModels();

    /**
     * 查询默认模型
     *
     * @return 默认模型，可能为空
     */
    @Select("SELECT * FROM ai_model_config WHERE is_default = 1 LIMIT 1")
    Optional<AiModelConfig> selectDefaultModel();

    /**
     * 取消所有模型的默认设置
     * 用于设置新默认模型前，先取消其他模型的默认状态
     */
    @Update("UPDATE ai_model_config SET is_default = 0")
    int clearAllDefault();

    /**
     * 设置指定模型为默认模型
     *
     * @param id 模型ID
     * @return 影响行数
     */
    @Update("UPDATE ai_model_config SET is_default = 1 WHERE id = #{id}")
    int setAsDefault(@Param("id") Long id);
}
