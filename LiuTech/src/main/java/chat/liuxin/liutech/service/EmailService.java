package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

/**
 * 邮件发送服务
 * 封装 Spring Boot Mail 发送逻辑
 *
 * @author liuxin
 */
@Slf4j
@Service
@RequiredArgsConstructor
 {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from}")
    private String fromAddress;

    @Value("${spring.mail.display-name:LiuTech博客}")
    private String displayName;

    /**
     * 发送验证码邮件
     *
     * @param to      收件人邮箱
     * @param code    验证码
     * @param purpose 用途（如"忘记密码"、"邮箱登录"）
     */
    public void sendVerificationCode(String to, String code, String purpose) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress, displayName);
            helper.setTo(to);
            helper.setSubject("【" + displayName + "】" + purpose + "验证码");

            String htmlContent = buildVerificationCodeEmail(code, purpose);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("验证码邮件已发送，收件人: {}, 用途: {}", to, purpose);
        } catch (Exception e) {
            log.error("发送验证码邮件失败，收件人: {}, 错误: {}", to, e.getMessage(), e);
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED, "邮件发送失败，请稍后重试");
        }
    }

    /**
     * 构建验证码邮件 HTML 内容
     * 简约大气风格，与博客设计系统一致
     * 使用 table 布局兼容各邮件客户端
     */
    private String buildVerificationCodeEmail(String code, String purpose) {
        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0"></head>
                <body style="margin:0;padding:0;background-color:#F8F9FA;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#F8F9FA;padding:48px 16px;">
                    <tr><td align="center">
                      <table role="presentation" width="480" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border-radius:8px;border:1px solid #E8EAED;">

                        <!-- 顶部：品牌名 + 简洁分隔 -->
                        <tr>
                          <td style="padding:36px 40px 0;">
                            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                              <tr>
                                <td style="text-align:left;">
                                  <span style="font-size:20px;font-weight:700;color:#202124;letter-spacing:-0.5px;">LiuTech</span>
                                </td>
                                <td style="text-align:right;">
                                  <span style="font-size:12px;color:#9AA0A6;">%s</span>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:16px 40px 0;">
                            <div style="border-top:1px solid #E8EAED;"></div>
                          </td>
                        </tr>

                        <!-- 正文区 -->
                        <tr>
                          <td style="padding:32px 40px 0;">
                            <p style="color:#5F6368;font-size:14px;line-height:1.8;margin:0 0 8px;">您好，</p>
                            <p style="color:#5F6368;font-size:14px;line-height:1.8;margin:0;">您正在为账号进行 <strong style="color:#202124;">%s</strong> 操作，验证码如下：</p>
                          </td>
                        </tr>

                        <!-- 验证码 -->
                        <tr>
                          <td style="padding:28px 40px 0;">
                            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                              <tr>
                                <td style="background-color:#F7F9FC;border:1px solid #E8EAED;border-radius:6px;padding:20px 24px;text-align:center;">
                                  <span style="display:inline-block;font-size:28px;font-weight:600;color:#2d90cd;letter-spacing:8px;font-family:'SF Mono',SFMono-Regular,Consolas,'Courier New',monospace;">%s</span>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>

                        <!-- 提示信息 -->
                        <tr>
                          <td style="padding:24px 40px 0;">
                            <p style="color:#9AA0A6;font-size:13px;line-height:1.8;margin:0;">验证码 5 分钟内有效，请勿泄露给他人。如非本人操作，请忽略此邮件。</p>
                          </td>
                        </tr>

                        <!-- 底部 -->
                        <tr>
                          <td style="padding:36px 40px 0;">
                            <div style="border-top:1px solid #F1F3F4;"></div>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:16px 40px 32px;">
                            <p style="color:#9AA0A6;font-size:12px;margin:0;">此邮件由 LiuTech 系统自动发送，请勿直接回复</p>
                          </td>
                        </tr>

                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(displayName, purpose, code);
    }
}
