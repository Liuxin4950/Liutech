package chat.liuxin.ai.service;

import chat.liuxin.ai.entity.AiChatMessage;
import chat.liuxin.ai.entity.AiConversation;

import java.util.List;

/**
 * 记忆服务接口
 * 作者：刘鑫
 * 时间：2025-09-24
 *
 * 存储模型：
 * - 会话（AiConversation）与消息（AiChatMessage）为一对多关系，通过 conversation_id 关联。
 * - 每次保存消息会同步维护会话的 messageCount 与 lastMessageAt。
 * - 删除会话时采取“先删消息、再删会话”的顺序，避免产生孤儿消息。
 *
 * 排序与分页约定：
 * - 用户全局历史：按 created_at 与 id 倒序分页返回；近期拼接上下文时再反转为升序。
 * - 会话内消息：同样按 created_at 与 id 倒序分页；最近 N 条再反转为升序用于提示词构造。
 */
public interface MemoryService {

    /**
     * 查询某用户最近 N 条消息（升序返回，便于直接拼接历史）
     * @param userId 用户ID
     * @param limit  返回条数（>0）
     * @return 从旧到新的消息列表；当 limit<=0 时返回空列表
     */
    List<AiChatMessage> listRecentMessages(String userId, int limit);

    /**
     * 分页查询某用户的聊天历史记录（按创建时间与ID倒序）
     * @param userId 用户ID
     * @param page   页码（>=1）
     * @param size   每页大小（>0）
     * @return 倒序的消息列表；参数非法时返回空列表
     */
    List<AiChatMessage> listHistoryMessages(String userId, int page, int size);

    /**
     * 查询某用户的聊天历史记录总数
     * @param userId 用户ID
     * @return 记录总数
     */
    long countHistoryMessages(String userId);

    /**
     * 保存一条用户消息（role=user，status 固定为 1）并同步更新会话指标
     * @param userId         用户ID
     * @param conversationId 会话ID（可为 null，建议始终提供）
     * @param content        文本内容
     * @param model          模型名称
     * @param metadataJson   额外元数据（JSON字符串，可为空）
     */
    void saveUserMessage(String userId, Long conversationId, String content, String model, String metadataJson);

    /**
     * 保存一条 AI 消息（role=assistant）并同步更新会话指标
     * 状态约定：status=1 表示成功；status=9 表示错误（content 可为空）
     * @param userId         用户ID
     * @param conversationId 会话ID
     * @param content        AI输出文本（错误时可为空）
     * @param model          模型名称
     * @param status         1成功；9错误
     * @param metadataJson   额外元数据（JSON字符串）
     */
    void saveAssistantMessage(String userId, Long conversationId, String content, String model, int status, String metadataJson);

    /**
     * 轻量清理：仅保留该用户最后 N 条记录（例如 10 条）
     * @param userId     用户ID
     * @param retainLastN 保留数量（>0）
     */
    void cleanupByRetainLastN(String userId, int retainLastN);

    /**
     * 清空用户所有聊天记忆（物理删除）
     * @param userId 用户ID
     */
    void clearAllMemory(String userId);

    /**
     * 创建会话
     * @param userId       用户ID
     * @param type         会话类型（如 general）
     * @param title        会话标题
     * @param metadataJson 元数据（JSON字符串）
     * @return 新建会话ID
     */
    Long createConversation(String userId, String type, String title, String metadataJson);

    /**
     * 列出用户的会话
     * @param userId 用户ID
     * @param type   类型筛选（可空）
     * @param page   页码
     * @param size   每页大小
     */
    java.util.List<AiConversation> listConversations(String userId, String type, int page, int size);

    /** 获取会话详情 */
    AiConversation getConversation(Long conversationId);

    /**
     * 分页列出会话内的消息（倒序）
     */
    java.util.List<AiChatMessage> listMessagesByConversation(Long conversationId, int page, int size);

    /**
     * 列出会话内最近 N 条消息（返回升序，便于拼接上下文）
     */
    java.util.List<AiChatMessage> listLastMessagesByConversation(Long conversationId, int limit);

    /** 重命名会话标题 */
    void renameConversation(Long conversationId, String title);

    /** 归档会话（status=9） */
    void archiveConversation(Long conversationId);

    /** 删除会话（先删消息，再删会话） */
    void deleteConversation(Long conversationId);
}
