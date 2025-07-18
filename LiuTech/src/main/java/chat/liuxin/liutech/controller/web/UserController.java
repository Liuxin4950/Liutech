package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

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
