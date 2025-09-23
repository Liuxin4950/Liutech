package chat.liuxin.ai.service;

import chat.liuxin.ai.entity.AiChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 上下文增强服务
 * 
 * 功能：
 * 1. 分析用户输入的意图和情感
 * 2. 构建更丰富的上下文信息
 * 3. 提取关键词和实体
 * 4. 优化历史对话的上下文构建
 * 
 * 作者：刘鑫
 * 时间：2025-09-24
 */
@Slf4j
@Service
public class ContextEnhancementService {
    
    // 意图识别关键词映射
    private static final Map<String, List<String>> INTENT_KEYWORDS = Map.of(
        "question", Arrays.asList("什么", "怎么", "为什么", "如何", "哪里", "谁", "?", "？"),
        "request", Arrays.asList("请", "帮我", "能否", "可以", "希望", "想要"),
        "praise", Arrays.asList("好", "棒", "不错", "优秀", "赞", "喜欢", "厉害"),
        "complaint", Arrays.asList("不好", "差", "糟糕", "问题", "错误", "失望"),
        "action", Arrays.asList("点赞", "收藏", "分享", "首页", "返回", "打开"),
        "greeting", Arrays.asList("你好", "hi", "hello", "早上好", "晚上好", "再见")
    );
    
    // 情感分析关键词
    private static final Map<String, List<String>> EMOTION_KEYWORDS = Map.of(
        "happy", Arrays.asList("开心", "高兴", "快乐", "兴奋", "满意", "😊", "😄", "👍"),
        "sad", Arrays.asList("难过", "伤心", "失望", "沮丧", "😢", "😭", "💔"),
        "angry", Arrays.asList("生气", "愤怒", "不满", "烦躁", "😠", "😡", "💢"),
        "confused", Arrays.asList("困惑", "不懂", "疑惑", "迷茫", "🤔", "❓"),
        "excited", Arrays.asList("激动", "兴奋", "期待", "惊喜", "🎉", "✨", "🔥")
    );
    
    // 实体提取正则表达式
    private static final Map<String, Pattern> ENTITY_PATTERNS = Map.of(
        "number", Pattern.compile("\\d+"),
        "url", Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+"),
        "email", Pattern.compile("[\\w\\.-]+@[\\w\\.-]+\\.[a-zA-Z]{2,}"),
        "date", Pattern.compile("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}|\\d{1,2}[-/]\\d{1,2}[-/]\\d{4}")
    );
    
    /**
     * 增强用户输入的上下文信息
     */
    public EnhancedContext enhanceUserInput(String userInput, Map<String, Object> context, 
                                          List<AiChatMessage> recentHistory) {
        log.debug("开始增强用户输入上下文，输入长度: {}, 历史记录数: {}", 
            userInput.length(), recentHistory.size());
        
        EnhancedContext enhanced = new EnhancedContext();
        enhanced.setOriginalInput(userInput);
        enhanced.setContext(context != null ? context : new HashMap<>());
        
        // 1. 意图识别
        enhanced.setIntent(detectIntent(userInput));
        
        // 2. 情感分析
        enhanced.setEmotion(detectEmotion(userInput));
        
        // 3. 实体提取
        enhanced.setEntities(extractEntities(userInput));
        
        // 4. 关键词提取
        enhanced.setKeywords(extractKeywords(userInput));
        
        // 5. 上下文相关性分析
        enhanced.setContextRelevance(analyzeContextRelevance(userInput, recentHistory));
        
        // 6. 构建增强的输入文本
        enhanced.setEnhancedInput(buildEnhancedInput(userInput, enhanced));
        
        log.debug("上下文增强完成，意图: {}, 情感: {}, 关键词数: {}", 
            enhanced.getIntent(), enhanced.getEmotion(), enhanced.getKeywords().size());
        
        return enhanced;
    }
    
    /**
     * 意图识别
     */
    private String detectIntent(String input) {
        String lowerInput = input.toLowerCase();
        
        for (Map.Entry<String, List<String>> entry : INTENT_KEYWORDS.entrySet()) {
            String intent = entry.getKey();
            List<String> keywords = entry.getValue();
            
            long matchCount = keywords.stream()
                .mapToLong(keyword -> countOccurrences(lowerInput, keyword.toLowerCase()))
                .sum();
            
            if (matchCount > 0) {
                return intent;
            }
        }
        
        return "general";
    }
    
