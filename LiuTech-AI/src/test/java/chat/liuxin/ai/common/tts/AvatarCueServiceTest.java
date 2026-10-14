package chat.liuxin.ai.common.tts;

import chat.liuxin.ai.infra.config.AvatarCueProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AvatarCueServiceTest {
    private final AvatarCueService cues = new AvatarCueService(new AvatarCueProperties());

    @Test
    void technicalTopicsDoNotActAsSadnessOrAnger() {
        for (String text : new String[]{"接口返回错误", "请求失败", "这是危险操作", "禁止访问", "拒绝连接"}) {
            var cue = cues.fromText(1, null, text);
            assertNotEquals("angry", cue.getExpression(), text);
            assertNotEquals("sad", cue.getExpression(), text);
            assertNull(cue.getMotion());
        }
    }

    @Test
    void explicitEmotionStillProducesCompatibleCues() {
        assertEquals("angry", cues.fromText(1, null, "我很生气！").getExpression());
        assertEquals("sad", cues.fromText(2, null, "有点难过").getExpression());
        var happy = cues.fromText(3, null, "太开心了！");
        assertEquals("happy", happy.getExpression());
        assertTrue(happy.getIntensity() > 0);
        assertEquals(3, happy.getSeq());
    }
}
