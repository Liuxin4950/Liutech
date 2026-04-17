package chat.liuxin.ai.dto;

import lombok.Data;

/**
 * 作者资料 DTO
 * 用于给 AI 注入博客作者与站点基础信息
 */
@Data
public class AuthorProfileDTO {

    private String name;
    private String title;
    private String avatar;
    private String bio;
    private Long posts;
    private Long comments;
    private Long views;

    public String toAiReadableFormat() {
        StringBuilder sb = new StringBuilder();
        sb.append("【站点作者】").append(valueOrDefault(name, "未知作者")).append("\n");
        if (hasText(title)) {
            sb.append("【作者身份】").append(title).append("\n");
        }
        if (hasText(bio)) {
            sb.append("【作者简介】").append(bio).append("\n");
        }
        sb.append("【站点统计】文章 ").append(numberOrZero(posts))
                .append(" 篇，评论 ").append(numberOrZero(comments))
                .append(" 条，浏览 ").append(numberOrZero(views))
                .append(" 次。\n");
        sb.append("请将该博客视为作者的个人技术博客，回答时优先基于站点现有内容，不要编造不存在的经历或文章。");
        return sb.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String valueOrDefault(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private long numberOrZero(Long value) {
        return value != null ? value : 0L;
    }
}
