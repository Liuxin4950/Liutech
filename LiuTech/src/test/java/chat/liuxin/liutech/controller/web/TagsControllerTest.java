package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.TagResp;
import chat.liuxin.liutech.service.TagsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TagsControllerTest {

    private TagsController controller;
    private TagsService tagsService;

    @BeforeEach
    void setUp() {
        tagsService = mock(TagsService.class);
        controller = new TagsController(tagsService);
    }

    // ========== getAllTags ==========

    @Test
    void getAllTags_shouldReturnList() {
        TagResp tag = new TagResp();
        tag.setName("Java");
        when(tagsService.getAllTagsWithPostCount()).thenReturn(List.of(tag));

        Result<List<TagResp>> result = controller.getAllTags();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("Java", result.getData().get(0).getName());
    }

    @Test
    void getAllTags_shouldReturnEmptyListWhenNoneExist() {
        when(tagsService.getAllTagsWithPostCount()).thenReturn(Collections.emptyList());

        Result<List<TagResp>> result = controller.getAllTags();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().isEmpty());
    }

    // ========== getTagById ==========

    @Test
    void getTagById_shouldReturnTagWhenExists() {
        TagResp tag = new TagResp();
        tag.setName("Spring");
        tag.setPostCount(5);
        when(tagsService.getTagByIdWithPostCount(1L)).thenReturn(tag);

        Result<TagResp> result = controller.getTagById(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("Spring", result.getData().getName());
        assertEquals(5, result.getData().getPostCount());
    }

    @Test
    void getTagById_shouldReturnErrorWhenNotFound() {
        when(tagsService.getTagByIdWithPostCount(999L)).thenReturn(null);

        Result<TagResp> result = controller.getTagById(999L);

        assertEquals(ErrorCode.TAG_NOT_FOUND.getCode(), result.getCode());
        assertNull(result.getData());
    }

    // ========== getHotTags ==========

    @Test
    void getHotTags_shouldReturnList() {
        TagResp tag = new TagResp();
        tag.setName("Docker");
        tag.setPostCount(10);
        when(tagsService.getHotTags(5)).thenReturn(List.of(tag));

        Result<List<TagResp>> result = controller.getHotTags(5);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().size());
    }

    @Test
    void getHotTags_shouldReturnEmptyListWhenNoneExist() {
        when(tagsService.getHotTags(20)).thenReturn(Collections.emptyList());

        Result<List<TagResp>> result = controller.getHotTags(20);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().isEmpty());
    }

    // ========== searchTagsByName ==========

    @Test
    void searchTagsByName_shouldReturnMatchingTags() {
        TagResp tag = new TagResp();
        tag.setName("JavaScript");
        when(tagsService.getTagsByName("script")).thenReturn(List.of(tag));

        Result<List<TagResp>> result = controller.searchTagsByName("script");

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("JavaScript", result.getData().get(0).getName());
    }

    @Test
    void searchTagsByName_shouldReturnEmptyWhenNoMatch() {
        when(tagsService.getTagsByName("nonexistent")).thenReturn(Collections.emptyList());

        Result<List<TagResp>> result = controller.searchTagsByName("nonexistent");

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().isEmpty());
    }
}
