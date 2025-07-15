package chat.liuxin.liutech.controller;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.User;
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
    public List<User> getAllUsers() {
        return userService.findAll();
    }

    @PostMapping("/add")
    public String addUser(@RequestBody User user) {
        userService.addUser(user);
        return "添加成功";
    }

    @DeleteMapping("/{id}")
    public String deleteUser(@PathVariable Long id) {
        userService.deleteById(id);
        return "删除成功";
    }
    @GetMapping("/find")
    public Result<List<User>> findByUserName(@RequestParam String userName) {
        return Result.success(userService.findByUserName(userName));
    }

}
