package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.mapper.PostSeriesMapper;
import chat.liuxin.liutech.mapper.PostsMapper;
import chat.liuxin.liutech.model.PostSeries;
import chat.liuxin.liutech.model.Posts;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.PostSeriesResp;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PostSeriesService 单元测试
 * 覆盖系列 CRUD、软删除解除文章关联、拖拽排序限定 seriesId 等核心逻辑
 *
 * @author 刘鑫
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostSeriesServiceTest {

    @Mock
    private PostSeriesMapper postSeriesMapper;

    @Mock
    private PostsMapper postsMapper;

    @InjectMocks
    private PostSeriesService postSeriesService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(postSeriesService, "baseMapper", postSeriesMapper);
        // LambdaUpdateWrapper 需要 TableInfo 缓存
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PostSeries.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Posts.class);
    }

    private PostSeriesResp createSeriesResp(Long id, String name, int postCount) {
        PostSeriesResp resp = new PostSeriesResp();
        resp.setId(id);
        resp.setName(name);
        resp.setDescription("desc");
        resp.setPostCount(postCount);
        return resp;
    }

    // ========== getAllSeriesWithPostCount ==========

    @Test
    void getAllSeriesWithPostCount_shouldReturnList() {
        when(postSeriesMapper.selectSeriesWithPostCount()).thenReturn(
                List.of(createSeriesResp(1L, "Spring", 3), createSeriesResp(2L, "Vue", 5)));

        List<PostSeriesResp> result = postSeriesService.getAllSeriesWithPostCount();

        assertEquals(2, result.size());
        assertEquals("Spring", result.get(0).getName());
        verify(postSeriesMapper).selectSeriesWithPostCount();
    }

    @Test
    void getAllSeriesWithPostCount_shouldReturnEmpty() {
        when(postSeriesMapper.selectSeriesWithPostCount()).thenReturn(Collections.emptyList());
        assertTrue(postSeriesService.getAllSeriesWithPostCount().isEmpty());
    }

    // ========== getSeriesListForAdmin ==========

    @Test
    void getSeriesListForAdmin_shouldReturnPage() {
        when(postSeriesMapper.selectSeriesForAdmin(any(), any(), any(), any()))
                .thenReturn(List.of(createSeriesResp(1L, "Spring", 3)));
        when(postSeriesMapper.countSeriesForAdmin(any(), any())).thenReturn(1);

        PageResp<PostSeriesResp> result = postSeriesService.getSeriesListForAdmin(1, 10, null, false);

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        assertEquals(1L, result.getTotal());
    }

    // ========== getSeriesDetail ==========

    @Test
    void getSeriesDetail_shouldReturnWhenExists() {
        when(postSeriesMapper.selectSeriesDetailById(1L)).thenReturn(createSeriesResp(1L, "Spring", 3));
        PostSeriesResp result = postSeriesService.getSeriesDetail(1L);
        assertNotNull(result);
        assertEquals("Spring", result.getName());
    }

    @Test
    void getSeriesDetail_shouldReturnNullWhenNotExists() {
        when(postSeriesMapper.selectSeriesDetailById(999L)).thenReturn(null);
        assertNull(postSeriesService.getSeriesDetail(999L));
    }

    // ========== getSeriesByName ==========

    @Test
    void getSeriesByName_shouldThrowWhenNameBlank() {
        assertThrows(BusinessException.class, () -> postSeriesService.getSeriesByName(""));
        assertThrows(BusinessException.class, () -> postSeriesService.getSeriesByName(null));
    }

    // ========== save ==========

    @Test
    void save_shouldThrowWhenNameExists() {
        PostSeriesResp resp = createSeriesResp(null, "Spring", 0);
        PostSeries existing = new PostSeries();
        existing.setName("Spring");
        when(postSeriesMapper.selectOne(any())).thenReturn(existing);

        assertThrows(BusinessException.class, () -> postSeriesService.save(resp));
        verify(postSeriesMapper, never()).insert(any(PostSeries.class));
    }

    @Test
    void save_shouldInsertWhenNameAvailable() {
        PostSeriesResp resp = createSeriesResp(null, "Spring", 0);
        when(postSeriesMapper.selectOne(any())).thenReturn(null);
        when(postSeriesMapper.insert(any(PostSeries.class))).thenReturn(1);

        boolean result = postSeriesService.save(resp);

        assertTrue(result);
        verify(postSeriesMapper).insert(any(PostSeries.class));
    }

    // ========== updateById ==========

    @Test
    void updateById_shouldCallUpdateById() {
        PostSeriesResp resp = createSeriesResp(1L, "Spring", 0);
        when(postSeriesMapper.updateById(any(PostSeries.class))).thenReturn(1);
        assertTrue(postSeriesService.updateById(resp));
        verify(postSeriesMapper).updateById(any(PostSeries.class));
    }

    // ========== removeByIds（核心：解除文章关联 + 软删系列） ==========

    @Test
    void removeByIds_shouldUnlinkPostsAndSoftDeleteSeries() {
        when(postsMapper.update(isNull(), any())).thenReturn(1);
        when(postSeriesMapper.update(isNull(), any())).thenReturn(1);

        boolean result = postSeriesService.removeByIds(List.of(1L, 2L));

        assertTrue(result);
        verify(postsMapper).update(isNull(), any());       // 解除文章 series_id
        verify(postSeriesMapper).update(isNull(), any());  // 软删系列
    }

    @Test
    void removeByIds_shouldReturnFalseWhenEmptyIds() {
        assertFalse(postSeriesService.removeByIds(Collections.emptyList()));
        assertFalse(postSeriesService.removeByIds(null));
        verify(postsMapper, never()).update(any(), any());
    }

    // ========== restoreSeries ==========

    @Test
    void restoreSeries_shouldReturnTrueWhenRestored() {
        when(postSeriesMapper.restoreSeriesById(1L)).thenReturn(1);
        assertTrue(postSeriesService.restoreSeries(1L));
    }

    @Test
    void restoreSeries_shouldReturnFalseWhenNotDeleted() {
        when(postSeriesMapper.restoreSeriesById(1L)).thenReturn(0);
        assertFalse(postSeriesService.restoreSeries(1L));
    }

    // ========== permanentDeleteSeries ==========

    @Test
    void permanentDeleteSeries_shouldCallDeleteBatchIds() {
        when(postSeriesMapper.deleteBatchIds(List.of(1L))).thenReturn(1);
        assertTrue(postSeriesService.permanentDeleteSeries(1L));
        verify(postSeriesMapper).deleteBatchIds(List.of(1L));
    }

    // ========== batchPermanentDeleteSeries ==========

    @Test
    void batchPermanentDeleteSeries_shouldCallDeleteBatchIds() {
        when(postSeriesMapper.deleteBatchIds(List.of(1L, 2L))).thenReturn(2);
        assertTrue(postSeriesService.batchPermanentDeleteSeries(List.of(1L, 2L)));
    }

    @Test
    void batchPermanentDeleteSeries_shouldReturnFalseWhenEmpty() {
        assertFalse(postSeriesService.batchPermanentDeleteSeries(Collections.emptyList()));
    }

    // ========== batchUpdateSeriesSort（核心：限定 seriesId） ==========

    @Test
    void batchUpdateSeriesSort_shouldPassSeriesIdToUpdate() {
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("postId", 100L);
        item.put("seriesSort", 2);
        items.add(item);

        when(postsMapper.updateSeriesSort(eq(100L), eq(1L), eq(2), eq(10L))).thenReturn(1);

        boolean result = postSeriesService.batchUpdateSeriesSort(1L, items, 10L);

        assertTrue(result);
        verify(postsMapper).updateSeriesSort(100L, 1L, 2, 10L);
    }

    @Test
    void batchUpdateSeriesSort_shouldDefaultSortToZeroWhenNull() {
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("postId", 100L);
        items.add(item);

        when(postsMapper.updateSeriesSort(eq(100L), eq(1L), eq(0), eq(10L))).thenReturn(1);

        postSeriesService.batchUpdateSeriesSort(1L, items, 10L);

        verify(postsMapper).updateSeriesSort(100L, 1L, 0, 10L);
    }

    @Test
    void batchUpdateSeriesSort_shouldReturnFalseWhenEmpty() {
        assertFalse(postSeriesService.batchUpdateSeriesSort(1L, Collections.emptyList(), 10L));
    }
}
