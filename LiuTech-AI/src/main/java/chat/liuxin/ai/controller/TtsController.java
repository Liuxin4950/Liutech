package chat.liuxin.ai.controller;

import chat.liuxin.ai.service.tts.TtsSpeechService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** AI 服务生成的临时 TTS 音频。 */
@RestController
@RequestMapping("/ai/tts")
@RequiredArgsConstructor
public class TtsController {

    private final TtsSpeechService ttsSpeechService;

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
}
