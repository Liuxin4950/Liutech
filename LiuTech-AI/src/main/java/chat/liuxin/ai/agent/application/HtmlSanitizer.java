package chat.liuxin.ai.agent.application;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.AttributePolicy;
import org.owasp.html.PolicyFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML 清理和提取工具。
 *
 * 职责：
 * - 使用 OWASP Java HTML Sanitizer 清理 AI 生成的 HTML（安全且标准）
 * - 剥离 AI 助手的前言/旁白文本
 * - 提取标题、摘要等结构化信息
 * - 检测 JSON 元数据尾部并移除
 *
 * 安全说明：
 * 正则清理容易被绕过（如嵌套标签、编码变体），
 * OWASP Java HTML Sanitizer 基于白名单策略，能有效防御 XSS 注入。
 */
final class HtmlSanitizer {

    private HtmlSanitizer() {}

    /**
     * OWASP HTML 策略：仅允许安全的 HTML 标签和属性。
     * 白名单包含 TinyMCE 编辑器常用的格式化标签，
     * 禁止 script、iframe、style、事件属性等危险内容。
     */
    /** 仅允许 data:image/ 前缀的 data: URI，防止 data:text/html 等 XSS 向量 */
    private static final AttributePolicy IMG_DATA_SRC_POLICY = (elementName, attributeName, value) -> {
        if (value != null && value.startsWith("data:image/")) {
            return value;
        }
        return null;
    };

    private static final PolicyFactory HTML_POLICY = new HtmlPolicyBuilder()
            .allowElements(
                    "h1", "h2", "h3", "h4", "h5", "h6",
                    "p", "br", "hr",
                    "ul", "ol", "li",
                    "blockquote", "pre", "code",
                    "strong", "em", "b", "i", "u", "s",
                    "table", "thead", "tbody", "tr", "th", "td",
                    "a", "img",
                    "div", "span", "section", "article"
            )
            .allowAttributes("href").onElements("a")
            .allowAttributes("src", "alt", "title", "width", "height").onElements("img")
            .allowAttributes("class").onElements("h1", "h2", "h3", "h4", "h5", "h6",
                    "p", "pre", "code", "blockquote", "ul", "ol", "li",
                    "table", "thead", "tbody", "tr", "th", "td",
                    "div", "span", "section", "article")
            .allowAttributes("target", "rel").onElements("a")
            .allowUrlProtocols("https", "http")
            .allowAttributes("src").matching(IMG_DATA_SRC_POLICY).onElements("img")
            .toFactory();

    /**
     * 清理 AI 生成的 HTML，确保安全且适合 TinyMCE 编辑器。
     *
     * 处理步骤：
     * 1. 剥离 AI 助手前言和元数据尾部
     * 2. 通过 OWASP 策略清理危险标签和属性
     * 3. 如果没有 HTML 块标签，将纯文本转为 &lt;p&gt; 段落
     */
    static String sanitize(String value) {
        if (isBlank(value)) {
            return defaultFallback();
        }
        // 1. 剥离前言和元数据
        String html = stripMetadataTail(stripPreamble(value))
                .replaceAll("(?is)```html", "")
                .replaceAll("(?is)```", "")
                .trim();
        // 2. 使用 OWASP Sanitizer 安全清理
        html = HTML_POLICY.sanitize(html);
        // 3. 如果没有块级标签，将纯文本包裹为 <p>
        if (!html.matches("(?s).*<\\s*(h2|h3|p|ul|ol|blockquote|pre|div|section)\\b.*")) {
            StringBuilder builder = new StringBuilder();
            for (String paragraph : html.split("\\R{2,}")) {
                String text = paragraph.trim();
                if (!text.isBlank()) {
                    builder.append("<p>").append(escape(text)).append("</p>\n");
                }
            }
            html = builder.toString().trim();
        }
        return stripMetadataTail(html);
    }

    /** 检查 HTML 是否安全（无 script、iframe、事件属性）。 */
    static boolean isSafe(String html) {
        if (html == null) return false;
        String lower = html.toLowerCase();
        return !lower.contains("<script")
                && !lower.contains("<iframe")
                && !lower.contains(" onclick=")
                && !lower.contains(" onload=")
                && !lower.contains("javascript:");
    }

