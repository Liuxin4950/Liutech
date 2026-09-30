package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.*;
import chat.liuxin.liutech.resp.DashboardResp.TrendData;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DashboardServiceTest {
    @Test void trendsAreGroupedOnceAndMissingDatesAreZeroFilled() {
        PostsMapper posts = mock(PostsMapper.class);
        UserMapper users = mock(UserMapper.class);
        when(posts.countPostsByDateRange(anyString(), anyString())).thenAnswer(call ->
                List.of(TrendData.builder().date(call.getArgument(0)).count(3L).build()));
        when(users.countUsersByDateRange(anyString(), anyString())).thenReturn(List.of());
        var service = new DashboardService(posts, users, mock(CategoriesMapper.class),
                mock(TagsMapper.class), mock(CommentsMapper.class));
        var result = service.getDashboardStats();
        assertEquals(7, result.getPostTrend().size());
        assertEquals(3, result.getPostTrend().getFirst().getCount());
        assertEquals(0, result.getPostTrend().getLast().getCount());
        assertTrue(result.getUserTrend().stream().allMatch(day -> day.getCount() == 0));
        verify(posts, times(1)).countPostsByDateRange(anyString(), anyString());
        verify(users, times(1)).countUsersByDateRange(anyString(), anyString());
        verify(posts, never()).countPostsByDate(any());
        verify(users, never()).countUsersByDate(any());
    }
}
