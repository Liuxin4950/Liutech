package chat.liuxin.ai.common.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 基于角色的工具注册器。
 *
 * 自动收集所有 {@link ToolGroup} 实现 Bean，按角色过滤出可用工具列表。
 * 这是 AI 工具隔离的防御纵深之一：即使 SecurityConfig 的 URL 层配置错误，
 * 用户角色也无法加载到仅 ADMIN 的工具（如 WritingTools）。
 *
 * 三层防御：SecurityConfig URL 层（/ai/writing 限 ADMIN）+ 本注册器角色层 + PromptService 提示词层。
 *
 * @author 刘鑫
 */
@Slf4j
@Component
public class RoleBasedToolRegistry {

    private final List<ToolGroup> allToolGroups;

    public RoleBasedToolRegistry(List<ToolGroup> allToolGroups) {
        this.allToolGroups = allToolGroups;
        log.info("RoleBasedToolRegistry 初始化，注册 {} 个工具组", allToolGroups.size());
    }

    /**
     * 按角色获取可用工具组列表。
     *
     * @param role 用户角色（ADMIN/USER/GUEST），null 视为 GUEST
     * @return 该角色允许调用的工具组对象列表（用于注册到 ChatClient）
     */
    public List<Object> getToolsForRole(String role) {
        String effectiveRole = role == null ? "GUEST" : role;
        List<Object> tools = new ArrayList<>();
        for (ToolGroup group : allToolGroups) {
            Set<String> allowed = group.allowedRoles();
            if (allowed == null || allowed.contains(effectiveRole)) {
                tools.add(group);
            }
        }
        log.debug("角色 {} 可用工具组: {}", effectiveRole, tools.size());
        return tools;
    }
}
