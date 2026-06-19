package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.VerificationCodeMapper;
import chat.liuxin.liutech.model.VerificationCode;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Calendar;
import java.util.Date;

/**
 * 验证码管理服务
 * 负责验证码的生成、存储、校验和清理
 *
 * @author liuxin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    /** 验证码有效期（分钟） */
    private static final int CODE_EXPIRE_MINUTES = 5;
    /** 验证码长度 */
    private static final int CODE_LENGTH = 6;
    /** 同一邮箱同一类型发送间隔（秒） */
    private static final int SEND_INTERVAL_SECONDS = 60;
    /** 最大错误尝试次数，超过则作废验证码 */
    private static final int MAX_ATTEMPT_COUNT = 5;

    private final VerificationCodeMapper verificationCodeMapper;

    private final EmailService emailService;

    @Autowired
    @Lazy
    private VerificationCodeService self;

    /**
     * 发送验证码
     *
     * @param email   邮箱
     * @param type    类型（REGISTER / FORGOT_PASSWORD / EMAIL_LOGIN）
     * @param purpose 用途描述（用于邮件标题）
     */
    public void sendCode(String email, String type, String purpose) {
        // 防刷：检查最近60秒内是否已发送
        checkSendInterval(email, type);

        // 生成6位数字验证码
        String code = generateCode();

        // 存入数据库
        VerificationCode vc = new VerificationCode();
        vc.setEmail(email);
        vc.setCode(code);
        vc.setType(type);
        vc.setUsed(0);
        vc.setAttemptCount(0);
        vc.setCreatedAt(new Date()); // 显式设置，兜底 MetaObjectHandler
        vc.setExpiresAt(buildExpiryTime());
        verificationCodeMapper.insert(vc);

        // 发送邮件
        emailService.sendVerificationCode(email, code, purpose);

        log.info("验证码已发送，邮箱: {}, 类型: {}", email, type);
    }

    /**
     * 校验验证码（不消耗，调用方决定何时 markUsed）
     *
     * @param email 邮箱
     * @param type  类型
     * @param code  用户输入的验证码
     */
    public VerificationCode verifyCode(String email, String type, String code) {
        VerificationCode vc = verificationCodeMapper.findValidCode(email, type, new Date());
        if (vc == null) {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID, "验证码无效或已过期，请重新获取");
        }
        // 爆破防护：超过最大尝试次数直接作废
        if (vc.getAttemptCount() >= MAX_ATTEMPT_COUNT) {
            verificationCodeMapper.markUsed(vc.getId());
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID, "验证码尝试次数过多，请重新获取");
        }
        if (!vc.getCode().equals(code)) {
            self.incrementAttemptCount(vc.getId());
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID, "验证码错误");
        }
        // 校验通过，不在此处 markUsed，由调用方在业务完成后标记
        log.info("验证码校验通过，邮箱: {}, 类型: {}", email, type);
        return vc;
    }

    /**
     * 标记验证码已使用（业务完成后调用）
     */
    public void markUsed(Long id) {
        verificationCodeMapper.markUsed(id);
    }

    /**
     * 增加错误尝试次数（独立事务，不受外层回滚影响）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementAttemptCount(Long id) {
        verificationCodeMapper.incrementAttemptCount(id);
    }

    /**
     * 检查发送间隔，防止频繁发送
     */
    private void checkSendInterval(String email, String type) {
        VerificationCode recent = verificationCodeMapper.findValidCode(email, type, new Date());
        if (recent != null) {
            long elapsed = (System.currentTimeMillis() - recent.getCreatedAt().getTime()) / 1000;
            if (elapsed < SEND_INTERVAL_SECONDS) {
                long wait = SEND_INTERVAL_SECONDS - elapsed;
                throw new BusinessException(ErrorCode.PARAMS_ERROR,
                        "发送过于频繁，请" + wait + "秒后再试");
            }
        }
    }

    /**
     * 生成6位数字验证码
     */
    private String generateCode() {
        SecureRandom secureRandom = new SecureRandom();
        int code = secureRandom.nextInt(900000) + 100000; // 100000-999999
        return String.valueOf(code);
    }

    /**
     * 构建过期时间
     */
    private Date buildExpiryTime() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, CODE_EXPIRE_MINUTES);
        return cal.getTime();
    }
}





