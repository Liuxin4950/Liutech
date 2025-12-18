package chat.liuxin.liutech.controller.web;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.model.Posts;
import chat.liuxin.liutech.model.Categories;
import chat.liuxin.liutech.model.Tags;
import chat.liuxin.liutech.service.PostsService;
import chat.liuxin.liutech.service.CategoriesService;
import chat.liuxin.liutech.service.TagsService;
import lombok.extern.slf4j.Slf4j;

/**
 * Sitemap 控制器
 * 提供网站地图生成功能，支持搜索引擎爬虫索引
 *
 * @author LiuTech
 * @date 2025-01-18
 */
@Slf4j
@RestController
@RequestMapping("/sitemap")
public class SitemapController {

    @Autowired
    private PostsService postsService;

    @Autowired
    private CategoriesService categoriesService;

    @Autowired
    private TagsService tagsService;

    private static final String BASE_URL = "https://liutech.chat";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /**
     * 生成 sitemap.xml
     * 包含所有已发布文章的 URL、分类页面、标签页面等
     *
     * @return XML 格式的网站地图
     */
    @GetMapping("/sitemap.xml")
    public String generateSitemap() {
        log.info("开始生成 sitemap.xml");

        StringBuilder sitemap = new StringBuilder();
        sitemap.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sitemap.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // 添加首页
        sitemap.append("  <url>\n");
        sitemap.append("    <loc>").append(BASE_URL).append("/</loc>\n");
        sitemap.append("    <changefreq>daily</changefreq>\n");
        sitemap.append("    <priority>1.0</priority>\n");
        sitemap.append("  </url>\n");

        // 获取所有已发布的文章
        List<Posts> publishedPosts = postsService.getPublishedPosts();
        for (Posts post : publishedPosts) {
            sitemap.append("  <url>\n");
            sitemap.append("    <loc>").append(BASE_URL).append("/posts/").append(post.getId()).append("</loc>\n");
            sitemap.append("    <lastmod>").append(formatDate(post.getUpdatedAt())).append("</lastmod>\n");
            sitemap.append("    <changefreq>weekly</changefreq>\n");
            sitemap.append("    <priority>0.8</priority>\n");
            sitemap.append("  </url>\n");
        }

        // 获取所有分类
        List<Categories> categories = categoriesService.list();
        for (Categories category : categories) {
            sitemap.append("  <url>\n");
            sitemap.append("    <loc>").append(BASE_URL).append("/categories/").append(category.getId()).append("</loc>\n");
            sitemap.append("    <changefreq>weekly</changefreq>\n");
            sitemap.append("    <priority>0.6</priority>\n");
            sitemap.append("  </url>\n");
        }

        // 获取所有标签
        List<Tags> tags = tagsService.list();
        for (Tags tag : tags) {
            sitemap.append("  <url>\n");
            sitemap.append("    <loc>").append(BASE_URL).append("/tags/").append(tag.getId()).append("</loc>\n");
            sitemap.append("    <changefreq>weekly</changefreq>\n");
            sitemap.append("    <priority>0.5</priority>\n");
            sitemap.append("  </url>\n");
        }

        sitemap.append("</urlset>");

        log.info("Sitemap 生成完成，包含 {} 篇文章", publishedPosts.size());
        return sitemap.toString();
    }

    /**
     * 格式化日期为 sitemap 要求的格式
     *
     * @param dateTime 日期时间
     * @return 格式化后的日期字符串
     */
    private String formatDate(java.util.Date dateTime) {
        if (dateTime == null) {
            return LocalDateTime.now().format(DATE_FORMATTER);
        }
        return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
                .format(dateTime);
    }
}
