package chat.liuxin.liutech.controller.internal;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.resp.AuthIntrospectionResp;
import chat.liuxin.liutech.utils.UserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅供容器内受信任服务调用的身份内省接口。 */
@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class AuthIntrospectionController {

    private final UserUtils userUtils;

    @GetMapping("/introspect")
    public Result<AuthIntrospectionResp> introspect() {
        Users user = userUtils.getCurrentUser();
        if (user == null) {
            return Result.fail(ErrorCode.UNAUTHORIZED, "用户未认证");
        }
        return Result.success(new AuthIntrospectionResp(user.getId(), user.getUsername(), user.getRole()));
    }
}
