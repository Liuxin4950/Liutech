package chat.liuxin.liutech.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import chat.liuxin.liutech.model.dto.TtsStatusDTO;

/**
 * TTS 服务在线探测（薄委托）
 *
 * 实际探测逻辑已合并到 {@link TtsSpeechService}，此类仅保留对外方法签名，
 * 供 TtsController / TtsAdminController 调用，避免改动 Controller。
 */
@Service
@RequiredArgsConstructor
public class TtsStatusService {

    private final TtsSpeechService ttsSpeechService;

    public TtsStatusDTO getStatus() {
        return ttsSpeechService.getStatus();
    }

    public void clearCache() {
        ttsSpeechService.clearStatusCache();
    }
}
