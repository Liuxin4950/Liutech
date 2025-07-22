package chat.liuxin.liutech.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求类
 * 用于用户修改密码时的参数验证
 * 
 * @author liuxin
 */
@Data
public class ChangePasswordReq {
    
    /**
     * 原密码
     * 用于验证用户身份，确保是本人操作
     */
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;
    
    /**
     * 新密码
     * 长度要求：6-20位
     */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "新密码长度必须在6-20位之间")
    private String newPassword;
    
    /**
     * 确认新密码
     * 必须与新密码一致
     */
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
    
    /**
     * 验证新密码和确认密码是否一致
     * 
     * @return 是否一致
     */
    public boolean isPasswordMatch() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }
}