package chat.liuxin.ai.service;

import chat.liuxin.ai.dto.FieldUpdatePayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FieldUpdateParser 单元测试。
 * 覆盖：无标记、完整标记、跨 chunk 标记（start/end 被分割）、非法 JSON、flush 剩余。
 */
class FieldUpdateParserTest {

    private final FieldUpdateParser parser = new FieldUpdateParser();

    @Test
    void feed_无标记_全部作为data() {
        FieldUpdateParser.ParseResult r = parser.feed("普通文本无标记");
        assertEquals(1, r.dataTexts().size());
        assertTrue(r.fieldUpdates().isEmpty());
    }

    @Test
    void feed_完整标记_解析为fieldUpdate() {
        String chunk = "前面文本---field-update---{\"title\":\"新标题\",\"categoryId\":5}---end---后面文本";
        FieldUpdateParser.ParseResult r = parser.feed(chunk);

        assertEquals(2, r.dataTexts().size());
        assertEquals("前面文本", r.dataTexts().get(0));
        assertEquals(1, r.fieldUpdates().size());
        FieldUpdatePayload payload = r.fieldUpdates().get(0);
        assertEquals("新标题", payload.getTitle());
        assertEquals(5L, payload.getCategoryId());
    }

    @Test
    void feed_跨chunk_start标记被分割_累积解析() {
        parser.feed("文本---field");
        FieldUpdateParser.ParseResult r = parser.feed("-update---{\"title\":\"X\"}---end---后续");

        assertEquals(1, r.fieldUpdates().size());
        assertEquals("X", r.fieldUpdates().get(0).getTitle());
    }

    @Test
    void feed_跨chunk_end标记被分割_累积解析() {
        parser.feed("文本---field-update---{\"summary\":\"新摘要\"}");
        FieldUpdateParser.ParseResult r = parser.feed("---end---尾部");

        assertEquals(1, r.fieldUpdates().size());
        assertEquals("新摘要", r.fieldUpdates().get(0).getSummary());
    }

    @Test
    void feed_非法JSON_跳过该fieldUpdate() {
        String chunk = "---field-update---{invalid json}---end---";
        FieldUpdateParser.ParseResult r = parser.feed(chunk);

        assertTrue(r.fieldUpdates().isEmpty());
    }

    @Test
    void feed_多个标记_全部解析() {
        String chunk = "---field-update---{\"title\":\"A\"}---end---中间---field-update---{\"title\":\"B\"}---end---";
        FieldUpdateParser.ParseResult r = parser.feed(chunk);

        assertEquals(2, r.fieldUpdates().size());
        assertEquals("A", r.fieldUpdates().get(0).getTitle());
        assertEquals("B", r.fieldUpdates().get(1).getTitle());
    }

    @Test
    void flush_无标记文本已在feed输出_flush返回空() {
        FieldUpdateParser.ParseResult r = parser.feed("剩余文本");
        assertEquals(1, r.dataTexts().size());
        assertEquals("剩余文本", r.dataTexts().get(0));
        assertEquals("", parser.flush());
    }

    @Test
    void flush_标记未闭合_丢弃未完成部分() {
        parser.feed("文本---field-update---{\"title\":\"X\"}");
        String rest = parser.flush();
        // "文本"已在 feed 时作为 data 输出，未闭合标记部分被丢弃
        assertEquals("", rest);
    }
}
