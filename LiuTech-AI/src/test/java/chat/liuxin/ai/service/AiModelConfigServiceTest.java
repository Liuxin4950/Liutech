package chat.liuxin.ai.service;

import chat.liuxin.ai.dto.ModelConfigRequest;
import chat.liuxin.ai.entity.AiModelConfig;
import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.infra.exception.AIServiceException;
import chat.liuxin.ai.infra.security.PromptBudget;
import chat.liuxin.ai.mapper.AiChatMessageMapper;
import chat.liuxin.ai.mapper.AiModelConfigMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiModelConfigServiceTest {
    final AiModelConfigMapper mapper=mock(AiModelConfigMapper.class);
    final AiChatProperties props=new AiChatProperties();
    final AiModelConfigService service=new AiModelConfigService(mapper,mock(AiChatMessageMapper.class),new PromptBudget(props),props);
    AiModelConfig model;
    @BeforeEach void setup() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),""),AiModelConfig.class);
        model=new AiModelConfig();model.setId(1L);model.setModelName("configured-model");model.setDisplayName("configured");
        model.setIsEnabled(true);model.setIsDefault(true);model.setMaxTokens(1000);model.setContextWindow(8192);
        when(mapper.lockModel(1L)).thenReturn(model);
    }
    static ModelConfigRequest request(boolean enabled) {
        var request=new ModelConfigRequest();request.setModelName("configured-model");request.setDisplayName("updated");
        request.setProvider("siliconflow");request.setIsEnabled(enabled);request.setMaxTokens(1000);request.setContextWindow(8192);return request;
    }
    @Test void editingCannotBypassTheExistingDefaultDisableGuard() {
        var error=assertThrows(AIServiceException.RequestException.class,()->service.updateModel(1L,request(false)));
        assertTrue(error.getMessage().contains("不能禁用默认模型"));
        var order=inOrder(mapper);order.verify(mapper).lockCatalog();order.verify(mapper).lockModel(1L);
        verify(mapper,never()).update(any(),any());verify(mapper,never()).updateById(any(AiModelConfig.class));
    }
    @Test void editingWritesOnlyConfigurationFieldsAndPreservesTheLockedDefaultFlag() {
        assertTrue(service.updateModel(1L,request(true)).getIsDefault());
        verify(mapper).update(isNull(),argThat(wrapper->
            !((LambdaUpdateWrapper<AiModelConfig>)wrapper).getSqlSet().contains("is_default")));
        verify(mapper,never()).updateById(any(AiModelConfig.class));
    }
    @Test void toggleWritesOnlyEnabledAndDeleteProtectsTheCurrentDefault() {
        assertThrows(AIServiceException.RequestException.class,()->service.toggleEnabled(1L,false));
        assertThrows(AIServiceException.RequestException.class,()->service.deleteModel(1L));
        model.setIsDefault(false);service.toggleEnabled(1L,false);
        verify(mapper).update(isNull(),argThat(wrapper->{
            String sql=((LambdaUpdateWrapper<AiModelConfig>)wrapper).getSqlSet();
            return sql.startsWith("is_enabled=") && !sql.contains("is_default") && !sql.contains("max_tokens");
        }));
        verify(mapper,never()).deleteById(anyLong());
    }
    @Test void switchingToADisabledOrDeletedTargetDoesNotClearTheCurrentDefault() {
        model.setIsEnabled(false);
        assertThrows(AIServiceException.RequestException.class,()->service.setDefaultModel(1L));
        when(mapper.lockModel(2L)).thenReturn(null);
        assertThrows(AIServiceException.RequestException.class,()->service.setDefaultModel(2L));
        verify(mapper,never()).clearAllDefault();verify(mapper,never()).setAsDefault(anyLong());
    }
}
