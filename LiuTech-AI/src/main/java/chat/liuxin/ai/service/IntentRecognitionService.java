package chat.liuxin.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 意图识别服务
 * 
 * 功能：
 * 1. 精确识别用户意图类型
 * 2. 分析指令的优先级和紧急程度
 * 3. 提供意图置信度评分
 * 4. 支持多层级意图分类
 * 
 * 作者：刘鑫
 * 时间：2025-09-24
 */
@Slf4j
@Service
public class IntentRecognitionService {
    
    // 主要意图分类
    public enum PrimaryIntent {
        QUESTION,       // 询问问题
        REQUEST,        // 请求帮助
        COMMAND,        // 执行命令
        INFORMATION,    // 获取信息
        SOCIAL,         // 社交互动
        FEEDBACK,       // 反馈评价
        NAVIGATION,     // 导航操作
        UNKNOWN         // 未知意图
    }
    
    // 次要意图分类
    public enum SecondaryIntent {
        URGENT,         // 紧急
        NORMAL,         // 普通
        CASUAL,         // 随意
        EXPLORATORY     // 探索性
    }
    
    // 意图模式定义
    private static final Map<PrimaryIntent, List<Pattern>> INTENT_PATTERNS = Map.of(
        PrimaryIntent.QUESTION, Arrays.asList(
            Pattern.compile(".*[什么|怎么|为什么|如何|哪里|谁|何时|多少].*"),
            Pattern.compile(".*[\\?？].*"),
            Pattern.compile(".*[能告诉我|想知道|请问].*")
        ),
        PrimaryIntent.REQUEST, Arrays.asList(
            Pattern.compile(".*[请|帮我|能否|可以|希望|想要|需要].*"),
            Pattern.compile(".*[帮助|协助|支持].*"),
            Pattern.compile(".*[麻烦|劳烦].*")
        ),
        PrimaryIntent.COMMAND, Arrays.asList(
            Pattern.compile(".*[执行|运行|启动|停止|关闭|打开].*"),
            Pattern.compile(".*[创建|删除|修改|更新|保存].*"),
            Pattern.compile(".*[发送|提交|上传|下载].*")
        ),
        PrimaryIntent.INFORMATION, Arrays.asList(
            Pattern.compile(".*[查看|显示|展示|列出|获取].*"),
            Pattern.compile(".*[状态|信息|详情|数据].*"),
            Pattern.compile(".*[统计|报告|分析].*")
        ),
        PrimaryIntent.SOCIAL, Arrays.asList(
            Pattern.compile(".*[你好|hi|hello|早上好|晚上好|再见].*"),
            Pattern.compile(".*[谢谢|感谢|不客气].*"),
            Pattern.compile(".*[聊天|交流|沟通].*")
        ),
        PrimaryIntent.FEEDBACK, Arrays.asList(
            Pattern.compile(".*[好|棒|不错|优秀|赞|喜欢|厉害].*"),
            Pattern.compile(".*[不好|差|糟糕|问题|错误|失望].*"),
            Pattern.compile(".*[建议|意见|评价|反馈].*")
        ),
        PrimaryIntent.NAVIGATION, Arrays.asList(
            Pattern.compile(".*[首页|返回|后退|前进|刷新].*"),
            Pattern.compile(".*[菜单|设置|配置|选项].*"),
            Pattern.compile(".*[跳转|导航|链接].*")
        )
    );
    
    // 紧急程度关键词
    private static final Map<SecondaryIntent, List<String>> URGENCY_KEYWORDS = Map.of(
        SecondaryIntent.URGENT, Arrays.asList("紧急", "急", "立即", "马上", "快", "尽快", "重要"),
        SecondaryIntent.NORMAL, Arrays.asList("请", "帮忙", "可以", "希望", "想要"),
        SecondaryIntent.CASUAL, Arrays.asList("随便", "看看", "了解", "聊聊", "说说"),
        SecondaryIntent.EXPLORATORY, Arrays.asList("试试", "测试", "实验", "探索", "研究")
    );
    
    /**
     * 识别用户输入的意图
     */
    public IntentResult recognizeIntent(String userInput) {
        log.debug("开始识别用户意图，输入: {}", userInput);
        
        IntentResult result = new IntentResult();
        result.setOriginalInput(userInput);
        
        // 1. 识别主要意图
        result.setPrimaryIntent(detectPrimaryIntent(userInput));
        
        // 2. 识别次要意图（紧急程度）
        result.setSecondaryIntent(detectSecondaryIntent(userInput));
        
        // 3. 计算置信度
        result.setConfidence(calculateConfidence(userInput, result.getPrimaryIntent()));
        
        // 4. 提取动作关键词
        result.setActionKeywords(extractActionKeywords(userInput));
        
        // 5. 分析复杂度
        result.setComplexity(analyzeComplexity(userInput));
        
        // 6. 生成处理建议
        result.setProcessingSuggestion(generateProcessingSuggestion(result));
        
        log.debug("意图识别完成，主要意图: {}, 置信度: {}", 
            result.getPrimaryIntent(), result.getConfidence());
        
        return result;
    }
    
