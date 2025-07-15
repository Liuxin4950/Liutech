package chat.liuxin.liutech.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user") // 如果类名和表名一致可省略
public class User {
    private Long id;
    private String userName; // 自动映射 user_name 字段
    private String email;
    private Integer age;
}
