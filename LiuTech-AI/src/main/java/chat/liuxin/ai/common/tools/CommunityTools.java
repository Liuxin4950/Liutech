package chat.liuxin.ai.common.tools;

import chat.liuxin.ai.service.community.CommunityScope;
import chat.liuxin.ai.infra.exception.AIServiceException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import java.util.*;

/** 社区专属本地工具；所有数据来自当前已授权快照，无任意文章/角色参数。 */
@Component
public class CommunityTools implements ToolGroup {
    public Set<String> allowedRoles() { return Set.of("BOT"); }
    public Set<String> allowedModes() { return Set.of("COMMUNITY"); }

    @Tool(description="分段读取正在评价的公开文章。只能读取当前文章；返回范围必须如实说明。")
    public Map<String,Object> readCommunityArticle(@ToolParam(description="正文起始字符位置，从0开始") Integer offset,
            @ToolParam(description="读取字符数，1到4000") Integer limit,ToolContext toolContext) {
        CommunityScope scope=CommunityScope.require(toolContext);
        String body=scope.context.path("post").path("content").asText();
        return segment(scope,"article",scope.postId(),body,offset,limit);
    }

    @Tool(description="检索当前角色自己的文本资料，返回匹配的资料ID和标题，可再分段读取。")
    public List<Map<String,Object>> searchRoleKnowledge(@ToolParam(description="关键词，最多100字符；空值列出资料") String keyword,ToolContext toolContext) {
        CommunityScope scope=CommunityScope.require(toolContext);
        if(keyword==null || keyword.length()>100) throw invalid();
        List<Map<String,Object>> result=new ArrayList<>();
        for(JsonNode item:scope.context.path("knowledge")) {
            if(item.path("botId").asLong()!=scope.botId()) continue;
            if(result.size()==10) break;
            if(keyword.isBlank() || (item.path("title").asText()+" "+item.path("content").asText()).toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))) {
                String body=item.path("content").asText();
                result.add(Map.of("id",item.path("id").asLong(),"title",item.path("title").asText(),"total",body.length()));
                scope.read("knowledge-index",item.path("id").asLong(),0,0,body.length());
            }
        }
        return result;
    }

    @Tool(description="按资料ID分段读取当前角色自己的知识文本，不能读取其他角色资料。")
    public Map<String,Object> readRoleKnowledge(@ToolParam(description="检索返回的资料ID") Long knowledgeId,
            @ToolParam(description="起始字符位置") Integer offset,@ToolParam(description="字符数，1到4000") Integer limit,ToolContext toolContext) {
        CommunityScope scope=CommunityScope.require(toolContext);
        for(JsonNode item:scope.context.path("knowledge")) if(item.path("id").asLong()==(knowledgeId==null?-1:knowledgeId)
                && item.path("botId").asLong()==scope.botId()) {
            return segment(scope,"knowledge",knowledgeId,item.path("content").asText(),offset,limit);
        }
        throw new AIServiceException.RequestException("当前角色没有该资料的读取权限");
    }

    @Tool(description="查看当前文章讨论中可回复的公开评论；不能查看私人聊天或其他线程。")
    public JsonNode readCommunityComments(ToolContext toolContext) {
        CommunityScope scope=CommunityScope.require(toolContext);
        JsonNode comments=scope.context.path("comments");
        scope.read("comments",scope.postId(),0,comments.size(),comments.size());
        return comments;
    }

    @Tool(description="按关键词查找这个角色参与过的公开互动记忆。只返回已经验证来源仍公开的本人记忆。")
    public List<Map<String,Object>> recallRoleMemory(@ToolParam(description="关键词，最多100字符；空字符串列出近期记忆") String keyword,ToolContext toolContext) {
        CommunityScope scope=CommunityScope.require(toolContext);
        if(keyword==null || keyword.length()>100) throw invalid();
        List<Map<String,Object>> result=scope.memories.stream().filter(m -> keyword.isBlank()
                || String.valueOf(m.get("summary")).toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))).limit(10).toList();
        for(var memory:result) scope.read("memory",memory.get("id"),0,String.valueOf(memory.get("summary")).length(),String.valueOf(memory.get("summary")).length());
        return result;
    }

    private Map<String,Object> segment(CommunityScope scope,String source,Object id,String text,Integer offset,Integer limit) {
        int start=offset==null?0:offset;
        int count=limit==null?2000:limit;
        if(start<0 || start>text.length() || count<1 || count>4000) throw invalid();
        int end=Math.min(text.length(),start+count);
        scope.read(source,id,start,end,text.length());
        return Map.of("source",source,"id",id,"start",start,"end",end,"total",text.length(),"content",text.substring(start,end),"truncated",start>0 || end<text.length());
    }
    private AIServiceException.RequestException invalid() { return new AIServiceException.RequestException("资料读取参数超出范围"); }
}
