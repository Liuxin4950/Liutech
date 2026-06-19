package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 用户认证查询服务
 * 供 JwtAuthenticationFilter 调用，避免 Filter 直接依赖 Mapper 绕过 Service 层。
 *
 * @author 刘鑫
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuthLookupService {

    private final UserMapper userMapper;

    private final JwtUtil jwtUtil;

    /**
     * 根据用户 ID 查询用户（仅返回未删除的记录）
     */
    public Users selectById(Long userId) {
        return userMapper.selectById(userId);
    }

    /**
     * 校验当前数据库用户状态是否允许 token 继续生效
     *
     * @param currentUser   数据库中的用户记录
     * @param tokenUsername  token 中携带的用户名
     * @param token         原始 JWT token（用于提取 passwordHash）
     * @return true 表示 token 仍然有效
     */
    public boolean isCurrentUserTokenValid(Users currentUser, String tokenUsername, String token) {
        if (currentUser == null || currentUser.getDeletedAt() != null) {
            return false;
        }
        if (!StringUtils.hasText(currentUser.getUsername()) || !currentUser.getUsername().equals(tokenUsername)) {
            return false;
        }
        if (!Integer.valueOf(1).equals(currentUser.getStatus())) {
            return false;
        }
        String tokenPasswordHash = jwtUtil.getPasswordHashFromToken(token);
        // token 中存储的是 SHA-256(passwordHash)，需要对数据库值也做 SHA-256 后再比较
        return !StringUtils.hasText(tokenPasswordHash) || tokenPasswordHash.equals(JwtUtil.sha256(currentUser.getPasswordHash()));
    }
}
