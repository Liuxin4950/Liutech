package chat.liuxin.ai.service.community;

import chat.liuxin.ai.common.client.ModelExecutionObserver;
import chat.liuxin.ai.infra.exception.AIServiceException;
import tools.jackson.databind.JsonNode;
import java.util.*;

/** 每轮不可变服务器作用域；工具无法自行更换角色、文章或读取私人聊天。 */
public final class CommunityScope implements ModelExecutionObserver {
    public static final String CONTEXT_KEY = "communityScope";
    public final JsonNode context;
    public final List<Map<String,Object>> memories;
    public final List<Map<String,Object>> readTrace = new ArrayList<>();
    public final List<Map<String,Object>> toolTrace = new ArrayList<>();
    private final Set<Long> readTargets = new HashSet<>();
    private int toolReadStart;
    public CommunityScope(JsonNode context,List<Map<String,Object>> memories) { this.context=context;this.memories=memories; }
    public long botId() { return context.path("bot").path("id").asLong(); }
    public long postId() { return context.path("post").path("id").asLong(); }
    public synchronized void read(String source,Object id,int start,int end,int total) {
        readTrace.add(new LinkedHashMap<>(Map.of("source",source,"id",id,"start",start,"end",end,"total",total,"truncated",start>0 || end<total)));
    }
    @Override public synchronized void toolStarted(String name,String input) {
        toolReadStart=readTrace.size();
        toolTrace.add(new LinkedHashMap<>(Map.of("name",name,"input",input == null ? "" : input,"status","RUNNING")));
    }
    @Override public synchronized void toolCompleted(String name) { updateTool(name,"SUCCEEDED"); }
    @Override public synchronized void rejectedTool(String name,String message) { updateTool(name,"FAILED"); }
    @Override public synchronized void toolResultDelivered(String name,String delivered,boolean truncated) {
        for (int i=toolTrace.size()-1;i>=0;i--) if (toolTrace.get(i).get("name").equals(name)) {
            toolTrace.get(i).put("deliveredChars",delivered.length());
            toolTrace.get(i).put("truncated",truncated);break;
        }
        if (truncated) for (int i=toolReadStart;i<readTrace.size();i++) {
            var trace=readTrace.get(i);trace.put("requestedEnd",trace.get("end"));
            trace.put("end",null);trace.put("rangeComplete",false);trace.put("truncated",true);
        }
        if (!truncated && name.equals("readCommunityComments")) readTargets.addAll(targets());
    }
    public synchronized void readTarget(long id) { if (targets().contains(id)) readTargets.add(id); }
    public synchronized Set<Long> readTargets() { return Set.copyOf(readTargets); }
    private void updateTool(String name,String status) {
        for (int i=toolTrace.size()-1;i>=0;i--) if (toolTrace.get(i).get("name").equals(name)) { toolTrace.get(i).put("status",status);break; }
    }
    public Set<Long> targets() {
        Set<Long> result = new HashSet<>();
        context.path("comments").forEach(comment -> {
            if (comment.path("botId").asLong(-1) != botId()) result.add(comment.path("id").asLong());
        });
        return result;
    }
    public static CommunityScope require(org.springframework.ai.chat.model.ToolContext context) {
        if (!(context.getContext().get(CONTEXT_KEY) instanceof CommunityScope scope)) {
            throw new AIServiceException.RequestException("社区工具缺少服务端角色作用域");
        }
        return scope;
    }
}
