package chat.liuxin.liutech.model;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldFill;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 验证码表
 * 用于忘记密码、邮箱验证码登录等场景
 *
 * @author liuxin
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("verification_codes")
public class VerificationCode extends IdEntity {

    /** 邮箱地址 */
    private String email;

    /** 验证码 */
    private String code;

    /** 类型：FORGOT_PASSWORD / EMAIL_LOGIN / REGISTER */
    private String type;

    /** 是否已使用(0未使用,1已使用) */
    private Integer used;

    /** 错误尝试次数 */
    private Integer attemptCount;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;

    /** 过期时间 */
    private Date expiresAt;
}
