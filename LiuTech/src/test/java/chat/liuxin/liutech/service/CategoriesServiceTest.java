package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.*;
import chat.liuxin.liutech.model.Categories;
import chat.liuxin.liutech.resp.CategoryResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * CategoriesService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class CategoriesServiceTest {

    @Mock
    private CategoriesMapper categoriesMapper;

    @Mock
    private PostsMapper postsMapper;

    @Mock
    private PostFavoritesMapper postFavoritesMapper;

    @Mock
    private PostLikesMapper postLikesMapper;

    @Mock
    private CommentsMapper commentsMapper;

    @Mock
    private PostTagsMapper postTagsMapper;

    @Mock
    private PostAttachmentsMapper postAttachmentsMapper;

    @InjectMocks
    private CategoriesService categoriesService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(categoriesService, "baseMapper", categoriesMapper);
    }

    // ========== 辅助方法 ==========

    private Categories createCategory(Long id, String name, String description) {
        Categories category = new Categories();
        category.setId(id);
        category.setName(name);
        category.setDescription(description);
        category.setCreatedAt(new Date());
        category.setUpdatedAt(new Date());
        return category;
    }

    private CategoryResp createCategoryResp(Long id, String name, String description, int postCount) {
        CategoryResp resp = new CategoryResp();
        resp.setId(id);
        resp.setName(name);
        resp.setDescription(description);
        resp.setCreatedAt(new Date());
        resp.setUpdatedAt(new Date());
        resp.setPostCount(postCount);
        return resp;
    }

    // ========== getAllCategoriesWithPostCount ==========

    @Test
    void getAllCategoriesWithPostCount_shouldReturnList() {
        List<CategoryResp> expected = Arrays.asList(
                createCategoryResp(1L, "技术", "技术文章", 10),
                createCategoryResp(2L, "生活", "生活随笔", 5)
        );
        when(categoriesMapper.selectCategoriesWithPostCount()).thenReturn(expected);

        List<CategoryResp> result = categoriesService.getAllCategoriesWithPostCount();

        assertEquals(2, result.size());
        assertEquals("技术", result.get(0).getName());
        assertEquals("生活", result.get(1).getName());
        verify(categoriesMapper).selectCategoriesWithPostCount();
    }

    @Test
    void getAllCategoriesWithPostCount_shouldReturnEmptyList() {
        when(categoriesMapper.selectCategoriesWithPostCount()).thenReturn(Collections.emptyList());

        List<CategoryResp> result = categoriesService.getAllCategoriesWithPostCount();

        assertTrue(result.isEmpty());
        verify(categoriesMapper).selectCategoriesWithPostCount();
    }

    // ========== getById ==========

    @Test
    void getById_shouldReturnCategoryRespWhenExists() {
        Categories category = createCategory(1L, "技术", "技术文章");
        when(categoriesMapper.selectById(1L)).thenReturn(category);

        CategoryResp result = categoriesService.getById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("技术", result.getName());
        assertEquals("技术文章", result.getDescription());
        assertEquals(0, result.getPostCount());
        verify(categoriesMapper).selectById(1L);
    }

    @Test
    void getById_shouldReturnNullWhenNotExists() {
        when(categoriesMapper.selectById(999L)).thenReturn(null);

        CategoryResp result = categoriesService.getById(999L);

        assertNull(result);
        verify(categoriesMapper).selectById(999L);
    }
}
