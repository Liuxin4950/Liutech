package chat.liuxin.ai.service;

import chat.liuxin.ai.dto.WritingContentPatch;
import chat.liuxin.ai.dto.ChatRequest;
import chat.liuxin.ai.infra.exception.AIServiceException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/** 每轮不可变原稿和原子补丁集合。模型不能把改后的内容当作下一次定位依据。 */
public final class WritingContentSession {
    public static final String CONTEXT_KEY = "writingContentSession";
    private static final int MAX_EDITS = 32;
    private static final int MAX_CONTENT_LENGTH = 200_000;
    private static final Pattern HTML_TAG = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9]*)((?:[^<>\"']|\"[^\"]*\"|'[^']*')*)>");

    private final String original;
    private final String revision;
    private List<LocatedEdit> edits = List.of();
    private boolean reviewed;

    public WritingContentSession(String original) {
        this.original = original == null ? "" : original;
        try {
            revision = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(this.original.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 不可用", error);
        }
    }

    public String revision() { return revision; }

    /** 前端只选择正文建议形态；权限与字段范围仍由服务端校验。 */
    public static String contentMode(ChatRequest request) {
        Object requested = request.getContext() == null ? null : request.getContext().get("contentMode");
        if (requested instanceof String mode) return mode;
        return request.getDraft() != null && request.getDraft().getContent() != null
                && !request.getDraft().getContent().isBlank() ? "patch" : "replace";
    }
    public synchronized boolean isEmpty() { return edits.isEmpty(); }
    public synchronized boolean wasReviewed() { return reviewed; }

    /** 本次调用先全部检查，再替换集合；任何一处失败都不留下部分补丁。 */
    public synchronized void add(List<WritingContentPatch.Edit> requested) {
        if (original.isBlank()) throw invalid("当前没有正文，请生成新文章的完整 HTML");
        if (requested == null || requested.size() + edits.size() > MAX_EDITS) {
            throw invalid("每轮最多修改 32 处正文，请分轮处理");
        }
        List<LocatedEdit> next = new ArrayList<>(edits);
        for (WritingContentPatch.Edit edit : requested) {
            if (edit == null || edit.before() == null || edit.before().isBlank() || edit.after() == null
                    || edit.before().length() > MAX_CONTENT_LENGTH || edit.after().length() > MAX_CONTENT_LENGTH) {
                throw invalid("正文修改必须包含原稿的完整 HTML 片段和替换内容");
            }
            if (edit.before().equals(edit.after())) throw invalid("正文修改与原文相同，请只提交确实需要修改的段落");
            int start = original.indexOf(edit.before());
            if (start < 0 || original.indexOf(edit.before(), start + 1) >= 0) {
                throw invalid("修改片段无法唯一匹配原稿，请逐字复制更完整的相邻段落");
            }
            int end = start + edit.before().length();
            if (!atTagBoundaries(start, end)
                    || WritingHtmlValidator.validate(edit.before(), original) == null
                    || (!edit.after().isEmpty() && WritingHtmlValidator.validate(edit.after(), original) == null)) {
                throw invalid("局部修改必须使用完整合法的 HTML 段落，不能修改半截标签或属性");
            }
            if (next.stream().anyMatch(existing -> start < existing.end() && end > existing.start())) {
                throw invalid("正文修改片段重叠，请合并为一处修改后重试");
            }
            next.add(new LocatedEdit(start, end, edit));
        }
        next.sort(Comparator.comparingInt(LocatedEdit::start));
        String candidate = assemble(next);
        if (candidate.isBlank() || candidate.length() > MAX_CONTENT_LENGTH) {
            throw invalid("修改后的正文为空或超过 200000 字符，请调整修改范围");
        }
        edits = List.copyOf(next);
        reviewed = true;
    }

    /** 补丁的完整片段已验证；原稿未修改部分按字节对应的原字符串保留。 */
    public synchronized String resultHtml() { return assemble(edits); }

    public synchronized WritingContentPatch finish() {
        return edits.isEmpty() ? null : new WritingContentPatch(revision, edits.stream().map(LocatedEdit::edit).toList());
    }

    private boolean atTagBoundaries(int start, int end) {
        var matcher = HTML_TAG.matcher(original);
        boolean startsAtTag = false;
        boolean endsAtTag = false;
        while (matcher.find()) {
            if (matcher.start() == start) startsAtTag = true;
            if (matcher.end() == end) endsAtTag = true;
        }
        return startsAtTag && endsAtTag;
    }

    private String assemble(List<LocatedEdit> changes) {
        StringBuilder result = new StringBuilder();
        int position = 0;
        for (LocatedEdit edit : changes) {
            result.append(original, position, edit.start()).append(edit.edit().after());
            position = edit.end();
        }
        return result.append(original, position, original.length()).toString();
    }

    private static AIServiceException.RequestException invalid(String message) {
        return new AIServiceException.RequestException(message);
    }

    private record LocatedEdit(int start, int end, WritingContentPatch.Edit edit) { }
}