    /** 剥离 HTML 标签，返回纯文本。 */
    static String stripTags(String html) {
        return html == null ? "" : html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    /** 提取 HTML 中的 h1/h2 标题文本。 */
    static String extractHeading(String html) {
        if (html == null) return "";
        Matcher matcher = Pattern.compile("(?is)<h[12][^>]*>(.*?)</h[12]>").matcher(html);
        if (matcher.find()) {
            return stripTags(matcher.group(1));
        }
        return "";
    }

    /** 转义 HTML 特殊字符。 */
    static String escape(String text) {
        return text == null ? "" : text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * 剥离 AI 助手的前言/旁白文本。
     * 例如"我来帮你写一篇文章"这类不包含实际内容的开头。
     */
    static String stripPreamble(String value) {
        if (isBlank(value)) return "";
        String text = removeLeadingSentences(value.trim());
        Matcher firstArticleTag = Pattern
                .compile("(?is)<\\s*(h1|h2|h3|p|ul|ol|blockquote|pre|section|article)\\b")
                .matcher(text);
        if (firstArticleTag.find() && firstArticleTag.start() > 0) {
            String prefix = stripTags(text.substring(0, firstArticleTag.start()));
            if (isAssistantPreamble(prefix)) {
                text = text.substring(firstArticleTag.start()).trim();
            }
        }
        StringBuilder builder = new StringBuilder();
        boolean dropping = true;
        for (String paragraph : text.split("\\R{2,}")) {
            String trimmed = paragraph.trim();
            if (trimmed.isBlank()) continue;
            if (dropping && isAssistantPreamble(stripTags(trimmed))) continue;
            dropping = false;
            if (!builder.isEmpty()) builder.append("\n\n");
            builder.append(trimmed);
        }
        return builder.isEmpty() ? text : builder.toString();
    }

    /**
     * 移除 HTML 末尾的 JSON 元数据块。
     * AI 写作助手会在正文末尾附带分类/标签 JSON，需要在展示前剥离。
     */
    static String stripMetadataTail(String value) {
        String source = defaultString(value).trim();
        if (source.isBlank()) return "";
        Matcher matcher = metadataMatcher(source);
        if (matcher.find()) {
            return source.substring(0, matcher.start()).trim();
        }
        return source;
    }

    /** 解析 HTML 末尾的 JSON 元数据中的分类/标签信息。 */
    static Matcher metadataMatcher(String contentHtml) {
        String source = defaultString(contentHtml);
        String quoted = "(?:\"|&quot;|\\\\\")";
        String maybeQuoted = quoted + "?";
        String taxonomyJson = "(\\{[\\s\\S]*?"
                + maybeQuoted + "categoryId" + maybeQuoted
                + "[\\s\\S]*?" + maybeQuoted + "(?:tagIds|tagNames|categoryName)" + maybeQuoted
                + "[\\s\\S]*?\\})";
        List<Pattern> patterns = List.of(
                Pattern.compile("(?is)(?:<p[^>]*>\\s*)?```\\s*json\\s*(?:</p>\\s*<p[^>]*>\\s*)?" + taxonomyJson + "\\s*```\\s*(?:</p>)?\\s*$"),
                Pattern.compile("(?is)(?:<p[^>]*>\\s*)?json\\s*(?:</p>\\s*<p[^>]*>\\s*)?" + taxonomyJson + "\\s*(?:```)?\\s*(?:</p>)?\\s*$"),
                Pattern.compile("(?is)(?:^|\\R|<p[^>]*>\\s*)" + taxonomyJson + "\\s*(?:</p>)?\\s*$")
        );
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(source);
            if (matcher.find()) {
                matcher.reset();
                return matcher;
            }
        }
        return Pattern.compile("a^").matcher(source);
    }

    /** 反转义 JSON 元数据中的 HTML 实体。 */
    static String unescapeMetadataJson(String value) {
        return defaultString(value)
                .replace("&quot;", "\"")
                .replace("&amp;quot;", "\"")
                .trim();
    }

    // ===== 内部方法 =====

    private static String defaultFallback() {
        return """
                <h2>背景</h2>
                <p>这里补充文章背景和目标读者。</p>
                <h2>核心思路</h2>
                <p>这里展开主要观点和技术路线。</p>
                <h2>实践步骤</h2>
                <ol><li>梳理目标。</li><li>设计实现路径。</li><li>验证结果。</li></ol>
                <h2>总结</h2>
                <p>这篇文章可以继续补充真实案例和代码细节。</p>
                """;
    }

    private static String removeLeadingSentences(String value) {
        String text = value == null ? "" : value.trim();
        boolean changed;
        do {
            changed = false;
            String plain = stripTags(text).trim();
            if (!startsWithPreamblePhrase(plain)) break;
            Matcher sentenceEnd = Pattern.compile("[。.!！]\\s*").matcher(text);
            if (sentenceEnd.find()) {
                text = text.substring(sentenceEnd.end()).trim();
                changed = true;
            }
        } while (changed && !text.isBlank());
        return text;
    }

    private static boolean isAssistantPreamble(String text) {
        if (isBlank(text)) return false;
        String normalized = text.replaceAll("\\s+", "");
        if (normalized.length() > 160) return false;
        return startsWithPreamblePhrase(normalized)
                || containsAny(normalized, "获取一下可用的分类和标签", "获取一下现有的分类和标签", "获取分类和标签信息", "先获取分类标签");
    }

    private static boolean startsWithPreamblePhrase(String text) {
        if (isBlank(text)) return false;
        String normalized = text.replaceAll("\\s+", "");
        return normalized.startsWith("我来帮你")
                || normalized.startsWith("我会帮你")
                || normalized.startsWith("让我")
                || normalized.startsWith("先让我")
                || normalized.startsWith("首先让我")
                || normalized.startsWith("下面")
                || normalized.startsWith("以下是")
                || normalized.startsWith("接下来");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private static boolean containsAny(String text, String... keywords) {
        if (text == null) return false;
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isEmpty() && text.contains(keyword)) return true;
        }
        return false;
    }
}
