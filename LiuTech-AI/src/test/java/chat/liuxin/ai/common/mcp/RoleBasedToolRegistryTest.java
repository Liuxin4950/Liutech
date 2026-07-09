package chat.liuxin.ai.common.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RoleBasedToolRegistry 单元测试。
 * 重点验证 role 大小写归一化（AuthUtils.resolveRole 返回小写，allowedRoles 声明大写）。
 */
class RoleBasedToolRegistryTest {

    private final BlogMcpTools blogMcpTools = new BlogMcpTools(null);
    private final WritingTools writingTools = new WritingTools(null);
    private final RoleBasedToolRegistry registry = new RoleBasedToolRegistry(List.of(blogMcpTools, writingTools));

    @Test
    void getToolsForRole_小写admin归一化匹配_拿到全部工具() {
        // AuthUtils.resolveRole 返回小写 "admin"，allowedRoles 声明大写 "ADMIN"，归一化后应匹配
        List<Object> tools = registry.getToolsForRole("admin");
        assertEquals(2, tools.size(), "admin 应拿到 BlogMcpTools + WritingTools");
        assertTrue(tools.contains(blogMcpTools));
        assertTrue(tools.contains(writingTools));
    }

    @Test
    void getToolsForRole_小写user只拿到BlogMcpTools() {
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
        assertEquals(2, tools.size());
    }
}
