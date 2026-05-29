package chat.liuxin.ai.infra.security;

/**
 * AI 能力枚举。
 *
 * <p>定义用户可以使用的 AI 功能。
 * <ul>
 *   <li>CHAT - 普通聊天</li>
 *   <li>READ - 读取公开内容（文章搜索、推荐、总结）</li>
 *   <li>WRITE - 管理员写入操作（草稿、发布、下架）</li>
 * </ul>
 *
 * @author liuxin
 */
public enum AiCapability {
    CHAT,
    READ,
    WRITE
}
