package chat.liuxin.ai.service;

import chat.liuxin.ai.common.client.BlogApiClient;
import chat.liuxin.ai.common.mcp.ToolResultBudget;
import chat.liuxin.ai.common.mcp.WritingTools;
import chat.liuxin.ai.dto.ChatRequest;
import chat.liuxin.ai.dto.AdminArticleDraftSnapshot;
import chat.liuxin.ai.dto.WritingContentPatch.Edit;
import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.infra.exception.AIServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class WritingContentSessionTest {
    private final WritingTools tools = new WritingTools(mock(BlogApiClient.class), new ToolResultBudget(new AiChatProperties()));

    @Test
    void editingTwoParagraphsPreservesEveryUntouchedByteAndReturnsOnlyEdits() {
        String original = "<!--保留编辑器标记-->\n<p style='color: red'>原来段</p>  \n"
                + "<p>错字一</p><p>中间不改</p><p>错字二</p>\n"
                + "<iframe src='/original' allowfullscreen></iframe><p>尾部</p>";
        var session = new WritingContentSession(original);
        session.add(List.of(new Edit("<p>错字一</p>", "<p>改好一</p>"), new Edit("<p>错字二</p>", "<p>改好二</p>")));
        assertEquals(original.replace("错字一", "改好一").replace("错字二", "改好二"), session.resultHtml());
        assertEquals(2, session.finish().edits().size());
        assertEquals(64, session.finish().baseRevision().length());
        assertEquals(new WritingContentSession(original).revision(), session.revision());
        assertNotEquals(new WritingContentSession(original + " ").revision(), session.revision());
    }

    @Test
    void standardTinyMceInlineAndStructuredTagsAllowTypoEditsAndStillRejectScripts() {
        List<String> paragraphs = List.of("<p><u>错字</u><sup>2</sup><sub>1</sub><mark>说明</mark></p>",
                "<dl><dt>术语</dt><dd>错字</dd></dl>", "<details><summary>展开</summary><p>错字</p></details>");
        for (String paragraph : paragraphs) {
            String original = "<!--原稿标记--><p>保留</p>" + paragraph;
            WritingContentSession session = new WritingContentSession(original);
            String corrected = paragraph.replace("错字", "正确");
            session.add(List.of(new Edit(paragraph, corrected)));
            assertEquals("<!--原稿标记--><p>保留</p>" + corrected, session.resultHtml());
            assertEquals(corrected, WritingHtmlValidator.validate(corrected, original));
            assertNull(WritingHtmlValidator.validate(paragraph.replace("错字", "<script>alert(1)</script>"), original));
        }
    }

    @Test
    void ambiguousAnchorRejectsWholeCallIncludingTheEarlierValidEdit() {
        var session = new WritingContentSession("<p>甲</p><p>重复</p><p>重复</p>");
        assertThrows(AIServiceException.RequestException.class, () -> session.add(List.of(
                new Edit("<p>甲</p>", "<p>乙</p>"), new Edit("<p>重复</p>", "<p>新</p>"))));
        assertTrue(session.isEmpty());
        assertFalse(session.wasReviewed());
    }

    @Test
    void largerAdjacentAnchorCanDisambiguateRepeatedParagraphs() {
        var session = new WritingContentSession("<p>甲</p><p>重复</p><p>重复</p>");
        session.add(List.of(new Edit("<p>甲</p><p>重复</p>", "<p>甲</p><p>只改第一处</p>")));
        assertEquals("<p>甲</p><p>只改第一处</p><p>重复</p>", session.resultHtml());
    }

    @Test
    void overlappingCallsCannotRewriteAnEarlierEditOrPartiallyAppend() {
        var session = new WritingContentSession("<div><p>甲</p><p>乙</p></div><p>丙</p>");
        session.add(List.of(new Edit("<p>甲</p>", "<p>改甲</p>")));
        assertThrows(AIServiceException.RequestException.class, () -> session.add(List.of(
                new Edit("<p>丙</p>", "<p>改丙</p>"),
                new Edit("<div><p>甲</p><p>乙</p></div>", "<div><p>另一修改</p></div>"))));
        assertEquals("<div><p>改甲</p><p>乙</p></div><p>丙</p>", session.resultHtml());
    }

    @Test
    void halfTagsScriptsChangedMediaAndAnchorsInsideAttributesAreRejected() {
        String original = "<p title='literal <p>假段落</p>'>甲</p><p>乙</p><iframe src='/old'></iframe>";
        for (Edit edit : List.of(
                new Edit("甲", "改甲"), new Edit("<p>乙</p>", "<p>半截"),
                new Edit("<p>乙</p>", "<p onclick='alert(1)'>攻击</p>"),
                new Edit("<p>乙</p>", "<script>alert(1)</script>"),
                new Edit("<iframe src='/old'></iframe>", "<iframe src='/new'></iframe>"),
                new Edit("<p>假段落</p>", "<p>插入属性</p>"))) {
            var session = new WritingContentSession(original);
            assertThrows(AIServiceException.RequestException.class, () -> session.add(List.of(edit)), edit.toString());
            assertTrue(session.isEmpty());
        }
    }

    @Test
    void appendAndDeleteUseExplicitAnchorsWithoutReplacingWholeArticle() {
        var session = new WritingContentSession("<p>不动</p><p>删掉</p><p>结尾</p>");
        session.add(List.of(new Edit("<p>删掉</p>", ""), new Edit("<p>结尾</p>", "<p>结尾</p><p>续写</p>")));
        assertEquals("<p>不动</p><p>结尾</p><p>续写</p>", session.resultHtml());
    }

    @Test
    void noChangesIsExplicitAndDoesNotProduceAnEmptyAdoptablePatch() {
        var session = new WritingContentSession("<p>正确</p>");
        session.add(List.of());
        assertTrue(session.wasReviewed());
        assertTrue(session.isEmpty());
        assertNull(session.finish());
    }

    @Test
    void editToolCannotBypassScopeOrWholeArticleMode() {
        for (Map<String, Object> extra : List.of(
                Map.<String, Object>of("allowedWritingFields", List.of("check")),
                Map.<String, Object>of("allowedWritingFields", List.of("title")),
                Map.<String, Object>of("writingContentMode", "replace"))) {
            var session = new WritingContentSession("<p>旧</p>");
            var context = new java.util.HashMap<>(extra);
            context.put(WritingContentSession.CONTEXT_KEY, session);
            assertThrows(AIServiceException.RequestException.class,
                    () -> tools.editArticleContent(List.of(new Edit("<p>旧</p>", "<p>新</p>")), new ToolContext(context)));
            assertFalse(session.wasReviewed());
        }
    }

    @Test
    void emptyNewDraftRequiresWholeArticleButExistingDraftDefaultsToPatch() {
        var request = new ChatRequest();
        assertEquals("replace", WritingContentSession.contentMode(request));
        request.setDraft(new AdminArticleDraftSnapshot());
        request.getDraft().setContent("<p>正文</p>");
        assertEquals("patch", WritingContentSession.contentMode(request));
        request.setContext(Map.of("contentMode", "replace"));
        assertEquals("replace", WritingContentSession.contentMode(request));
        assertThrows(AIServiceException.RequestException.class, () -> new WritingContentSession("").add(List.of()));
    }
}
