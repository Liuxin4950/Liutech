package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.PostTagsMapper;
import chat.liuxin.liutech.mapper.TagsMapper;
import chat.liuxin.liutech.model.Tags;
import chat.liuxin.liutech.resp.TagResp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * TagsService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class TagsServiceTest {

    @Mock
    private TagsMapper tagsMapper;

    @Mock
    private PostTagsMapper postTagsMapper;

    @InjectMocks
    private TagsService tagsService;

    // ========== 辅助方法 ==========

    private Tags createTag(Long id, String name, String description) {
        Tags tag = new Tags();
        tag.setId(id);
        tag.setName(name);
        tag.setDescription(description);
        tag.setCreatedAt(new Date());
        tag.setUpdatedAt(new Date());
        return tag;
    }

    private TagResp createTagResp(Long id, String name, String description, int postCount) {
        TagResp resp = new TagResp();
        resp.setId(id);
        resp.setName(name);
        resp.setDescription(description);
        resp.setCreatedAt(new Date());
        resp.setUpdatedAt(new Date());
        resp.setPostCount(postCount);
        return resp;
    }

    // ========== getAllTagsWithPostCount ==========

    @Test
    void getAllTagsWithPostCount_shouldReturnList() {
        List<TagResp> expected = Arrays.asList(
                createTagResp(1L, "Java", "Java相关", 20),
                createTagResp(2L, "Vue", "Vue相关", 15)
        );
        when(tagsMapper.selectTagsWithPostCount()).thenReturn(expected);

        List<TagResp> result = tagsService.getAllTagsWithPostCount();

        assertEquals(2, result.size());
        assertEquals("Java", result.get(0).getName());
        assertEquals("Vue", result.get(1).getName());
        verify(tagsMapper).selectTagsWithPostCount();
    }

    @Test
    void getAllTagsWithPostCount_shouldReturnEmptyList() {
        when(tagsMapper.selectTagsWithPostCount()).thenReturn(Collections.emptyList());

        List<TagResp> result = tagsService.getAllTagsWithPostCount();

        assertTrue(result.isEmpty());
        verify(tagsMapper).selectTagsWithPostCount();
    }

    // ========== getHotTags ==========

    @Test
    void getHotTags_shouldReturnHotTags() {
        List<TagResp> expected = Arrays.asList(
                createTagResp(1L, "Java", "Java相关", 50),
                createTagResp(2L, "Spring", "Spring相关", 30)
        );
        when(tagsMapper.selectHotTags(10)).thenReturn(expected);

        List<TagResp> result = tagsService.getHotTags(10);

        assertEquals(2, result.size());
        assertEquals("Java", result.get(0).getName());
        assertEquals(50, result.get(0).getPostCount());
        verify(tagsMapper).selectHotTags(10);
    }

    @Test
    void getHotTags_shouldReturnEmptyList() {
        when(tagsMapper.selectHotTags(10)).thenReturn(Collections.emptyList());

        List<TagResp> result = tagsService.getHotTags(10);

        assertTrue(result.isEmpty());
        verify(tagsMapper).selectHotTags(10);
    }

    // ========== searchTagsByName (getTagsByName) ==========

    @Test
    void searchTagsByName_shouldReturnMatchingTags() {
        List<TagResp> expected = Arrays.asList(
                createTagResp(1L, "Java", "Java相关", 20)
        );
        when(tagsMapper.selectTagsByName("Java")).thenReturn(expected);

        List<TagResp> result = tagsService.getTagsByName("Java");

        assertEquals(1, result.size());
        assertEquals("Java", result.get(0).getName());
        verify(tagsMapper).selectTagsByName("Java");
    }

    @Test
    void searchTagsByName_shouldReturnEmptyListWhenNoMatch() {
        when(tagsMapper.selectTagsByName("不存在")).thenReturn(Collections.emptyList());

        List<TagResp> result = tagsService.getTagsByName("不存在");

        assertTrue(result.isEmpty());
        verify(tagsMapper).selectTagsByName("不存在");
    }

    @Test
    void searchTagsByName_shouldThrowWhenNameIsNull() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> tagsService.getTagsByName(null));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        verify(tagsMapper, never()).selectTagsByName(any());
    }

    @Test
    void searchTagsByName_shouldThrowWhenNameIsEmpty() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> tagsService.getTagsByName("  "));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        verify(tagsMapper, never()).selectTagsByName(any());
    }
}
