package chat.liuxin.liutech.controller;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.req.UserReq;
import chat.liuxin.liutech.resl.UserResl;
import chat.liuxin.liutech.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/user")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 用户注册接口
     */
    @PostMapping("/register")
    public Result<UserResl> register(@Valid @RequestBody UserReq userReq) {
        log.info("用户注册: {}", userReq.getUsername());
        UserResl userResl = userService.register(userReq);
        return Result.success(userResl);
    }

    /**
     * 用户登录接口
     */
    @PostMapping("/login")
    public Result<UserResl> login(@Valid @RequestBody UserReq userReq) {
        log.info("用户登录: {}", userReq.getUsername());
        UserResl userResl = userService.login(userReq);
        return Result.success(userResl);
    }
}
