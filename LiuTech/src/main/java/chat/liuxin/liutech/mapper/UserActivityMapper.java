package chat.liuxin.liutech.mapper;

import chat.liuxin.liutech.resp.UserActivityResp;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface UserActivityMapper {
    List<UserActivityResp> selectActivities(@Param("userId") Long userId, @Param("offset") long offset, @Param("size") int size);
    long countActivities(@Param("userId") Long userId);
}
