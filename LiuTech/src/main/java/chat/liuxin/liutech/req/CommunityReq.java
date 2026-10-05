package chat.liuxin.liutech.req;

import jakarta.validation.constraints.*;
import java.util.List;

/** 社区接口输入，业务校验由 CommunityService 集中执行。 */
public final class CommunityReq {
    private CommunityReq() {}
    public record Bot(@NotBlank @Size(max=80) String name, @Size(max=1000) String avatarUrl,
        @NotBlank @Size(max=10000) String personality, @Size(max=10000) String background,
        @Size(max=1000) String interests, @NotNull Boolean enabled, @NotNull @Min(0) @Max(100) Integer participation,
        @Size(max=10000) String systemPrompt) {
        public Bot(String name, String avatarUrl, String personality, String background, String interests,
                   Boolean enabled, Integer participation) {
            this(name, avatarUrl, personality, background, interests, enabled, participation, null);
        }
    }
    public record Knowledge(@NotBlank @Size(max=200) String title, @NotBlank @Size(max=200000) String content) {}
    public record Settings(@NotNull Boolean enabled, @Min(0) @Max(1000) int botDailyCommentLimit,
        @Min(0) @Max(10000) int siteDailyCommentLimit, @Min(0) @Max(1000) int postDailyCommentLimit,
        @Min(0) @Max(1000) int botDailyTaskLimit, @Min(0) @Max(10000) int siteDailyTaskLimit,
        @Min(0) @Max(3600) int minDelaySeconds, @Min(0) @Max(3600) int maxDelaySeconds,
        @Min(0) @Max(3600) int cooldownSeconds, @Min(1) @Max(4) int maxChainComments) {}
    public record Enabled(@NotNull Boolean enabled) {}
    public record Invite(@Size(max=2) List<@Positive Long> botIds) {}
    public record Claim(@Min(1) @Max(20) int limit, @Min(60) @Max(600) int leaseSeconds) {}
    public record Ack(@NotBlank String leaseToken) {}
    public record Attempt(@NotBlank String taskId, @Min(1) @Max(10) int attempt,
        @NotNull @Positive Long botId, @NotNull @Positive Long postId, boolean preview) {}
    public record Publish(@NotBlank String taskId, @NotNull @Positive Long botId, @NotNull @Positive Long postId,
        @Positive Long parentId, @Positive Long contextCommentId, @NotBlank String rootEventId,
        @NotBlank String contextVersion, @NotBlank @Size(max=1000) String content) {}
    public record Visibility(@NotNull @Size(max=200) List<@Positive Long> sourcePostIds,
        @NotNull @Size(max=200) List<@Positive Long> sourceCommentIds) {}
}
