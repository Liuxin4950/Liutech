package chat.liuxin.ai.mapper;

import chat.liuxin.ai.entity.KnowledgeDocument;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识文档数据访问层
 * 
 * 作者：刘鑫
 * 时间：2025-09-05
 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {
    
    /**
     * 查询所有启用的文档
     */
    @Select("SELECT * FROM knowledge_documents WHERE enabled = 1 ORDER BY created_at DESC")
    List<KnowledgeDocument> selectEnabledDocuments();
    
    /**
     * 根据文档类型查询启用的文档
     */
    @Select("SELECT * FROM knowledge_documents WHERE doc_type = #{docType} AND enabled = 1 ORDER BY created_at DESC")
    List<KnowledgeDocument> selectByDocTypeAndEnabled(@Param("docType") String docType);
    
    /**
     * 获取热门标签
     */
    @Select("SELECT DISTINCT tag FROM knowledge_documents WHERE enabled = 1 AND tag IS NOT NULL AND tag != '' ORDER BY RAND() LIMIT #{limit}")
    List<String> selectPopularTags(@Param("limit") int limit);
    
    /**
     * 根据标签查询文档
     */
    @Select("SELECT * FROM knowledge_documents WHERE tag = #{tag} AND enabled = 1 ORDER BY created_at DESC")
    List<KnowledgeDocument> selectByTag(@Param("tag") String tag);
    
    /**
     * 模糊搜索文档标题
     */
    @Select("SELECT * FROM knowledge_documents WHERE title LIKE CONCAT('%', #{keyword}, '%') AND enabled = 1 ORDER BY created_at DESC")
    List<KnowledgeDocument> searchByTitle(@Param("keyword") String keyword);
    
    /**
     * 根据ID列表批量查询
     */
    @Select("<script>" +
            "SELECT * FROM knowledge_documents WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "AND enabled = 1 ORDER BY created_at DESC" +
            "</script>")
    List<KnowledgeDocument> selectByIds(@Param("ids") List<Long> ids);
}