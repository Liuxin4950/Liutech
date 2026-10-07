package chat.liuxin.ai.service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 新正文与局部修改共用的完整 HTML 校验边界；原稿媒体只允许完整原样保留。 */
public final class WritingHtmlValidator {
    private WritingHtmlValidator() {}

    private static final Set<String> ARTICLE_TAGS = Set.of("article", "section", "div", "h1", "h2", "h3", "h4", "h5", "h6", "p", "pre", "code", "ul", "ol", "li", "blockquote", "strong", "em", "b", "i", "u", "s", "del", "a", "img", "br", "hr", "table", "thead", "tbody", "tfoot", "tr", "th", "td", "span", "figure", "figcaption", "sup", "sub", "mark", "dl", "dt", "dd", "details", "summary");
    private static final Set<String> VOID_TAGS = Set.of("img", "br", "hr");
    private static final Set<String> BLOCK_TAGS = Set.of("article", "section", "div", "h1", "h2", "h3", "h4", "h5", "h6", "p", "pre", "ul", "ol", "blockquote", "table", "figure", "dl", "details");
    private static final Set<String> EXISTING_MEDIA_TAGS = Set.of("audio", "video", "iframe", "svg", "math");
    private static final Pattern HTML_TAG = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9]*)((?:[^<>\"']|\"[^\"]*\"|'[^']*')*)>");
    private static final Pattern UNSAFE_ATTRIBUTE = Pattern.compile("(?i)\\s(?:on[a-z]+|srcdoc)\\s*=|(?:javascript|vbscript|data)\\s*:");

    /** 只接受完整合法的文章 HTML 片段，禁止从聊天/半截正文中猜测一个可回写区间。 */
    public static String validate(String text, String originalContent) {
        if (text == null) return null;
        String html = text.trim();
        if (!html.startsWith("<") || !html.endsWith(">")) return null;
        // 原稿完整媒体节点只供原样保留：验证时遮罩其内部结构，返回时仍使用完整原输出。
        // 前端会在惰性 DOM 中净化保留节点；新媒体或被改动的子资源不会获得例外。
        String validationHtml = maskExistingMedia(html, originalContent);
        Matcher matcher = HTML_TAG.matcher(validationHtml);
        Deque<String> stack = new ArrayDeque<>();
        int previous = 0;
        boolean first = true;
        while (matcher.find()) {
            String between = validationHtml.substring(previous, matcher.start());
            if (between.indexOf('<') >= 0 || (stack.isEmpty() && !between.isBlank())) return null;
            String tag = matcher.group(2).toLowerCase(Locale.ROOT);
            String attributes = matcher.group(3);
            if (!ARTICLE_TAGS.contains(tag) || UNSAFE_ATTRIBUTE.matcher(attributes).find()) return null;
            boolean closing = !matcher.group(1).isEmpty();
            if (first && (closing || !BLOCK_TAGS.contains(tag))) return null;
            first = false;
            if (closing) {
                if (!attributes.isBlank() || stack.isEmpty() || !stack.pop().equals(tag)) return null;
            } else if (!VOID_TAGS.contains(tag)) {
                if (!attributes.endsWith("/")) stack.push(tag);
            }
            previous = matcher.end();
        }
        return !first && stack.isEmpty() && validationHtml.substring(previous).isBlank() ? html : null;
    }

    /** 复用标签扫描器提取五类既有媒体的完整根节点，不解析或信任生成内容中的未知节点。 */
    private static String maskExistingMedia(String html, String originalContent) {
        if (originalContent == null || originalContent.isBlank()) return html;
        Matcher matcher = HTML_TAG.matcher(originalContent);
        String mediaTag = null;
        int start = 0;
        int depth = 0;
        List<String> nodes = new ArrayList<>();
        while (matcher.find()) {
            String tag = matcher.group(2).toLowerCase(Locale.ROOT);
            boolean closing = !matcher.group(1).isEmpty();
            boolean selfClosing = matcher.group(3).trim().endsWith("/");
            if (mediaTag == null) {
                if (closing || !EXISTING_MEDIA_TAGS.contains(tag)) continue;
                start = matcher.start();
                if (selfClosing) {
                    nodes.add(originalContent.substring(start, matcher.end()));
                } else {
                    mediaTag = tag;
                    depth = 1;
                }
            } else if (mediaTag.equals(tag)) {
                if (closing) depth--;
                else if (!selfClosing) depth++;
                if (depth == 0) {
                    nodes.add(originalContent.substring(start, matcher.end()));
                    mediaTag = null;
                }
            }
        }
        for (String node : nodes) html = html.replace(node, "<div></div>");
        return html;
    }

}
