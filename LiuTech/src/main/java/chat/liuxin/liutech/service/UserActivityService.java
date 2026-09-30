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
        PageQuery query = PageQuery.of(page, size, 20);
        return new PageResp<>(mapper.selectActivities(userId, query.offset(), (int) query.size()),
                mapper.countActivities(userId), query.current(), query.size());
    }
}
