package chat.liuxin.ai.service.impl;

import chat.liuxin.ai.config.AiPromptConfig;
import chat.liuxin.ai.entity.KnowledgeDocument;
import chat.liuxin.ai.mapper.KnowledgeDocumentMapper;
import chat.liuxin.ai.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG服务实现类
 * 基于向量相似度的知识检索服务
 * 
 * 作者：刘鑫
 * 时间：2025-09-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {
    
    private final KnowledgeDocumentMapper documentMapper;
    private final EmbeddingModel embeddingModel;
    private final AiPromptConfig promptConfig;
    
    /**
     * 向量维度（根据使用的embedding模型调整）
     */
    private static final int EMBEDDING_DIMENSION = 768;
    
    @Override
    @Transactional
    public KnowledgeDocument saveDocument(KnowledgeDocument document) {
        try {
            // 生成文档向量
            String embedding = generateEmbedding(document.getContent());
            document.setEmbedding(embedding);
            
            // 如果没有摘要，自动生成
            if (document.getSummary() == null || document.getSummary().isEmpty()) {
                document.setSummary(generateSummary(document.getContent()));
            }
            
            document.setEnabled(true);
            documentMapper.insert(document);
            log.info("保存知识文档成功: {}", document.getTitle());
            return document;
        } catch (Exception e) {
            log.error("保存知识文档失败: {}", document.getTitle(), e);
            throw new RuntimeException("保存知识文档失败", e);
        }
    }
    
    @Override
    @Transactional
    public int saveDocumentsBatch(List<KnowledgeDocument> documents) {
        int successCount = 0;
        for (KnowledgeDocument doc : documents) {
            try {
                saveDocument(doc);
                successCount++;
            } catch (Exception e) {
                log.error("批量保存文档失败: {}", doc.getTitle(), e);
            }
        }
        log.info("批量保存知识文档完成，成功: {}/{} 个", successCount, documents.size());
        return successCount;
    }
    
    @Override
    public List<KnowledgeDocument> searchRelevantKnowledge(String query, int limit, double minScore) {
        try {
            // 生成查询向量
            String queryEmbedding = generateEmbedding(query);
            
            // 获取所有启用的文档
            List<KnowledgeDocument> allDocs = documentMapper.selectEnabledDocuments();
            
            // 计算相似度并排序
            return allDocs.stream()
                .map(doc -> {
                    double similarity = calculateCosineSimilarity(queryEmbedding, doc.getEmbedding());
                    doc.setSimilarityScore(similarity);
                    return doc;
                })
                .filter(doc -> doc.getSimilarityScore() >= minScore)
                .sorted((a, b) -> Double.compare(b.getSimilarityScore(), a.getSimilarityScore()))
                .limit(limit)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("知识检索失败: {}", query, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<KnowledgeDocument> searchRelevantKnowledge(String query) {
        return searchRelevantKnowledge(query, promptConfig.getRagResultLimit(), promptConfig.getRagSimilarityThreshold());
    }
    
    @Override
    public String buildRagEnhancedPrompt(String userQuery, List<KnowledgeDocument> relevantDocs) {
        if (relevantDocs == null || relevantDocs.isEmpty()) {
            return userQuery;
        }
        
        StringBuilder enhancedPrompt = new StringBuilder();
        enhancedPrompt.append("基于以下知识库信息，请回答用户的问题：\n\n");
        
        // 添加相关知识片段
        for (int i = 0; i < relevantDocs.size(); i++) {
            KnowledgeDocument doc = relevantDocs.get(i);
            enhancedPrompt.append(String.format("【知识片段 %d】\n", i + 1));
            enhancedPrompt.append(String.format("标题: %s\n", doc.getTitle()));
            enhancedPrompt.append(String.format("内容: %s\n", doc.getContent()));
            if (doc.getSource() != null) {
                enhancedPrompt.append(String.format("来源: %s\n", doc.getSource()));
            }
            enhancedPrompt.append("\n");
        }
        
        enhancedPrompt.append("用户问题: ").append(userQuery).append("\n\n");
        enhancedPrompt.append("请结合上述知识库信息，提供专业准确的回答。如果知识库中没有相关信息，请明确说明。");
        
        return enhancedPrompt.toString();
    }
    
    @Override
    @Transactional
    public boolean updateDocumentEmbedding(Long documentId) {
        try {
            KnowledgeDocument doc = documentMapper.selectById(documentId);
            if (doc == null) {
                log.warn("文档不存在: {}", documentId);
                return false;
            }
            
            String newEmbedding = generateEmbedding(doc.getContent());
            doc.setEmbedding(newEmbedding);
            documentMapper.updateById(doc);
            
            log.info("更新文档向量成功: {}", documentId);
            return true;
        } catch (Exception e) {
            log.error("更新文档向量失败: {}", documentId, e);
            return false;
        }
    }
    
    @Override
    @Transactional
    public boolean deleteDocument(Long documentId) {
        try {
            int result = documentMapper.deleteById(documentId);
            boolean success = result > 0;
            if (success) {
                log.info("删除知识文档成功: {}", documentId);
            } else {
                log.warn("删除知识文档失败，文档不存在: {}", documentId);
            }
            return success;
        } catch (Exception e) {
            log.error("删除知识文档失败: {}", documentId, e);
            return false;
        }
    }
    
    @Override
    public List<KnowledgeDocument> searchByDocType(String docType, String query, int limit) {
        try {
            List<KnowledgeDocument> docs = documentMapper.selectByDocTypeAndEnabled(docType);
            String queryEmbedding = generateEmbedding(query);
            
            return docs.stream()
                .map(doc -> {
                    double similarity = calculateCosineSimilarity(queryEmbedding, doc.getEmbedding());
                    doc.setSimilarityScore(similarity);
                    return doc;
                })
                .sorted((a, b) -> Double.compare(b.getSimilarityScore(), a.getSimilarityScore()))
                .limit(limit)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("按文档类型检索失败: {} - {}", docType, query, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<String> getPopularTags(int limit) {
        return documentMapper.selectPopularTags(limit);
    }
    
    /**
     * 生成文本向量
     */
    private String generateEmbedding(String text) {
        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
            float[] embeddings = response.getResults().get(0).getOutput();
            return Arrays.toString(embeddings);
        } catch (Exception e) {
            log.error("生成文本向量失败", e);
            throw new RuntimeException("生成文本向量失败", e);
        }
    }
    
    /**
     * 生成文档摘要
     */
    private String generateSummary(String content) {
        if (content.length() <= 200) {
            return content;
        }
        return content.substring(0, 200) + "...";
    }
    
    /**
     * 计算余弦相似度
     */
    private double calculateCosineSimilarity(String embedding1, String embedding2) {
        try {
            float[] vec1 = parseEmbedding(embedding1);
            float[] vec2 = parseEmbedding(embedding2);
            
            if (vec1.length != vec2.length) {
                log.warn("向量维度不匹配: {} vs {}", vec1.length, vec2.length);
                return 0.0;
            }
            
            double dotProduct = 0.0;
            double norm1 = 0.0;
            double norm2 = 0.0;
            
            for (int i = 0; i < vec1.length; i++) {
                dotProduct += vec1[i] * vec2[i];
                norm1 += vec1[i] * vec1[i];
                norm2 += vec2[i] * vec2[i];
            }
            
            if (norm1 == 0.0 || norm2 == 0.0) {
                return 0.0;
            }
            
            return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
        } catch (Exception e) {
            log.error("计算余弦相似度失败", e);
            return 0.0;
        }
    }
    
    /**
     * 解析向量字符串
     */
    private float[] parseEmbedding(String embedding) {
        String[] parts = embedding.replace("[", "").replace("]", "").split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].trim());
        }
        return vector;
    }
}