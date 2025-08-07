package chat.liuxin.liutech.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置类
 * 
 * @author 刘鑫
 * @date 2025-08-07
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Autowired
    private FileUploadConfig fileUploadConfig;
    
    /**
     * 配置静态资源映射
     * 让上传的文件可以通过URL访问
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置文件访问路径映射
        registry.addResourceHandler(fileUploadConfig.getUrlPrefix() + "/**")
                .addResourceLocations("file:" + fileUploadConfig.getBasePath() + "/");
        
        // 配置图片访问路径
        registry.addResourceHandler("/uploads/images/**")
                .addResourceLocations("file:" + fileUploadConfig.getBasePath() + "/" + fileUploadConfig.getImagePath() + "/");
        
        // 配置文档访问路径
        registry.addResourceHandler("/uploads/documents/**")
                .addResourceLocations("file:" + fileUploadConfig.getBasePath() + "/" + fileUploadConfig.getDocumentPath() + "/");
        
        // 配置资源访问路径
        registry.addResourceHandler("/uploads/resources/**")
                .addResourceLocations("file:" + fileUploadConfig.getBasePath() + "/" + fileUploadConfig.getResourcePath() + "/");
    }
}