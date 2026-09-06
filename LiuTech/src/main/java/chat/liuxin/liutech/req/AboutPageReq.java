package chat.liuxin.liutech.req;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端更新关于页内容的请求。
 */
@Data
public class AboutPageReq {

    @Valid
    @NotNull(message = "作者资料不能为空")
    private Author author;

    @NotBlank(message = "座右铭不能为空")
    @Size(max = 120, message = "座右铭不能超过120个字符")
    private String motto;

    @NotEmpty(message = "个人介绍至少需要一段")
    @Size(max = 6, message = "个人介绍不能超过6段")
    private List<@NotBlank(message = "介绍段落不能为空") @Size(max = 800, message = "单段介绍不能超过800个字符") String> introParagraphs;

    @Valid
    @Size(max = 8, message = "社交链接不能超过8个")
    private List<SocialLink> socialLinks;

    @Valid
    @NotEmpty(message = "技术栈至少需要一个分组")
    @Size(max = 8, message = "技术栈分组不能超过8个")
    private List<SkillGroup> skillGroups;

    @Valid
    @NotEmpty(message = "项目经历至少需要一项")
    @Size(max = 20, message = "项目经历不能超过20项")
    private List<Project> projects;

    @Valid
    @NotNull(message = "荣誉信息不能为空")
    private Honors honors;

    @NotBlank(message = "联系区说明不能为空")
    @Size(max = 300, message = "联系区说明不能超过300个字符")
    private String contactText;

    @NotBlank(message = "Banner 描述不能为空")
    @Size(max = 200, message = "Banner 描述不能超过200个字符")
    private String bannerDescription;

    @NotBlank(message = "SEO 描述不能为空")
    @Size(max = 300, message = "SEO 描述不能超过300个字符")
    private String metaDescription;

    @Data
    public static class Author {
        @NotBlank(message = "作者姓名不能为空")
        @Size(max = 50, message = "作者姓名不能超过50个字符")
        private String name;

        @NotBlank(message = "作者头衔不能为空")
        @Size(max = 80, message = "作者头衔不能超过80个字符")
        private String title;

        @NotBlank(message = "作者头像不能为空")
        @Size(max = 500, message = "作者头像地址过长")
        private String avatar;

        @NotBlank(message = "作者简介不能为空")
        @Size(max = 500, message = "作者简介不能超过500个字符")
        private String bio;
    }

    @Data
    public static class SocialLink {
        @NotBlank(message = "社交链接标签不能为空")
        @Size(max = 40, message = "社交链接标签过长")
        private String label;

        @NotBlank(message = "社交链接展示值不能为空")
        @Size(max = 100, message = "社交链接展示值过长")
        private String value;

        @NotBlank(message = "社交链接地址不能为空")
        @Size(max = 500, message = "社交链接地址过长")
        private String href;
    }

    @Data
    public static class SkillGroup {
        @NotBlank(message = "技术栈分组名不能为空")
        @Size(max = 40, message = "技术栈分组名过长")
        private String category;

        @NotEmpty(message = "技术栈分组至少需要一项技能")
        @Size(max = 20, message = "单个技术栈分组不能超过20项")
        private List<@NotBlank(message = "技能名称不能为空") @Size(max = 50, message = "技能名称过长") String> skills;
    }

    @Data
    public static class Project {
        @NotBlank(message = "项目名称不能为空")
        @Size(max = 100, message = "项目名称不能超过100个字符")
        private String name;

        @NotBlank(message = "项目描述不能为空")
        @Size(max = 2000, message = "项目描述不能超过2000个字符")
        private String description;

        @Size(max = 12, message = "单个项目技术关键词不能超过12个")
        private List<@NotBlank(message = "项目技术关键词不能为空") @Size(max = 30, message = "项目技术关键词过长") String> technologies;

        @Size(max = 500, message = "项目链接地址过长")
        private String link;
    }

    @Data
    public static class Honors {
        @NotBlank(message = "荣誉摘要不能为空")
        @Size(max = 500, message = "荣誉摘要不能超过500个字符")
        private String summary;

        @Size(max = 500, message = "荣誉图片地址过长")
        private String imageUrl;
    }
}
