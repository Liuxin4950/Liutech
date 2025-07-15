package chat.liuxin.liutech;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("chat.liuxin.liutech.mapper") // 指向你的 mapper 包
public class LiuTechApplication {
    public static void main(String[] args) {
        SpringApplication.run(LiuTechApplication.class, args);
        System.out.println("http://127.0.0.1:8080/blog/");
    }

}
