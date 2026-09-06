package chat.liuxin.liutech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.req.AboutPageReq;
import chat.liuxin.liutech.resp.AboutPageResp;
import chat.liuxin.liutech.resp.ProfileResp;
import tools.jackson.databind.ObjectMapper;

class AboutPageServiceTest {

    private SystemSettingService systemSettingService;
    private UserProfileService userProfileService;
    private AboutPageService aboutPageService;

    @BeforeEach
    void setUp() {
        systemSettingService = mock(SystemSettingService.class);
        userProfileService = mock(UserProfileService.class);
        aboutPageService = new AboutPageService(systemSettingService, userProfileService, new ObjectMapper());
    }

    @Test
    void shouldRejectMissingContentInsteadOfReturningFakeDefaults() {
        when(systemSettingService.getValue(AboutPageService.CONTENT_KEY)).thenReturn("");
        when(userProfileService.getDefaultProfile()).thenReturn(profile());

        assertThrows(BusinessException.class, () -> aboutPageService.getAboutPage());
    }

    @Test
    void shouldBuildPublicResponseFromStoredContent() {
        when(systemSettingService.getValue(AboutPageService.CONTENT_KEY)).thenReturn(validContentJson());
        when(userProfileService.getDefaultProfile()).thenReturn(profile());

        AboutPageResp result = aboutPageService.getAboutPage();

        assertEquals("刘鑫", result.getAuthor().getName());
        assertEquals(List.of("Vue 3"), result.getSkillGroups().get(0).getSkills());
        assertEquals(List.of("Vue 3"), result.getProjects().get(0).getTechnologies());
    }

    @Test
    void shouldRejectLegacyContentInsteadOfHidingItWithFallbackData() {
        when(systemSettingService.getValue(AboutPageService.CONTENT_KEY)).thenReturn("""
                {"motto":"旧结构","skillGroups":[{"category":"前端","icon":"layout","skills":[{"name":"Vue 3","icon":"vue"}]}]}
                """);
        when(userProfileService.getDefaultProfile()).thenReturn(profile());

        assertThrows(BusinessException.class, () -> aboutPageService.getAboutPage());
    }

    @Test
    void shouldPersistAuthorAndStructuredContentTogether() {
        AboutPageReq req = validRequest();

        AboutPageResp result = aboutPageService.updateAboutPage(req);

        assertEquals("新名字", result.getAuthor().getName());
        assertEquals(1, result.getProjects().size());
        assertEquals(List.of("Vue"), result.getProjects().get(0).getTechnologies());
        assertEquals(List.of("Vue 3"), result.getSkillGroups().get(0).getSkills());
        verify(systemSettingService).upsert("author.name", "新名字", "作者昵称");
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(systemSettingService).upsert(eq(AboutPageService.CONTENT_KEY), jsonCaptor.capture(), anyString());
        assertTrue(jsonCaptor.getValue().contains("\"technologies\""));
        assertFalse(jsonCaptor.getValue().contains("\"icon\""));
        assertFalse(jsonCaptor.getValue().contains("\"tags\""));
        assertFalse(jsonCaptor.getValue().contains("\"type\""));
    }

    @Test
    void shouldRejectUnsafeLink() {
        AboutPageReq req = validRequest();
        req.getProjects().get(0).setLink("javascript:alert(1)");

        assertThrows(BusinessException.class, () -> aboutPageService.updateAboutPage(req));
    }

    private ProfileResp profile() {
        ProfileResp profile = new ProfileResp();
        profile.setName("刘鑫");
        profile.setTitle("全栈工程师");
        profile.setAvatar("/洛天依.png");
        profile.setBio("简介");
        return profile;
    }

    private String validContentJson() {
        return """
                {
                  "motto": "真实内容",
                  "introParagraphs": ["介绍"],
                  "socialLinks": [{"label":"GitHub","value":"Liuxin4950","href":"https://github.com/Liuxin4950"}],
                  "skillGroups": [{"category":"前端开发","skills":["Vue 3"]}],
                  "projects": [{"name":"项目","description":"描述","technologies":["Vue 3"],"link":"/"}],
                  "honors": {"summary":"荣誉","imageUrl":null},
                  "contactText": "欢迎联系",
                  "bannerDescription": "Banner",
                  "metaDescription": "SEO"
                }
                """;
    }

    private AboutPageReq validRequest() {
        AboutPageReq req = new AboutPageReq();

        AboutPageReq.Author author = new AboutPageReq.Author();
        author.setName("新名字");
        author.setTitle("新头衔");
        author.setAvatar("/洛天依.png");
        author.setBio("新简介");
        req.setAuthor(author);

        req.setMotto("新座右铭");
        req.setIntroParagraphs(List.of("第一段"));

        AboutPageReq.SocialLink socialLink = new AboutPageReq.SocialLink();
        socialLink.setLabel("GitHub");
        socialLink.setValue("example");
        socialLink.setHref("https://example.com");
        req.setSocialLinks(List.of(socialLink));

        AboutPageReq.SkillGroup group = new AboutPageReq.SkillGroup();
        group.setCategory("前端");
        group.setSkills(List.of("Vue 3"));
        req.setSkillGroups(List.of(group));

        AboutPageReq.Project project = new AboutPageReq.Project();
        project.setName("项目");
        project.setDescription("项目描述");
        project.setTechnologies(List.of("Vue"));
        project.setLink("/");
        req.setProjects(List.of(project));

        AboutPageReq.Honors honors = new AboutPageReq.Honors();
        honors.setSummary("荣誉摘要");
        honors.setImageUrl(null);
        req.setHonors(honors);

        req.setContactText("欢迎联系");
        req.setBannerDescription("Banner");
        req.setMetaDescription("SEO");
        return req;
    }
}