    /**
     * 检测主要意图
     */
    private PrimaryIntent detectPrimaryIntent(String input) {
        String normalizedInput = input.toLowerCase().trim();
        
        Map<PrimaryIntent, Double> intentScores = new HashMap<>();
        
        for (Map.Entry<PrimaryIntent, List<Pattern>> entry : INTENT_PATTERNS.entrySet()) {
            PrimaryIntent intent = entry.getKey();
            List<Pattern> patterns = entry.getValue();
            
            double score = patterns.stream()
                .mapToDouble(pattern -> pattern.matcher(normalizedInput).matches() ? 1.0 : 0.0)
                .sum();
            
            if (score > 0) {
                intentScores.put(intent, score);
            }
        }
        
        return intentScores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(PrimaryIntent.UNKNOWN);
    }
    
    /**
     * 检测次要意图（紧急程度）
     */
    private SecondaryIntent detectSecondaryIntent(String input) {
        String lowerInput = input.toLowerCase();
        
        for (Map.Entry<SecondaryIntent, List<String>> entry : URGENCY_KEYWORDS.entrySet()) {
            SecondaryIntent intent = entry.getKey();
            List<String> keywords = entry.getValue();
            
            boolean hasKeyword = keywords.stream()
                .anyMatch(lowerInput::contains);
            
            if (hasKeyword) {
                return intent;
            }
        }
        
        return SecondaryIntent.NORMAL;
    }
    
    /**
     * 计算意图识别置信度
     */
    private double calculateConfidence(String input, PrimaryIntent intent) {
        if (intent == PrimaryIntent.UNKNOWN) {
            return 0.0;
        }
        
        String normalizedInput = input.toLowerCase().trim();
        List<Pattern> patterns = INTENT_PATTERNS.get(intent);
        
        if (patterns == null) {
            return 0.5;
        }
        
        long matchCount = patterns.stream()
            .mapToLong(pattern -> pattern.matcher(normalizedInput).matches() ? 1 : 0)
            .sum();
        
        double baseConfidence = (double) matchCount / patterns.size();
        
        // 根据输入长度和复杂度调整置信度
        double lengthFactor = Math.min(1.0, input.length() / 50.0);
        double complexityFactor = analyzeComplexity(input) / 10.0;
        
        return Math.min(1.0, baseConfidence * (0.7 + lengthFactor * 0.2 + complexityFactor * 0.1));
    }
    
    /**
     * 提取动作关键词
     */
    private List<String> extractActionKeywords(String input) {
        Set<String> actionWords = Set.of(
            "创建", "删除", "修改", "更新", "保存", "发送", "提交", "上传", "下载",
            "查看", "显示", "展示", "列出", "获取", "执行", "运行", "启动", "停止",
            "关闭", "打开", "搜索", "查找", "分析", "计算", "处理", "生成"
        );
        
        return Arrays.stream(input.split("[\\s\\p{Punct}]+"))
            .filter(actionWords::contains)
            .distinct()
            .collect(Collectors.toList());
    }
    
    /**
     * 分析输入复杂度
     */
    private int analyzeComplexity(String input) {
        int complexity = 0;
        
        // 基于长度
        complexity += Math.min(3, input.length() / 20);
        
        // 基于句子数量
        complexity += input.split("[。！？.!?]").length - 1;
        
        // 基于特殊字符
        complexity += (int) input.chars()
            .filter(ch -> "()[]{}\"'".indexOf(ch) >= 0)
            .count();
        
        // 基于数字和URL
        if (input.matches(".*\\d+.*")) complexity++;
        if (input.matches(".*https?://.*")) complexity++;
        
        return Math.min(10, complexity);
    }
    
    /**
     * 生成处理建议
     */
    private String generateProcessingSuggestion(IntentResult result) {
        StringBuilder suggestion = new StringBuilder();
        
        switch (result.getPrimaryIntent()) {
            case QUESTION:
                suggestion.append("提供详细和准确的答案");
                break;
            case REQUEST:
                suggestion.append("确认需求并提供帮助");
                break;
            case COMMAND:
                suggestion.append("验证权限后执行命令");
                break;
            case INFORMATION:
                suggestion.append("获取并展示相关信息");
                break;
            case SOCIAL:
                suggestion.append("友好回应并维持对话");
                break;
            case FEEDBACK:
                suggestion.append("记录反馈并适当回应");
                break;
            case NAVIGATION:
                suggestion.append("引导用户到目标位置");
                break;
            default:
                suggestion.append("尝试理解用户需求");
        }
        
        if (result.getSecondaryIntent() == SecondaryIntent.URGENT) {
            suggestion.append("，优先处理");
        }
        
        if (result.getComplexity() > 5) {
            suggestion.append("，可能需要分步处理");
        }
        
        return suggestion.toString();
    }
    
    /**
     * 意图识别结果
     */
    @lombok.Data
    public static class IntentResult {
        private String originalInput;
        private PrimaryIntent primaryIntent;
        private SecondaryIntent secondaryIntent;
        private double confidence;
        private List<String> actionKeywords;
        private int complexity;
        private String processingSuggestion;
        
        public IntentResult() {
            this.actionKeywords = new ArrayList<>();
        }
    }
}