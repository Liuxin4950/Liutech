package chat.liuxin.ai.service;

import chat.liuxin.ai.entity.KnowledgeDocument;

import java.util.List;

/**
 * RAG知识检索服务接口
 * 提供文档存储、向量化和相似度检索功能
 * 
 * 作者：刘鑫
 * 时间：2025-09-05
 */
public interface RagService {
    
    /**
     * 保存知识文档
     * @param document 文档对象
     * @return 保存后的文档
     */
    KnowledgeDocument saveDocument(KnowledgeDocument document);
    
    /**
     * 批量保存知识文档
     * @param documents 文档列表
     * @return 保存成功的文档数量
     */
    int saveDocumentsBatch(List<KnowledgeDocument> documents);
    
    /**
     * 根据查询文本检索相关知识
     * @param query 查询文本
     * @param limit 返回结果数量限制
     * @param minScore 最小相似度阈值
     * @return 相关文档列表，按相似度降序排列
     */
    List<KnowledgeDocument> searchRelevantKnowledge(String query, int limit, double minScore);
    
    /**
     * 简化版本的知识检索（使用默认参数）
     * @param query 查询文本
     * @return 相关文档列表
     */
    List<KnowledgeDocument> searchRelevantKnowledge(String query);
    
    /**
     * 构建RAG增强的提示词
     * @param userQuery 用户查询
     * @param relevantDocs 相关文档
     * @return 增强后的提示词
     */
    String buildRagEnhancedPrompt(String userQuery, List<KnowledgeDocument> relevantDocs);
    
    /**
     * 更新文档的向量表示
     * @param documentId 文档ID
     * @return 是否更新成功
     */
    boolean updateDocumentEmbedding(Long documentId);
    
    /**
     * 删除知识文档
     * @param documentId 文档ID
     * @return 是否删除成功
     */
    boolean deleteDocument(Long documentId);
    
    /**
     * 根据文档类型检索
     * @param docType 文档类型
     * @param query 查询文本
     * @param limit 返回数量限制
     * @return 相关文档列表
     */
    List<KnowledgeDocument> searchByDocType(String docType, String query, int limit);
    
    /**
     * 获取热门标签
     * @param limit 返回数量限制
     * @return 热门标签列表
     */
    List<String> getPopularTags(int limit);
}