package chat.liuxin.ai.service;

import chat.liuxin.ai.entity.AiChatMessage;

import java.util.List;

/**
 * 记忆服务接口
 * 作者：刘鑫
 * 时间：2025-09-24
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

    /** 清空用户所有聊天记忆（物理删除） */
    void clearAllMemory(String userId);
}