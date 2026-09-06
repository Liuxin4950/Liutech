package chat.liuxin.liutech.service;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.req.AboutPageReq;
import chat.liuxin.liutech.resp.AboutPageResp;
import chat.liuxin.liutech.resp.ProfileResp;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 关于页内容聚合与管理。
 * 作者基础资料复用 author.*，页面结构化内容存入 about.content。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AboutPageService {

    static final String CONTENT_KEY = "about.content";
    private static final String CONTENT_DESCRIPTION = "关于页结构化内容（JSON）";

    private final SystemSettingService systemSettingService;
    private final UserProfileService userProfileService;
    private final ObjectMapper objectMapper;

    @Cacheable(value = "aboutPage", key = "'public'")
    @Transactional(readOnly = true)
    public AboutPageResp getAboutPage() {
        return buildResponse(readContent(), authorFrom(userProfileService.getDefaultProfile()));
    }

    @CacheEvict(value = "aboutPage", allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public AboutPageResp updateAboutPage(AboutPageReq req) {
        validateLinks(req);

        AboutPageResp.Author author = mapAuthor(req.getAuthor());
        StoredContent content = mapContent(req);

        systemSettingService.upsert("author.name", author.getName(), "作者昵称");
        systemSettingService.upsert("author.title", author.getTitle(), "作者头衔/职位");
        systemSettingService.upsert("author.avatar", author.getAvatar(), "作者头像 URL");
        systemSettingService.upsert("author.bio", author.getBio(), "作者个人简介");
        try {
            systemSettingService.upsert(CONTENT_KEY, objectMapper.writeValueAsString(content), CONTENT_DESCRIPTION);
        } catch (Exception e) {
            log.error("序列化关于页配置失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "关于页配置保存失败");
        }

        log.debug("关于页配置已更新：skillGroups={}, projects={}",
                content.getSkillGroups().size(), content.getProjects().size());
        return buildResponse(content, author);
    }

    private StoredContent readContent() {
        String json = systemSettingService.getValue(CONTENT_KEY);
        if (json == null || json.isBlank()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "关于页内容尚未配置");
        }
        StoredContent content;
        try {
            content = objectMapper.readValue(json, StoredContent.class);
        } catch (Exception e) {
            log.error("关于页配置解析失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "关于页内容配置无效");
        }
        validateStoredContent(content);
        return content;
    }

    private void validateStoredContent(StoredContent content) {
        boolean invalid = content == null
                || isBlank(content.getMotto())
                || content.getIntroParagraphs() == null
                || content.getIntroParagraphs().isEmpty()
                || content.getIntroParagraphs().stream().anyMatch(this::isBlank)
                || content.getSocialLinks() == null
                || content.getSocialLinks().stream().anyMatch(link -> link == null
                        || isBlank(link.getLabel()) || isBlank(link.getValue()) || isBlank(link.getHref()))
                || content.getSkillGroups() == null
                || content.getSkillGroups().isEmpty()
                || content.getSkillGroups().stream().anyMatch(group -> group == null
                        || isBlank(group.getCategory()) || group.getSkills() == null || group.getSkills().isEmpty()
                        || group.getSkills().stream().anyMatch(this::isBlank))
                || content.getProjects() == null
                || content.getProjects().isEmpty()
                || content.getProjects().stream().anyMatch(project -> project == null
                        || isBlank(project.getName()) || isBlank(project.getDescription())
                        || project.getTechnologies() == null)
                || content.getHonors() == null
                || isBlank(content.getHonors().getSummary())
                || isBlank(content.getContactText())
                || isBlank(content.getBannerDescription())
                || isBlank(content.getMetaDescription());
        if (invalid) {
            log.error("关于页配置字段缺失或结构不完整");
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "关于页内容配置无效");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private AboutPageResp buildResponse(StoredContent content, AboutPageResp.Author author) {
        AboutPageResp resp = new AboutPageResp();
        resp.setAuthor(author);
        resp.setMotto(content.getMotto());
        resp.setIntroParagraphs(content.getIntroParagraphs());
        resp.setSocialLinks(content.getSocialLinks());
        resp.setSkillGroups(content.getSkillGroups());
        resp.setProjects(content.getProjects());
        resp.setHonors(content.getHonors());
        resp.setContactText(content.getContactText());
        resp.setBannerDescription(content.getBannerDescription());
        resp.setMetaDescription(content.getMetaDescription());
        return resp;
    }

    private AboutPageResp.Author authorFrom(ProfileResp profile) {
        AboutPageResp.Author author = new AboutPageResp.Author();
        author.setName(profile.getName());
        author.setTitle(profile.getTitle());
        author.setAvatar(profile.getAvatar());
        author.setBio(profile.getBio());
        return author;
    }

    private AboutPageResp.Author mapAuthor(AboutPageReq.Author source) {
        AboutPageResp.Author target = new AboutPageResp.Author();
        target.setName(source.getName().trim());
        target.setTitle(source.getTitle().trim());
        target.setAvatar(source.getAvatar().trim());
        target.setBio(source.getBio().trim());
        return target;
    }

    private StoredContent mapContent(AboutPageReq req) {
        StoredContent content = new StoredContent();
        content.setMotto(req.getMotto().trim());
        content.setIntroParagraphs(req.getIntroParagraphs().stream().map(String::trim).toList());
        content.setSocialLinks(req.getSocialLinks() == null ? List.of() : req.getSocialLinks().stream().map(this::mapSocialLink).toList());
        content.setSkillGroups(req.getSkillGroups().stream().map(this::mapSkillGroup).toList());
        content.setProjects(req.getProjects().stream().map(this::mapProject).toList());
        content.setHonors(mapHonors(req.getHonors()));
        content.setContactText(req.getContactText().trim());
        content.setBannerDescription(req.getBannerDescription().trim());
        content.setMetaDescription(req.getMetaDescription().trim());
        return content;
    }

    private AboutPageResp.SocialLink mapSocialLink(AboutPageReq.SocialLink source) {
        AboutPageResp.SocialLink target = new AboutPageResp.SocialLink();
        target.setLabel(source.getLabel().trim());
        target.setValue(source.getValue().trim());
        target.setHref(source.getHref().trim());
        return target;
    }

    private AboutPageResp.SkillGroup mapSkillGroup(AboutPageReq.SkillGroup source) {
        AboutPageResp.SkillGroup target = new AboutPageResp.SkillGroup();
        target.setCategory(source.getCategory().trim());
        target.setSkills(source.getSkills().stream().map(String::trim).toList());
        return target;
    }

    private AboutPageResp.Project mapProject(AboutPageReq.Project source) {
        AboutPageResp.Project target = new AboutPageResp.Project();
        target.setName(source.getName().trim());
        target.setDescription(source.getDescription().trim());
        target.setTechnologies(source.getTechnologies() == null
                ? List.of()
                : source.getTechnologies().stream().map(String::trim).toList());
        target.setLink(normalizeNullable(source.getLink()));
        return target;
    }

    private AboutPageResp.Honors mapHonors(AboutPageReq.Honors source) {
        AboutPageResp.Honors target = new AboutPageResp.Honors();
        target.setSummary(source.getSummary().trim());
        target.setImageUrl(normalizeNullable(source.getImageUrl()));
        return target;
    }

    private void validateLinks(AboutPageReq req) {
        if (req.getSocialLinks() != null) {
            for (AboutPageReq.SocialLink link : req.getSocialLinks()) {
                validateHref(link.getHref(), false, true);
            }
        }
        for (AboutPageReq.Project project : req.getProjects()) {
            validateHref(project.getLink(), true, false);
        }
        validateHref(req.getAuthor().getAvatar(), false, false);
        validateHref(req.getHonors().getImageUrl(), true, false);
    }

    private void validateHref(String href, boolean allowBlank, boolean allowMailto) {
        String value = normalizeNullable(href);
        if (value == null) {
            if (allowBlank) return;
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "链接地址不能为空");
        }
        if (value.startsWith("/") && !value.startsWith("//")) return;
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            boolean isHttp = ("http".equals(scheme) || "https".equals(scheme)) && uri.getHost() != null;
            boolean isMailto = allowMailto && "mailto".equals(scheme) && !uri.getSchemeSpecificPart().isBlank();
            if (isHttp || isMailto) return;
        } catch (IllegalArgumentException ignore) {
            // 统一转换为用户可读的参数错误。
        }
        String allowedTypes = allowMailto ? "站内路径、HTTP(S) 或 mailto" : "站内路径或 HTTP(S)";
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "链接地址仅支持" + allowedTypes);
    }

    private String normalizeNullable(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    @Data
    public static class StoredContent {
        private String motto;
        private List<String> introParagraphs;
        private List<AboutPageResp.SocialLink> socialLinks;
        private List<AboutPageResp.SkillGroup> skillGroups;
        private List<AboutPageResp.Project> projects;
        private AboutPageResp.Honors honors;
        private String contactText;
        private String bannerDescription;
        private String metaDescription;
    }
}
