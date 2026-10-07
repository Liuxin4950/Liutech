package chat.liuxin.ai.dto.community;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CommunityPreviewRequest(@NotNull @Positive Long botId, @NotNull @Positive Long postId,
                                      @Positive Long commentId) {}
