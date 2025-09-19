package chat.liuxin.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档实体
 * 用于存储和管理RAG检索的文档片段
 * 
 * 作者：刘鑫
 * 时间：2025-09-05
 */
@Data
@TableName("knowledge_documents")
public class KnowledgeDocument {
    
    /**
     * 文档ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 文档标题
     */
    private String title;
    
    /**
     * 文档内容
     */
    private String content;
    
    /**
     * 文档向量（用于相似度检索）
     */
    private String embedding;
    
    /**
     * 文档类型（如：技术文档、API文档、教程等）
     */
    private String docType;
    
    /**
     * 文档标签（逗号分隔）
     */
    private String tags;
    
    /**
     * 来源URL或文件路径
     */
    private String source;
    
    /**
     * 文档摘要
     */
    private String summary;
    
    /**
     * 相似度得分（查询时动态计算）
     */
    @TableField(exist = false)
    private Double similarityScore;
    
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    /**
     * 是否启用
     */
    private Boolean enabled;
    
    /**
     * 文档版本
     */
    private String version;
}