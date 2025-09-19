package chat.liuxin.liutech.resp;

import lombok.Data;

/**
 * 个人资料响应类
 * 用于首页个人信息卡片展示
 *
 * @author 刘鑫
 * @date 2024-12-19
 */
@Data
public class ProfileResp {
    /**
     * 姓名/昵称
     */
    private String name;

    /**
     * 职位/头衔
     */
    private String title;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 个人简介
     */
    private String bio;

    /**
     * 统计信息
     */
    private Stats stats;

    /**
     * 统计信息内部类
     */
    @Data
    public static class Stats {
        /**
         * 文章数量
         */
        private Long posts;

        /**
         * 评论数量
         */
        private Long comments;

        /**
         * 浏览量
         */
        private Long views;
    }
}