    /**
     * 情感分析
     */
    private String detectEmotion(String input) {
        String lowerInput = input.toLowerCase();
        Map<String, Long> emotionScores = new HashMap<>();
        
        for (Map.Entry<String, List<String>> entry : EMOTION_KEYWORDS.entrySet()) {
            String emotion = entry.getKey();
            List<String> keywords = entry.getValue();
            
            long score = keywords.stream()
                .mapToLong(keyword -> countOccurrences(lowerInput, keyword.toLowerCase()))
                .sum();
            
            if (score > 0) {
                emotionScores.put(emotion, score);
            }
        }
        
        return emotionScores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("neutral");
    }
    
    /**
     * 实体提取
     */
    private Map<String, List<String>> extractEntities(String input) {
        Map<String, List<String>> entities = new HashMap<>();
        
        for (Map.Entry<String, Pattern> entry : ENTITY_PATTERNS.entrySet()) {
            String entityType = entry.getKey();
            Pattern pattern = entry.getValue();
            
            List<String> matches = pattern.matcher(input)
                .results()
                .map(matchResult -> matchResult.group())
                .collect(Collectors.toList());
            
            if (!matches.isEmpty()) {
                entities.put(entityType, matches);
            }
        }
        
        return entities;
    }
    
    /**
     * 关键词提取
     */
    private List<String> extractKeywords(String input) {
        // 简单的关键词提取：去除停用词，提取长度大于1的词
        Set<String> stopWords = Set.of("的", "了", "在", "是", "我", "你", "他", "她", "它", 
            "这", "那", "有", "和", "与", "或", "但", "而", "就", "都", "也", "还", "又");
        
        return Arrays.stream(input.split("[\\s\\p{Punct}]+"))
            .filter(word -> word.length() > 1)
            .filter(word -> !stopWords.contains(word))
            .distinct()
            .limit(10) // 限制关键词数量
            .collect(Collectors.toList());
    }
    
    /**
     * 分析与历史对话的相关性
     */
    private double analyzeContextRelevance(String input, List<AiChatMessage> recentHistory) {
        if (recentHistory.isEmpty()) {
            return 0.0;
        }
        
        Set<String> inputWords = new HashSet<>(Arrays.asList(input.toLowerCase().split("\\s+")));
        
        double totalRelevance = 0.0;
        int validMessages = 0;
        
        for (AiChatMessage message : recentHistory) {
            if (message.getContent() != null) {
                Set<String> messageWords = new HashSet<>(
                    Arrays.asList(message.getContent().toLowerCase().split("\\s+"))
                );
                
                // 计算词汇重叠度
                Set<String> intersection = new HashSet<>(inputWords);
                intersection.retainAll(messageWords);
                
                Set<String> union = new HashSet<>(inputWords);
                union.addAll(messageWords);
                
                double relevance = union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
                totalRelevance += relevance;
                validMessages++;
            }
        }
        
        return validMessages > 0 ? totalRelevance / validMessages : 0.0;
    }
    
    /**
     * 构建增强的输入文本
     */
    private String buildEnhancedInput(String originalInput, EnhancedContext context) {
        StringBuilder enhanced = new StringBuilder();
        
        // 添加意图和情感信息
        if (!"general".equals(context.getIntent()) || !"neutral".equals(context.getEmotion())) {
            enhanced.append("[用户意图: ").append(context.getIntent())
                .append(", 情感: ").append(context.getEmotion()).append("] ");
        }
        
        // 添加关键词信息
        if (!context.getKeywords().isEmpty()) {
            enhanced.append("[关键词: ")
                .append(String.join(", ", context.getKeywords()))
                .append("] ");
        }
        
        // 添加原始输入
        enhanced.append(originalInput);
        
        return enhanced.toString();
    }
    
    /**
     * 计算字符串中子串出现次数
     */
    private long countOccurrences(String text, String substring) {
        if (text.isEmpty() || substring.isEmpty()) {
            return 0;
        }
        
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
    
    /**
     * 增强上下文数据类
     */
    @lombok.Data
    public static class EnhancedContext {
        private String originalInput;
        private String enhancedInput;
        private String intent;
        private String emotion;
        private List<String> keywords;
        private Map<String, List<String>> entities;
        private Map<String, Object> context;
        private double contextRelevance;
    }
}