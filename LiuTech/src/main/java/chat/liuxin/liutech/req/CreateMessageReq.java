package chat.liuxin.liutech.req;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建留言请求类
 */
@Data
public class CreateMessageReq {

    /**
     * 昵称
     */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 100, message = "昵称不能超过100字符")
    private String nickname;

    /**
     * 邮箱
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 留言内容
     */
    @NotBlank(message = "留言内容不能为空")
    @Size(max = 1000, message = "留言内容不能超过1000字符")
    private String content;
}
