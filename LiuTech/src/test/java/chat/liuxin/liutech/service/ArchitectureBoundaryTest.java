package chat.liuxin.liutech.service;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 防止后续二次开发重新绕过分层与统一分页入口。 */
class ArchitectureBoundaryTest {
    @Test void controllersCannotDependOnMappers() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (var candidate : scanner.findCandidateComponents("chat.liuxin.liutech.controller")) {
            Class<?> controller = Class.forName(candidate.getBeanClassName());
            for (var field : controller.getDeclaredFields()) {
                assertFalse(BaseMapper.class.isAssignableFrom(field.getType())
                        || field.getType().getPackageName().equals("chat.liuxin.liutech.mapper"),
                        controller.getName() + " 不得直接依赖 " + field.getType().getName());
            }
        }
    }

    @Test void mapperOffsetsMustUseLongAndServicesCannotComposeSqlPagination() throws Exception {
        Path root = Path.of("src/main/java/chat/liuxin/liutech");
        try (var paths = Files.list(root.resolve("mapper"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                Class<?> mapper = Class.forName("chat.liuxin.liutech.mapper." + path.getFileName().toString().replace(".java", ""));
                for (var method : mapper.getDeclaredMethods()) {
                    for (var param : method.getParameters()) {
                        Param name = param.getAnnotation(Param.class);
                        if (name != null && name.value().equals("offset")) {
                            assertEquals(long.class, param.getType(), method.toString());
                        }
                    }
                }
            }
        }
        try (var paths = Files.list(root.resolve("service"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                assertFalse(source.matches("(?s).*\\.last\\(.*"), path + " 不得拼接 SQL 尾句");
                assertFalse(source.contains("ResponseEntity"), path + " 不得构造入站 HTTP 响应");
            }
        }
    }
}
