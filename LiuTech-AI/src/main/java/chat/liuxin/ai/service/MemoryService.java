package chat.liuxin.ai.service;

import chat.liuxin.ai.entity.AiChatMessage;

import java.util.List;

/**
 * 聊天记忆服务接口：封装"数据库记忆"的读写与清理策略。
 * 学习引导：
 * - listRecentMessages：查询最近N条（不含本轮输入），用于拼接Prompt上下文。
 * - saveUserMessage / saveAssistantMessage：分别写入用户与AI的消息；AI消息在流式模式下只在完成或错误时写入。
 * - cleanupByRetainLastN：轻量清理策略示例，按用户保留最后N条，避免无限增长。
 * - listHistoryMessages：分页查询用户的聊天历史记录。
 */
public interface MemoryService {

    /** 查询某用户最近N条消息（升序返回，便于直接拼接历史） */
    List<AiChatMessage> listRecentMessages(String userId, int limit);

    /** 分页查询某用户的聊天历史记录（按创建时间倒序） */
    List<AiChatMessage> listHistoryMessages(String userId, int page, int size);

    /** 查询某用户的聊天历史记录总数 */
    long countHistoryMessages(String userId);

    /** 保存一条用户消息（role=user，status固定1） */
    void saveUserMessage(String userId, String content, String model, String metadataJson);

    /** 保存一条AI消息（role=assistant，status=1成功；9错误），content可为空（错误时） */
    void saveAssistantMessage(String userId, String content, String model, int status, String metadataJson);

    /** 轻量清理：仅保留该用户最后N条记录（例如10条） */
    void cleanupByRetainLastN(String userId, int retainLastN);
}