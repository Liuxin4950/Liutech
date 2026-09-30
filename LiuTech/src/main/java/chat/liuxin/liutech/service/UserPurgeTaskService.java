package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.UserPurgeTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.UUID;

/** HTTP 调用在数据库事务之外；租约过期可恢复，AI 永久清理接口幂等。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPurgeTaskService {
    private final UserPurgeTaskMapper tasks;
    private final AiUserDataClient aiClient;

    @Scheduled(scheduler = "userPurgeScheduler", fixedDelayString = "${ai.user-data.cleanup.interval-ms:10000}", initialDelayString = "${ai.user-data.cleanup.initial-delay-ms:10000}")
    public void processPending() {
        for (Long id : tasks.selectDueIds()) {
            String lease = UUID.randomUUID().toString();
            if (tasks.claim(id, lease) != 1) continue;
            try {
                aiClient.purgeUser(id);
                if (tasks.complete(id, lease) == 1) log.info("用户跨服务清理任务完成: userId={}", id);
            } catch (Exception e) {
                // 仅存错误类别，不把上游响应、凭据或个人数据存入任务台账。
                tasks.retryLater(id, lease, e.getClass().getSimpleName());
                log.warn("用户跨服务清理暂未完成，将重试: userId={}", id, e);
            }
        }
    }
}
