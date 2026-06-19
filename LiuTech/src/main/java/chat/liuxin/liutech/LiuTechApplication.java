package chat.liuxin.liutech;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication
@MapperScan("chat.liuxin.liutech.mapper")
@EnableScheduling
public class LiuTechApplication {
    public static void main(String[] args) {
        var context = SpringApplication.run(LiuTechApplication.class, args);
        Environment env = context.getBean(Environment.class);
        String port = env.getProperty("server.port", "8080");
        log.info("Server started at http://127.0.0.1:{}", port);
    }

}
