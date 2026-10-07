package chat.liuxin.ai.common.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RoleBasedToolRegistry 单元测试。
 * 重点验证 role 大小写归一化（AuthUtils.resolveRole 返回小写，allowedRoles 声明大写）。
 */
class RoleBasedToolRegistryTest {

    private final BlogTools blogMcpTools = new BlogTools(null, null);
    private final WritingTools writingTools = new WritingTools(null, null);
    private final RoleBasedToolRegistry registry = new RoleBasedToolRegistry(List.of(blogMcpTools, writingTools));

    @Test
    void getToolsForRole_小写admin归一化匹配_拿到全部工具() {
        // AuthUtils.resolveRole 返回小写 "admin"，allowedRoles 声明大写 "ADMIN"，归一化后应匹配
        List<Object> tools = registry.getToolsForRole("admin");
        assertEquals(1, tools.size(), "聊天入口即使管理员也只能拿到博客读取工具");
        assertTrue(tools.contains(blogMcpTools));
        assertFalse(tools.contains(writingTools));
    }

    @Test
    void getToolsForRole_小写user只拿到BlogTools() {
        List<Object> tools = registry.getToolsForRole("user");
        assertEquals(1, tools.size());
        assertTrue(tools.contains(blogMcpTools));
        assertFalse(tools.contains(writingTools));
    }

    @Test
    void getToolsForRole_nullRole视为GUEST() {
        List<Object> tools = registry.getToolsForRole(null);
        assertEquals(1, tools.size());
        assertTrue(tools.contains(blogMcpTools));
    }

    @Test
    void getToolsForRole_大写role也能匹配() {
        List<Object> tools = registry.getToolsForRole("ADMIN");
        assertEquals(1, tools.size());
    }
    @Test
    void writingModeRequiresAdministratorAndExcludesChatTools() {
        assertEquals(List.of(writingTools), registry.getToolsForRoleAndMode("admin", "WRITING"));
        assertTrue(registry.getToolsForRoleAndMode("USER", "WRITING").isEmpty());
        assertTrue(registry.getToolsForRoleAndMode("GUEST", "WRITING").isEmpty());
        assertTrue(registry.getToolsForRoleAndMode("ADMIN", "UNKNOWN").isEmpty());
    }
}
