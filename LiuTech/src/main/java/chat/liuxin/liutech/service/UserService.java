package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.req.LoginReq;
import chat.liuxin.liutech.req.RegisterReq;
import chat.liuxin.liutech.resl.UserResl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public List<Users> findAll() {
        return userMapper.selectList(null);
    }

    public void addUser(Users user) {
        userMapper.insert(user);
    }

    public void updateUser(Users user) {
        userMapper.updateById(user);
    }

    public void deleteById(Long id) {
        userMapper.deleteById(id);
    }

    public List<Users> findByUserName(String userName) {
        return userMapper.findByUserName(userName);
    }

    public UserResl register(RegisterReq registerReq) {
        // 1. 检查用户名是否已存在
        List<Users> existingUsers = findByUserName(registerReq.getUsername());
        if (existingUsers != null && !existingUsers.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名已存在");
        }

        // 2. 密码长度验证
        if (registerReq.getPassword().length() < 6) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度不能少于6位");
        }

        // 3. 创建用户对象并设置属性
        Users user = new Users();
        user.setUsername(registerReq.getUsername());
        user.setPasswordHash(passwordEncoder.encode(registerReq.getPassword())); // 密码加密存储
        user.setStatus(1); // 默认正常状态
        Date now = new Date();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        // 4. 保存用户信息
        addUser(user);

        // 5. 转换为响应对象
        UserResl userResl = new UserResl();
        BeanUtils.copyProperties(user, userResl);

        return userResl;
    }

    public UserResl login(LoginReq loginReq) {
        // 1. 查询用户信息
        List<Users> users = findByUserName(loginReq.getUsername());
        if (users == null || users.isEmpty()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }

        Users user = users.get(0);

        // 2. 验证密码
        if (!passwordEncoder.matches(loginReq.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }

        // 3. 更新最后登录时间
        user.setLastLoginAt(new Date());
        updateUser(user);

        // 4. 转换为响应对象
        UserResl userResl = new UserResl();
        BeanUtils.copyProperties(user, userResl);

        return userResl;
    }
}
