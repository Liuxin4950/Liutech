package chat.liuxin.liutech;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

@Slf4j
@SpringBootApplication
@MapperScan("chat.liuxin.liutech.mapper") // 指向你的 mapper 包
public class LiuTechApplication {
    public static void main(String[] args) {
        var context = SpringApplication.run(LiuTechApplication.class, args);
        Environment env = context.getBean(Environment.class);
        String port = env.getProperty("server.port", "8080");
        log.info("Server started at http://127.0.0.1:{}", port);
    }

}
