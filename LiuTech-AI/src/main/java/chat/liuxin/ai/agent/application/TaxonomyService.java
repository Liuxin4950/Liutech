package chat.liuxin.ai.agent.application;
import chat.liuxin.ai.dto.AgentUserContext;

import chat.liuxin.ai.agent.application.AdminBlogClient;
import chat.liuxin.ai.agent.application.AdminBlogClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类/标签匹配服务。
 *
 * 从 Orchestrator 中提取的共享逻辑，供 WritingHandler、DraftHandler 等复用。
 * 职责：
 * - 读取后台分类和标签列表
 * - 文本 ↔ 分类/标签的模糊匹配
 * - 推断建议分类名和标签
 *
 * @author liuxin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaxonomyService {

    private final AdminBlogClient adminBlogClient;

    /** 读取所有可用分类（降级为空列表） */
    public List<AdminBlogClient.AdminTaxonomyItem> readCategories(AgentUserContext user) {
        try {
            if (user == null || isBlank(user.getBearerToken())) return List.of();
            return adminBlogClient.listCategories(user.getBearerToken());
        } catch (Exception e) {
            log.warn("读取分类列表失败，降级为名称建议: {}", e.getMessage());
            return List.of();
        }
    }

    /** 读取所有可用标签（降级为空列表） */
    public List<AdminBlogClient.AdminTaxonomyItem> readTags(AgentUserContext user) {
        try {
            if (user == null || isBlank(user.getBearerToken())) return List.of();
            return adminBlogClient.listTags(user.getBearerToken());
        } catch (Exception e) {
            log.warn("读取标签列表失败，降级为名称建议: {}", e.getMessage());
            return List.of();
        }
    }

    /** 按文本模糊匹配单个分类 */
    public AdminBlogClient.AdminTaxonomyItem matchTaxonomy(List<AdminBlogClient.AdminTaxonomyItem> items, String text) {
        if (items == null || items.isEmpty() || text == null) return null;
        String normalized = text.toLowerCase();
        // 精确匹配优先（名称作为子串出现在文本中）
        AdminBlogClient.AdminTaxonomyItem exact = items.stream()
                .filter(item -> item.getName() != null && normalized.contains(item.getName().toLowerCase()))
                .findFirst().orElse(null);
        if (exact != null) return exact;
        // 降级：按词匹配（名称中任一词出现在文本中即视为匹配）
        return items.stream()
                .filter(item -> item.getName() != null && containsAnyWord(normalized, item.getName().toLowerCase()))
                .findFirst().orElse(null);
    }

    /** 按文本模糊匹配多个标签 */
    public List<AdminBlogClient.AdminTaxonomyItem> matchTaxonomies(List<AdminBlogClient.AdminTaxonomyItem> items, String text, int limit) {
        if (items == null || items.isEmpty() || text == null) return List.of();
        String normalized = text.toLowerCase();
        List<AdminBlogClient.AdminTaxonomyItem> exact = items.stream()
                .filter(item -> item.getName() != null && normalized.contains(item.getName().toLowerCase()))
                .limit(limit).toList();
        if (!exact.isEmpty()) return exact;
        return items.stream()
                .filter(item -> item.getName() != null && containsAnyWord(normalized, item.getName().toLowerCase()))
                .limit(limit).toList();
    }

    /** 按 ID 查找分类/标签 */
    public AdminBlogClient.AdminTaxonomyItem findById(List<AdminBlogClient.AdminTaxonomyItem> items, Long id) {
        if (items == null || items.isEmpty() || id == null) return null;
        return items.stream().filter(item -> id.equals(item.getId())).findFirst().orElse(null);
    }

    /** 按名称精确查找分类/标签 */
    public AdminBlogClient.AdminTaxonomyItem findByName(List<AdminBlogClient.AdminTaxonomyItem> items, String name) {
        if (items == null || items.isEmpty() || isBlank(name)) return null;
        return items.stream().filter(item -> item.getName() != null && item.getName().equalsIgnoreCase(name.trim())).findFirst().orElse(null);
    }

    /** 按 ID 列表批量查找 */
    public List<AdminBlogClient.AdminTaxonomyItem> findByIds(List<AdminBlogClient.AdminTaxonomyItem> items, List<Long> ids) {
        if (items == null || items.isEmpty() || ids == null || ids.isEmpty()) return List.of();
        return items.stream().filter(item -> ids.contains(item.getId())).limit(6).toList();
    }

    /** 按名称列表批量查找 */
    public List<AdminBlogClient.AdminTaxonomyItem> findByNames(List<AdminBlogClient.AdminTaxonomyItem> items, List<String> names, int limit) {
        if (items == null || items.isEmpty() || names == null || names.isEmpty()) return List.of();
        List<String> normalized = names.stream().filter(n -> !isBlank(n)).map(n -> n.trim().toLowerCase()).toList();
        if (normalized.isEmpty()) return List.of();
        return items.stream().filter(item -> item.getName() != null && normalized.contains(item.getName().toLowerCase())).limit(limit).toList();
    }

    /** 根据消息内容推断建议分类名 */
    public String inferSuggestedCategory(String message) {
        String text = message == null ? "" : message.toLowerCase();
        if (containsAny(text, "部署", "docker", "nginx", "运维")) return "部署运维";
        if (containsAny(text, "spring", "java", "后端", "接口")) return "后端开发";
        if (containsAny(text, "vue", "前端", "typescript", "页面")) return "前端开发";
        if (containsAny(text, "ai", "agent", "模型", "提示词")) return "AI 实践";
        return "技术笔记";
    }

    /** 根据消息内容和模型建议生成推荐标签 */
    public List<String> suggestedTags(String message, List<String> existingNames, List<String> modelSuggestedNames) {
        List<String> result = new ArrayList<>();
        if (modelSuggestedNames != null) {
            for (String name : modelSuggestedNames) {
                if (!isBlank(name)
                        && (existingNames == null || existingNames.stream().noneMatch(e -> e.equalsIgnoreCase(name)))
                        && result.stream().noneMatch(e -> e.equalsIgnoreCase(name))) {
                    result.add(name.trim());
                }
            }
        }
        String text = message == null ? "" : message.toLowerCase();
        addIfRelevant(result, existingNames, text, "Spring Boot", "spring", "后端");
        addIfRelevant(result, existingNames, text, "Vue", "vue", "前端");
        addIfRelevant(result, existingNames, text, "Docker", "docker", "部署");
        addIfRelevant(result, existingNames, text, "AI", "ai", "agent", "模型");
        addIfRelevant(result, existingNames, text, "MySQL", "mysql", "数据库");
        if (result.isEmpty()) { result.add("技术实践"); result.add("博客写作"); }
        return result.stream().limit(6).toList();
    }

    // ===== 工具方法 =====

    private void addIfRelevant(List<String> result, List<String> existingNames, String text, String tag, String... keywords) {
        if (existingNames != null && existingNames.stream().anyMatch(name -> name.equalsIgnoreCase(tag))) return;
        for (String kw : keywords) { if (text.contains(kw) && !result.contains(tag)) { result.add(tag); return; } }
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) { if (text.contains(kw)) return true; } return false;
    }

    private boolean containsAnyWord(String text, String name) {
        if (text == null || name == null) return false;
        String[] words = name.split("[\\s,，、/]+");
        for (String word : words) { if (word.length() >= 2 && text.contains(word)) return true; }
        return false;
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }
}

