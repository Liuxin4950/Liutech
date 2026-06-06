package chat.liuxin.liutech.mapper;

import chat.liuxin.liutech.model.VerificationCode;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;

@Mapper
public interface VerificationCodeMapper extends BaseMapper<VerificationCode> {

    /**
     * 查询指定邮箱+类型的有效未使用验证码（最新一条）
     */
    @Select("SELECT * FROM verification_codes WHERE email = #{email} AND type = #{type} AND used = 0 AND expires_at > #{now} ORDER BY id DESC LIMIT 1")
    VerificationCode findValidCode(@Param("email") String email, @Param("type") String type, @Param("now") Date now);

    /**
     * 标记验证码为已使用
     */
    @Update("UPDATE verification_codes SET used = 1 WHERE id = #{id}")
    int markUsed(@Param("id") Long id);

    /**
     * 增加错误尝试次数
     */
    @Update("UPDATE verification_codes SET attempt_count = attempt_count + 1 WHERE id = #{id}")
    int incrementAttemptCount(@Param("id") Long id);

    /**
     * 清理过期验证码
     */
    @Update("DELETE FROM verification_codes WHERE expires_at < #{now}")
    int cleanExpired(@Param("now") Date now);
}
