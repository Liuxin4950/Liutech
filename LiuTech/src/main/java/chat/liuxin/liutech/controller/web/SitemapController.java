package chat.liuxin.liutech.controller.web;

import lombok.RequiredArgsConstructor;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.model.Categories;
import chat.liuxin.liutech.model.Posts;
import chat.liuxin.liutech.model.Tags;
import chat.liuxin.liutech.service.CategoriesService;
import chat.liuxin.liutech.service.PostsService;
import chat.liuxin.liutech.service.TagsService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SitemapController {

    @Value("${server.base.url:https://liuxin.chat}")
    private String baseUrl;

    private final PostsService postsService;

    private final CategoriesService categoriesService;

    private final TagsService tagsService;
    private final chat.liuxin.liutech.service.PostSeriesService postSeriesService;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String generateSitemap() {

        StringBuilder sitemap = new StringBuilder();
        sitemap.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sitemap.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        appendUrl(sitemap, "/", null, "daily", "1.0");
        appendUrl(sitemap, "/about", null, "weekly", "0.7");
        appendUrl(sitemap, "/archive", null, "weekly", "0.7");
        appendUrl(sitemap, "/categories", null, "weekly", "0.6");
        appendUrl(sitemap, "/tags", null, "weekly", "0.6");
        appendUrl(sitemap, "/series", null, "weekly", "0.6");
        for (var series : postSeriesService.getAllSeriesWithPostCount()) {
            if (series.getPostCount() != null && series.getPostCount() > 0) {
                appendUrl(sitemap, "/series-detail/" + series.getId(), series.getUpdatedAt(), "weekly", "0.6");
            }
        }

        List<Posts> publishedPosts = postsService.getPublishedPosts();
        for (Posts post : publishedPosts) {
            appendUrl(sitemap, "/post/" + post.getId(), post.getUpdatedAt(), "weekly", "0.8");
        }

        List<Categories> categories = categoriesService.list();
        for (Categories category : categories) {
            appendUrl(sitemap, "/category-detail/" + category.getId(), category.getUpdatedAt(), "weekly", "0.6");
        }

        List<Tags> tags = tagsService.list();
        for (Tags tag : tags) {
            appendUrl(sitemap, "/tags/" + tag.getId(), tag.getUpdatedAt(), "weekly", "0.5");
        }

        sitemap.append("</urlset>");

        return sitemap.toString();
    }

    private void appendUrl(StringBuilder sitemap, String path, Date lastModified, String changeFreq, String priority) {
        sitemap.append("  <url>\n");
        String url = baseUrl.replaceAll("/+$", "") + path;
        sitemap.append("    <loc>").append(org.springframework.web.util.HtmlUtils.htmlEscape(url)).append("</loc>\n");
        if (lastModified != null) {
            sitemap.append("    <lastmod>").append(formatDate(lastModified)).append("</lastmod>\n");
        }
        sitemap.append("    <changefreq>").append(changeFreq).append("</changefreq>\n");
        sitemap.append("    <priority>").append(priority).append("</priority>\n");
        sitemap.append("  </url>\n");
    }

    private String formatDate(Date dateTime) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        formatter.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return formatter.format(dateTime == null ? new Date() : dateTime);
    }
}
