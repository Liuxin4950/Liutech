package chat.liuxin.ai.service.impl;
 
import chat.liuxin.ai.req.ChatRequest;
import chat.liuxin.ai.resp.ChatResponse;
import chat.liuxin.ai.service.AiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// 删除别名导入，Java不支持；下方使用全限定名避免冲突
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
 
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
 
/**
 * AI聊天核心服务实现（精简版）
 * 学习顺序：
 * 1) 先读 processChat：最小可运行的一问一答
 * 2) 再读 processChatStream：SSE按块推送
 *
 * 作者：刘鑫
 * 时间：2025-09-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {
 
    // 只有一个依赖：大模型
    /**
     * OllamaChatModel是Spring AI提供的Ollama模型客户端
     * 通过application.yml配置:
     * spring:
     *   ai:
     *     ollama:
     *       base-url: http://localhost:11434  # Ollama服务地址
     *       model: llama2 # 默认模型名称
     * 
     * 由Spring Boot自动配置和注入
     */
    private final OllamaChatModel chatModel;
 
    /**
     * 1) 普通聊天：一次性返回完整AI回复
     */
    @Override
    public ChatResponse processChat(ChatRequest request) {
        try {
            //获取请求的问题
            String input = request.getMessage();
            //获取请求的模型
            String modelName = request.getModel() != null ? request.getModel() : "ollama";
 
            // 直接将用户输入构造成Prompt调用
            // 创建一个新的Prompt对象来封装用户的输入消息
            // Prompt是Spring AI的标准输入格式，用于向AI模型发送请求
            var prompt = new Prompt(input);
            
            // 调用AI模型的call方法处理请求
            // chatModel.call()会同步等待AI完整的响应
            var response = chatModel.call(prompt);
            
            // 从响应中提取AI生成的文本内容
            // getResult()获取响应结果
            // getOutput()获取输出内容
            // response返回的是ChatResponse对象，包含以下层级:
            // ChatResponse
            //   - Result (getResult())
            //     - Output (getOutput()) 
            //       - Content (getContent()) - 最终的文本内容
            String aiOutput = response.getResult().getOutput().getContent();
 
            return ChatResponse.builder()
                    .success(true)//是否成功
                    .message(aiOutput)//响应消息
                    .userId(request.getUserId())//用户ID
                    .model(modelName)//模型名称
                    .historyCount(0)//历史消息数量
                    .timestamp(System.currentTimeMillis())//响应时间戳
                    .responseLength(aiOutput != null ? aiOutput.length() : 0)//响应长度
                    .build();

            
        } catch (Exception e) {
            log.error("AI普通聊天失败", e);
            return ChatResponse.error("AI服务暂时不可用: " + e.getMessage(), request.getUserId());
        }
    }
 
    /**
     * 2) 流式聊天：通过SSE按块推送AI回复
     */
    @Override
    public SseEmitter processChatStream(ChatRequest request) {
        
        // 创建SSE发射器,用于服务器向客户端推送事件流
        // 设置超时时间为30秒,如果30秒内服务器没有发送任何数据,连接会自动关闭
        SseEmitter emitter = new SseEmitter(30000L);
        try {
            // 先把用户基本信息发给前端（可选）
            // 向客户端发送用户信息事件
            // name("user") - 事件名称为"user"
            // data() - 事件数据是一个Map，包含用户ID
            // Map.of() - 创建一个包含单个键值对的Map，键为"userId"，值为请求中的用户ID
            // emitter.send() - 通过SSE发射器发送事件
            emitter.send(SseEmitter.event().name("user").data(Map.of("userId", request.getUserId())));
 
            CompletableFuture.runAsync(() -> {
                try {
                    // 发送开始事件
                    emitter.send(SseEmitter.event().name("start").data(Map.of("message", "AI正在思考中...")));
 
                    // 调用模型流式接口：输入就是用户本轮的问题
                    // 调用模型的stream方法获取流式响应
                    // 这里使用Spring AI的Flux响应式流来处理AI模型的实时输出
                    // ChatResponse是Spring AI定义的标准响应格式
                    Flux<org.springframework.ai.chat.model.ChatResponse> stream = chatModel.stream(new Prompt(request.getMessage()));
 
                    // 用于累积存储AI的完整响应文本
                    StringBuilder full = new StringBuilder();
                    
                    stream.subscribe(
                            part -> {
                                try {
                                    // 从响应部分获取AI生成的文本内容
                                    String chunk = part.getResult().getOutput().getContent();
                                    
                                    if (chunk != null && !chunk.isEmpty()) {

                                        full.append(chunk);
                                        // 发送数据事件，包含当前块的内容
                                        emitter.send(SseEmitter.event().name("data").data(Map.of("chunk", chunk)));

                                    }
                                } catch (IOException ioe) {
                                    log.error("SSE发送数据失败", ioe);
                                    emitter.completeWithError(ioe);
                                }
                            },
                            err -> {
                                log.error("AI流式响应异常", err);
                                try {
                                    emitter.send(SseEmitter.event().name("error").data(Map.of("success", false, "message", "AI服务异常: " + err.getMessage())));
                                } catch (IOException ioe) {
                                    log.error("SSE发送错误事件失败", ioe);
                                }
                                emitter.completeWithError(err);
                            },
                            () -> {
                                try {
                                    emitter.send(SseEmitter.event().name("complete").data(Map.of("success", true, "totalLength", full.length())));
                                    emitter.complete();
                                } catch (IOException ioe) {
                                    log.error("SSE发送完成事件失败", ioe);
                                    emitter.completeWithError(ioe);
                                }
                            }
                    );
                } catch (Exception ex) {
                    log.error("处理SSE流异常", ex);
                    try {
                        emitter.send(SseEmitter.event().name("error").data(Map.of("success", false, "message", "处理请求异常: " + ex.getMessage())));
                    } catch (IOException ioe) {
                        log.error("SSE发送异常事件失败", ioe);
                    }
                    emitter.completeWithError(ex);
                }
            });
        } catch (Exception e) {
            log.error("创建SSE失败", e);
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of("success", false, "message", "创建流式连接失败: " + e.getMessage())));
            } catch (IOException ioe) {
                log.error("SSE发送初始化错误失败", ioe);
            }
            emitter.completeWithError(e);
        }
        return emitter;
    }
}