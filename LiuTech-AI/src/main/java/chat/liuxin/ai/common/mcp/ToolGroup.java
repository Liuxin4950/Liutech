package chat.liuxin.ai.common.mcp;

import java.util.Set;

/**
 * 工具组角色声明接口。
 *
 * 实现该接口的 Tool 类通过 {@link #allowedRoles()} 声明可使用的角色，
 * {@code RoleBasedToolRegistry} 据此按角色过滤，实现 admin/user 工具隔离的防御纵深
 * （与 SecurityConfig URL 层、PromptService 提示词层共同构成三层隔离）。
 *
 * @author 刘鑫
 */
public interface ToolGroup {

    /**
     * 允许调用该工具组的角色集合（如 "ADMIN"、"USER"、"GUEST"）。
     *
     * @return 允许的角色集合
     */
    Set<String> allowedRoles();
}
