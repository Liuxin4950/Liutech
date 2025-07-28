package chat.liuxin.liutech.req;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户资料请求参数
 * 用于用户更新自己的个人信息
 * 
 * @author liuxin
 */
@Data
public class UpdateProfileReq {
    
    /**
     * 邮箱地址
     */
    @Email(message = "邮箱格式不正确")
    private String email;
    
    /**
     * 头像URL
     */
    private String avatarUrl;
    
    /**
     * 昵称
     */
    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;
    
    /**
     * 个人简介
     */
    @Size(max = 500, message = "个人简介长度不能超过500个字符")
    private String bio;
}