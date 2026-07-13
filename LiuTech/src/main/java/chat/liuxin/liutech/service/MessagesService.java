package chat.liuxin.liutech.service;

import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.MessagesMapper;
import chat.liuxin.liutech.model.Messages;
import chat.liuxin.liutech.req.CreateMessageReq;
import chat.liuxin.liutech.resp.MessageResp;
import lombok.extern.slf4j.Slf4j;

/**
 * 留言服务类
 */
@Slf4j
@Service
public class MessagesService extends ServiceImpl<MessagesMapper, Messages> {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    /**
     * 获取已审核的公开留言列表
     */
    @Transactional(readOnly = true)
    public List<MessageResp> getApprovedMessages() {
        QueryWrapper<Messages> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1)  // 只查询已审核
               .orderByDesc("created_at");

        List<Messages> messages = this.list(wrapper);
        return messages.stream()
                .map(this::convertToResp)
                .collect(Collectors.toList());
    }

    /**
     * 创建留言（无需登录，默认待审核状态）
     */
    @Transactional(rollbackFor = Exception.class)
    public MessageResp createMessage(CreateMessageReq req) {
        log.debug("收到留言请求: nickname={}, email={}", req.getNickname(), req.getEmail());

        // 验证邮箱格式
        if (!EMAIL_PATTERN.matcher(req.getEmail()).matches()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱格式不正确");
        }

        // 检查邮箱是否在短时间内频繁留言
        checkRecentMessageLimit(req.getEmail());

        // 创建留言
        Messages message = new Messages();
        message.setNickname(req.getNickname());
        message.setEmail(req.getEmail());
        message.setContent(req.getContent());
        message.setStatus(0);  // 默认待审核
        message.setCreatedAt(new Date());
        message.setUpdatedAt(new Date());

        boolean saved = this.save(message);
        if (!saved) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "留言提交失败");
        }

        log.debug("留言提交成功: id={}", message.getId());
        return convertToResp(message);
    }

    /**
     * 检查短时间内是否已留言（防刷）
     */
    private void checkRecentMessageLimit(String email) {
        QueryWrapper<Messages> wrapper = new QueryWrapper<>();
        wrapper.eq("email", email)
               .gt("created_at", new Date(System.currentTimeMillis() - 5 * 60 * 1000)); // 5分钟内

        Long count = this.count(wrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "您的留言太频繁了，请5分钟后再试");
        }
    }

    /**
     * 转换为响应对象（邮箱脱敏）
     */
    private MessageResp convertToResp(Messages message) {
        MessageResp resp = new MessageResp();
        resp.setId(message.getId());
        resp.setNickname(message.getNickname());
        resp.setEmail(maskEmail(message.getEmail()));
        resp.setContent(message.getContent());
        resp.setStatus(message.getStatus());
        resp.setReply(message.getReply());
        resp.setRepliedAt(message.getRepliedAt());
        resp.setCreatedAt(message.getCreatedAt());
        return resp;
    }

    /**
     * 邮箱脱敏：只显示前2位和@域名
     */
    private String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return "";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) {
            return "**" + email.substring(atIndex);
        }
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }

    // ========== 管理员方法 ==========

    /**
     * 管理员分页查询留言列表
     */
    @Transactional(readOnly = true)
    public IPage<Messages> getMessagesForAdmin(Integer page, Integer size, String nickname, Integer status, Boolean includeDeleted) {
        Page<Messages> pageParam = new Page<>(page, size);
        QueryWrapper<Messages> wrapper = new QueryWrapper<>();

        if (nickname != null && !nickname.isEmpty()) {
            wrapper.like("nickname", nickname);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        if (!Boolean.TRUE.equals(includeDeleted)) {
            wrapper.isNull("deleted_at");
        }

        wrapper.orderByDesc("created_at");
        return this.page(pageParam, wrapper);
    }

    /**
     * 审核留言（通过/拒绝）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean reviewMessage(Long id, Integer status, Long adminId) {
        if (status == null || (status != 1 && status != 2)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "审核状态必须为 1(通过) 或 2(拒绝)");
        }
        Messages message = this.getById(id);
        if (message == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "留言不存在");
        }
        message.setStatus(status);
        message.setUpdatedAt(new Date());
        if (adminId != null) {
            message.setUpdatedBy(adminId);
        }
        return this.updateById(message);
    }

    /**
     * 回复留言
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean replyMessage(Long id, String reply, Long adminId) {
        Messages message = this.getById(id);
        if (message == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "留言不存在");
        }
        message.setReply(reply);
        message.setRepliedAt(new Date());
        message.setRepliedBy(adminId);
        message.setStatus(1); // 回复后自动通过
        message.setUpdatedAt(new Date());
        if (adminId != null) {
            message.setUpdatedBy(adminId);
        }
        return this.updateById(message);
    }

    /**
     * 软删除留言
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteMessage(Long id) {
        Messages message = this.getById(id);
        if (message == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "留言不存在");
        }
        return baseMapper.softDeleteById(id) > 0;
    }

    /**
     * 批量软删除留言
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean batchDeleteMessages(List<Long> ids) {
        int total = 0;
        for (Long id : ids) {
            Messages message = this.getById(id);
            if (message != null) {
                total += baseMapper.softDeleteById(id);
            }
        }
        return total > 0;
    }

    /**
     * 恢复已删除的留言
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean restoreMessage(Long id) {
        // 使用原生 SQL 查询，绕过 @TableLogic 自动过滤
        Messages message = baseMapper.selectUnfilteredById(id);
        if (message == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "留言不存在");
        }
        if (message.getDeletedAt() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该留言未被删除");
        }

        return baseMapper.restoreById(id) > 0;
    }

    /**
     * 彻底删除留言（物理删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean permanentDeleteMessage(Long id) {
        // 使用原生 DELETE SQL，绕过 @TableLogic 逻辑删除拦截
        return baseMapper.physicalDeleteById(id) > 0;
    }

    /**
     * 批量彻底删除留言（物理删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean batchPermanentDeleteMessages(List<Long> ids) {
        int total = 0;
        for (Long id : ids) {
            total += baseMapper.physicalDeleteById(id);
        }
        return total > 0;
    }
}
