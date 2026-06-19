package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.AnnouncementsMapper;
import chat.liuxin.liutech.model.Announcements;
import chat.liuxin.liutech.resp.AnnouncementResp;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AnnouncementsService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class AnnouncementsServiceTest {

    @Mock
    private AnnouncementsMapper announcementsMapper;

    @InjectMocks
    private AnnouncementsService announcementsService;

    // ========== 辅助方法 ==========

    private Announcements createAnnouncement(Long id, String title, Integer type, Integer priority, Integer status) {
        Announcements announcement = new Announcements();
        announcement.setId(id);
        announcement.setTitle(title);
        announcement.setContent("测试内容");
        announcement.setType(type);
        announcement.setPriority(priority);
        announcement.setStatus(status);
        announcement.setStartTime(new Date(System.currentTimeMillis() - 86400000L));
        announcement.setEndTime(new Date(System.currentTimeMillis() + 86400000L));
        announcement.setIsTop(0);
        announcement.setViewCount(0);
        announcement.setCreatedAt(new Date());
        announcement.setUpdatedAt(new Date());
        return announcement;
    }

    // ========== getValidAnnouncements ==========

    @Test
    void getValidAnnouncements_shouldReturnPaginatedResults() {
        Announcements announcement = createAnnouncement(1L, "公告1", 1, 2, 1);

        Page<Announcements> page = new Page<>(1, 10);
        page.setRecords(List.of(announcement));
        page.setTotal(1);

        when(announcementsMapper.selectValidAnnouncements(any(Page.class))).thenReturn(page);

        IPage<AnnouncementResp> result = announcementsService.getValidAnnouncements(1, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());

        AnnouncementResp resp = result.getRecords().get(0);
        assertEquals(1L, resp.getId());
        assertEquals("公告1", resp.getTitle());
        assertEquals("系统", resp.getTypeName());
        assertEquals("中", resp.getPriorityName());
        assertEquals("发布", resp.getStatusName());
        assertNotNull(resp.getIsValid());

        verify(announcementsMapper).selectValidAnnouncements(any(Page.class));
    }

    @Test
    void getValidAnnouncements_shouldReturnEmptyPage() {
        Page<Announcements> page = new Page<>(1, 10);
        page.setRecords(Collections.emptyList());
        page.setTotal(0);

        when(announcementsMapper.selectValidAnnouncements(any(Page.class))).thenReturn(page);

        IPage<AnnouncementResp> result = announcementsService.getValidAnnouncements(1, 10);

        assertNotNull(result);
        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        verify(announcementsMapper).selectValidAnnouncements(any(Page.class));
    }

    // ========== getTopAnnouncements ==========

    @Test
    void getTopAnnouncements_shouldReturnTopAnnouncements() {
        Announcements announcement = createAnnouncement(1L, "置顶公告", 1, 3, 1);
        announcement.setIsTop(1);

        when(announcementsMapper.selectTopAnnouncements(5)).thenReturn(List.of(announcement));

        List<AnnouncementResp> result = announcementsService.getTopAnnouncements(5);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("置顶公告", result.get(0).getTitle());
        assertEquals("系统", result.get(0).getTypeName());
        assertEquals("高", result.get(0).getPriorityName());
        assertEquals("发布", result.get(0).getStatusName());
        verify(announcementsMapper).selectTopAnnouncements(5);
    }

    @Test
    void getTopAnnouncements_shouldUseDefaultLimitWhenNull() {
        when(announcementsMapper.selectTopAnnouncements(5)).thenReturn(Collections.emptyList());

        List<AnnouncementResp> result = announcementsService.getTopAnnouncements(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(announcementsMapper).selectTopAnnouncements(5);
    }

    @Test
    void getTopAnnouncements_shouldUseDefaultLimitWhenZero() {
        when(announcementsMapper.selectTopAnnouncements(5)).thenReturn(Collections.emptyList());

        List<AnnouncementResp> result = announcementsService.getTopAnnouncements(0);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(announcementsMapper).selectTopAnnouncements(5);
    }

    @Test
    void getTopAnnouncements_shouldUseDefaultLimitWhenNegative() {
        when(announcementsMapper.selectTopAnnouncements(5)).thenReturn(Collections.emptyList());

        List<AnnouncementResp> result = announcementsService.getTopAnnouncements(-1);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(announcementsMapper).selectTopAnnouncements(5);
    }

    // ========== getLatestAnnouncements ==========

    @Test
    void getLatestAnnouncements_shouldReturnLatestAnnouncements() {
        Announcements a1 = createAnnouncement(1L, "最新公告1", 2, 1, 1);
        Announcements a2 = createAnnouncement(2L, "最新公告2", 3, 2, 1);

        when(announcementsMapper.selectLatestAnnouncements(10)).thenReturn(List.of(a1, a2));

        List<AnnouncementResp> result = announcementsService.getLatestAnnouncements(10);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("最新公告1", result.get(0).getTitle());
        assertEquals("活动", result.get(0).getTypeName());
        assertEquals("最新公告2", result.get(1).getTitle());
        assertEquals("维护", result.get(1).getTypeName());
        verify(announcementsMapper).selectLatestAnnouncements(10);
    }

    @Test
    void getLatestAnnouncements_shouldUseDefaultLimitWhenNull() {
        when(announcementsMapper.selectLatestAnnouncements(10)).thenReturn(Collections.emptyList());

        List<AnnouncementResp> result = announcementsService.getLatestAnnouncements(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(announcementsMapper).selectLatestAnnouncements(10);
    }

    @Test
    void getLatestAnnouncements_shouldUseDefaultLimitWhenZero() {
        when(announcementsMapper.selectLatestAnnouncements(10)).thenReturn(Collections.emptyList());

        List<AnnouncementResp> result = announcementsService.getLatestAnnouncements(0);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(announcementsMapper).selectLatestAnnouncements(10);
    }

    @Test
    void getLatestAnnouncements_shouldUseDefaultLimitWhenNegative() {
        when(announcementsMapper.selectLatestAnnouncements(10)).thenReturn(Collections.emptyList());

        List<AnnouncementResp> result = announcementsService.getLatestAnnouncements(-1);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(announcementsMapper).selectLatestAnnouncements(10);
    }

    // ========== 转换验证 ==========

    @Test
    void getTopAnnouncements_shouldMapAllTypeNames() {
        Announcements sys = createAnnouncement(1L, "系统公告", 1, 1, 1);
        Announcements act = createAnnouncement(2L, "活动公告", 2, 1, 1);
        Announcements mnt = createAnnouncement(3L, "维护公告", 3, 1, 1);
        Announcements oth = createAnnouncement(4L, "其他公告", 4, 1, 1);

        when(announcementsMapper.selectTopAnnouncements(5)).thenReturn(List.of(sys, act, mnt, oth));

        List<AnnouncementResp> result = announcementsService.getTopAnnouncements(5);

        assertEquals("系统", result.get(0).getTypeName());
        assertEquals("活动", result.get(1).getTypeName());
        assertEquals("维护", result.get(2).getTypeName());
        assertEquals("其他", result.get(3).getTypeName());
    }

    @Test
    void getTopAnnouncements_shouldMapAllPriorityNames() {
        Announcements low = createAnnouncement(1L, "低优先级", 1, 1, 1);
        Announcements med = createAnnouncement(2L, "中优先级", 1, 2, 1);
        Announcements high = createAnnouncement(3L, "高优先级", 1, 3, 1);
        Announcements urgent = createAnnouncement(4L, "紧急", 1, 4, 1);

        when(announcementsMapper.selectTopAnnouncements(5)).thenReturn(List.of(low, med, high, urgent));

        List<AnnouncementResp> result = announcementsService.getTopAnnouncements(5);

        assertEquals("低", result.get(0).getPriorityName());
        assertEquals("中", result.get(1).getPriorityName());
        assertEquals("高", result.get(2).getPriorityName());
        assertEquals("紧急", result.get(3).getPriorityName());
    }

    @Test
    void getTopAnnouncements_shouldMapAllStatusNames() {
        Announcements draft = createAnnouncement(1L, "草稿", 1, 1, 0);
        Announcements published = createAnnouncement(2L, "已发布", 1, 1, 1);
        Announcements offline = createAnnouncement(3L, "已下线", 1, 1, 2);

        when(announcementsMapper.selectTopAnnouncements(5)).thenReturn(List.of(draft, published, offline));

        List<AnnouncementResp> result = announcementsService.getTopAnnouncements(5);

        assertEquals("草稿", result.get(0).getStatusName());
        assertEquals("发布", result.get(1).getStatusName());
        assertEquals("下线", result.get(2).getStatusName());
    }
}
