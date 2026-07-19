package chat.liuxin.liutech.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.dto.TtsPublicStatusDTO;
import chat.liuxin.liutech.model.dto.TtsSpeechRequestDTO;
import chat.liuxin.liutech.model.dto.TtsSpeechResponseDTO;
import chat.liuxin.liutech.service.TtsSpeechService;
import chat.liuxin.liutech.service.TtsStatusService;
import jakarta.validation.Valid;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * TTS（语音推理）公共接口
 *
 * 说明：
 * - 前端和 AI 服务用它判断“语音功能是否可用”
 * - 公共状态只返回最小字段，完整配置和诊断信息走 /admin/tts/**
 */
@RestController
@RequestMapping("/tts")
@RequiredArgsConstructor
public class TtsController {

    private final TtsStatusService ttsStatusService;

    private final TtsSpeechService ttsSpeechService;

    @Value("${tts.proxy.internal-token:${TTS_PROXY_INTERNAL_TOKEN:}}")
    private String internalToken;

    @GetMapping("/status")
    public Result<TtsPublicStatusDTO> status() {
        return Result.success(TtsPublicStatusDTO.from(ttsStatusService.getStatus()));
    }

    @PostMapping("/speech")
    public Result<TtsSpeechResponseDTO> speech(
            @Valid @RequestBody TtsSpeechRequestDTO request,
            @RequestHeader(value = "X-TTS-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        return Result.success(ttsSpeechService.synthesize(request.getText()));
    }

    @GetMapping("/audio/{fileName:.+}")
    public ResponseEntity<Resource> audio(@PathVariable String fileName) {
        Path path = ttsSpeechService.resolveAudioFile(fileName);
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .contentType(ttsSpeechService.mediaTypeFor(fileName))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .body(resource);
    }

    private void assertInternalToken(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new chat.liuxin.liutech.common.BusinessException(
                    chat.liuxin.liutech.common.ErrorCode.FORBIDDEN,
                    "TTS 内部调用令牌未配置");
        }
        if (!internalToken.equals(token)) {
            throw new chat.liuxin.liutech.common.BusinessException(
                    chat.liuxin.liutech.common.ErrorCode.FORBIDDEN,
                    "TTS 内部调用令牌无效");
        }
    }
}
