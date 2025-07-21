package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.req.LoginReq;
import chat.liuxin.liutech.req.RegisterReq;
import chat.liuxin.liutech.resl.UserResl;
import chat.liuxin.liutech.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 用户注册接口
     */
    @PostMapping("/register")
    public Result<UserResl> register(@Valid @RequestBody RegisterReq userReq) {
        log.info("用户注册: {}", userReq.getUsername());
        UserResl userResl = userService.register(userReq);
        return Result.success(userResl);
    }

    /**
     * 用户登录接口
     */
    @PostMapping("/login")
    public Result<UserResl> login(@Valid @RequestBody LoginReq userReq) {
        log.info("用户登录: {}", userReq.getUsername());
        UserResl userResl = userService.login(userReq);
        return Result.success(userResl);
    }
    @GetMapping("/all")
    public Result<List<Users>> getAllUsers() {
        return Result.success(userService.findAll());
    }

    @PostMapping("/add")
    public Result<String> addUser(@RequestBody Users user) {
        userService.addUser(user);
        return Result.success("添加成功");
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteUser(@PathVariable Long id) {
        userService.deleteById(id);
        return Result.success("删除成功");
    }
    @GetMapping("/find")
    public Result<List<Users>> findByUserName(@RequestParam String userName) {
        return Result.success(userService.findByUserName(userName));
    }

}
