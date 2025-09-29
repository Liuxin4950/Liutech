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
        CAPABILITY_INQUIRY, // 功能查询
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
        PrimaryIntent.CAPABILITY_INQUIRY, Arrays.asList(
            Pattern.compile(".*[你能|你会|你可以|能做|会做|可以做].*[什么|哪些|那些].*"),
            Pattern.compile(".*[功能|能力|本领|技能].*[有哪些|是什么|都有什么].*"),
            Pattern.compile(".*[还有|还能|还会|还可以].*[什么|哪些|那些].*"),
            Pattern.compile(".*[纳西妲|AI|助手].*[能|会|可以].*[干|做|帮].*[什么|哪些].*"),
            Pattern.compile(".*[跳转|导航].*[那些|哪些|什么].*[页面|地方].*"),
            Pattern.compile(".*[有什么|都有什么|还有什么].*[功能|能力|页面].*")
        ),
        PrimaryIntent.NAVIGATION, Arrays.asList(
            // 首页相关
            Pattern.compile(".*[首页|主页|回到首页|返回首页|home].*"),
            // 文章相关页面
            Pattern.compile(".*[发布文章|写文章|创建文章|发表文章|create].*"),
            Pattern.compile(".*[我的文章|个人文章|my.?posts].*"),
            Pattern.compile(".*[草稿箱|草稿|drafts].*"),
            Pattern.compile(".*[收藏|我的收藏|favorites].*"),
            Pattern.compile(".*[全部文章|所有文章|文章列表|posts].*"),
            // 分类和标签
            Pattern.compile(".*[分类|类别|categories].*"),
            Pattern.compile(".*[标签|tags].*"),
            Pattern.compile(".*[归档|文章归档|archive].*"),
            // 个人相关
            Pattern.compile(".*[个人资料|个人信息|profile].*"),
            Pattern.compile(".*[关于我|关于|about].*"),
            Pattern.compile(".*[聊天记录|聊天历史|chat.?history].*"),
            // 通用导航
            Pattern.compile(".*[跳转|导航|链接|去|到].*"),
            Pattern.compile(".*[页面|打开|进入|访问].*"),
            Pattern.compile(".*[返回|后退|前进|刷新].*")
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
    
    // 动作类型枚举
    public enum ActionType {
        NAVIGATE,    // 导航类：navigate:home, navigate:posts
        INTERACT,    // 交互类：interact:like, interact:favorite
        SEARCH,      // 搜索类：search:posts, search:tags
        SHOW,        // 展示类：show:capabilities, show:help
        NONE         // 无动作
    }
    
    // 导航目标映射
    private static final Map<String, String> NAVIGATION_TARGETS = createNavigationTargets();
    
    private static Map<String, String> createNavigationTargets() {
        Map<String, String> map = new HashMap<>();
        map.put("首页|主页|回到首页|返回首页|home", "home");
        map.put("发布文章|写文章|创建文章|发表文章|create", "create-post");
        map.put("我的文章|个人文章|my.?posts", "my-posts");
        map.put("草稿箱|草稿|drafts", "drafts");
        map.put("收藏|我的收藏|favorites", "favorites");
        map.put("全部文章|所有文章|文章列表|posts", "posts");
        map.put("分类|类别|categories", "categories");
        map.put("标签|tags", "tags");
        map.put("归档|文章归档|archive", "archive");
        map.put("个人资料|个人信息|profile", "profile");
        map.put("关于我|关于|about", "about");
        map.put("聊天记录|聊天历史|chat.?history", "chat-history");
        return map;
    }

    // 交互动作映射
    private static final Map<String, String> INTERACTION_ACTIONS = createInteractionActions();
    
    private static Map<String, String> createInteractionActions() {
        Map<String, String> map = new HashMap<>();
        map.put("点赞|喜欢|like|赞", "like");
        map.put("收藏|加星|favorite|mark", "favorite");
        map.put("分享|share", "share");
        map.put("评论|comment", "comment");
        return map;
    }

    // 搜索类型映射
    private static final Map<String, String> SEARCH_TYPES = createSearchTypes();
    
    private static Map<String, String> createSearchTypes() {
        Map<String, String> map = new HashMap<>();
        map.put("文章|帖子|posts", "posts");
        map.put("标签|tags", "tags");
        map.put("分类|类别|categories", "categories");
        map.put("用户|users", "users");
        return map;
    }
    
    /**
     * 根据意图识别结果生成结构化动作
     */
    public String generateStructuredAction(IntentResult intentResult) {
        String input = intentResult.getOriginalInput().toLowerCase();
        
        // 导航类动作
        if (intentResult.getPrimaryIntent() == PrimaryIntent.NAVIGATION) {
            String target = findMatchingTarget(input, NAVIGATION_TARGETS);
            if (target != null) {
                return "navigate:" + target;
            }
        }
        
        // 交互类动作
        if (intentResult.getPrimaryIntent() == PrimaryIntent.SOCIAL || 
            intentResult.getPrimaryIntent() == PrimaryIntent.FEEDBACK) {
            String action = findMatchingTarget(input, INTERACTION_ACTIONS);
            if (action != null) {
                return "interact:" + action;
            }
        }
        
        // 搜索类动作
        if (intentResult.getPrimaryIntent() == PrimaryIntent.INFORMATION ||
            intentResult.getPrimaryIntent() == PrimaryIntent.QUESTION) {
            String searchType = findMatchingTarget(input, SEARCH_TYPES);
            if (searchType != null) {
                return "search:" + searchType;
            }
        }
        
        // 展示类动作
        if (intentResult.getPrimaryIntent() == PrimaryIntent.CAPABILITY_INQUIRY) {
            return "show:capabilities";
        }
        
        // 默认无动作
        return "none";
    }
    
    /**
     * 在映射中查找匹配的目标
     */
    private String findMatchingTarget(String input, Map<String, String> targetMap) {
        for (Map.Entry<String, String> entry : targetMap.entrySet()) {
            String pattern = entry.getKey();
            String target = entry.getValue();
            
            if (input.matches(".*[" + pattern + "].*")) {
                return target;
            }
        }
        return null;
    }
    
    /**
     * 解析结构化动作
     */
    public ActionInfo parseAction(String structuredAction) {
        if ("none".equals(structuredAction)) {
            return new ActionInfo(ActionType.NONE, null, null);
        }
        
        String[] parts = structuredAction.split(":", 2);
        if (parts.length != 2) {
            return new ActionInfo(ActionType.NONE, null, null);
        }
        
        ActionType type;
        try {
            type = ActionType.valueOf(parts[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            return new ActionInfo(ActionType.NONE, null, null);
        }
        
        return new ActionInfo(type, parts[0], parts[1]);
    }
    
    /**
     * 动作信息类
     */
    @lombok.Data
    public static class ActionInfo {
        private ActionType type;
        private String category;
        private String value;
        
        public ActionInfo(ActionType type, String category, String value) {
            this.type = type;
            this.category = category;
            this.value = value;
        }
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