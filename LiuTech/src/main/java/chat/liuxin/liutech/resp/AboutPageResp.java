package chat.liuxin.liutech.resp;

import java.util.List;

import lombok.Data;

/**
 * 关于页公开内容。
 */
@Data
public class AboutPageResp {

    private Author author;
    private String motto;
    private List<String> introParagraphs;
    private List<SocialLink> socialLinks;
    private List<SkillGroup> skillGroups;
    private List<Project> projects;
    private Honors honors;
    private String contactText;
    private String bannerDescription;
    private String metaDescription;

    @Data
    public static class Author {
        private String name;
        private String title;
        private String avatar;
        private String bio;
    }

    @Data
    public static class SocialLink {
        private String label;
        private String value;
        private String href;
    }

    @Data
    public static class SkillGroup {
        private String category;
        private List<String> skills;
    }

    @Data
    public static class Project {
        private String name;
        private String description;
        private List<String> technologies;
        private String link;
    }

    @Data
    public static class Honors {
        private String summary;
        private String imageUrl;
    }
}
