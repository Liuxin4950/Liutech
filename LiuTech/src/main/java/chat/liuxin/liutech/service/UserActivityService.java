package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.*;
import chat.liuxin.liutech.mapper.UserActivityMapper;
import chat.liuxin.liutech.resp.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserActivityService {
    private final UserActivityMapper mapper;

    @Transactional(readOnly = true)
    public PageResp<UserActivityResp> list(Long userId, int page, int size) {
        if (page < 1 || size < 1 || size > 20) throw new BusinessException(ErrorCode.PARAMS_ERROR, "分页参数不正确，每页最多20条");
        return new PageResp<>(mapper.selectActivities(userId, (long) (page - 1) * size, size), mapper.countActivities(userId), (long) page, (long) size);
    }
}
