package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.TagResp;
import chat.liuxin.liutech.service.TagsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TagsAdminControllerTest {

    private TagsAdminController controller;
    private TagsService tagsService;

    @BeforeEach
    void setUp() {
        tagsService = mock(TagsService.class);
        controller = new TagsAdminController(tagsService);
    }

    // ========== getTagList ==========

    @Test
    void getTagList_shouldReturnPageResult() {
        PageResp<TagResp> pageResp = new PageResp<>(Collections.emptyList(), 0L, 1L, 10L);
        when(tagsService.getTagListForAdmin(1, 10, null, false)).thenReturn(pageResp);

        Result<PageResp<TagResp>> result = controller.getTagList(1, 10, null, false);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals(0L, result.getData().getTotal());
    }

    @Test
    void getTagList_shouldPassFilterParams() {
        PageResp<TagResp> pageResp = new PageResp<>(Collections.emptyList(), 2L, 1L, 10L);
        when(tagsService.getTagListForAdmin(1, 10, "java", true)).thenReturn(pageResp);

        Result<PageResp<TagResp>> result = controller.getTagList(1, 10, "java", true);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(tagsService).getTagListForAdmin(1, 10, "java", true);
    }

    @Test
    void getTagList_shouldPropagateException() {
        when(tagsService.getTagListForAdmin(anyInt(), anyInt(), any(), anyBoolean()))
                .thenThrow(new RuntimeException("db error"));

        // 瘦身后 Controller 不再 try-catch，异常直接抛出由 GlobalExceptionHandler 统一兜底
        assertThrows(RuntimeException.class, () -> controller.getTagList(1, 10, null, false));
    }

    // ========== getTagById ==========

    @Test
    void getTagById_shouldReturnTagWhenExists() {
        TagResp tag = new TagResp();
        tag.setName("Spring Boot");
        when(tagsService.getById(1L)).thenReturn(tag);

        Result<TagResp> result = controller.getTagById(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("Spring Boot", result.getData().getName());
    }

    @Test
    void getTagById_shouldReturnErrorWhenNotFound() {
        when(tagsService.getById(999L)).thenReturn(null);

        Result<TagResp> result = controller.getTagById(999L);

        assertEquals(ErrorCode.TAG_NOT_FOUND.getCode(), result.getCode());
    }

    // ========== createTag ==========

    @Test
    void createTag_shouldReturnSuccess() {
        TagResp tag = new TagResp();
        tag.setName("New Tag");
        when(tagsService.save(tag)).thenReturn(true);

        Result<String> result = controller.createTag(tag);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void createTag_shouldReturnErrorWhenServiceFails() {
        TagResp tag = new TagResp();
        tag.setName("Duplicate");
        when(tagsService.save(tag)).thenReturn(false);

        Result<String> result = controller.createTag(tag);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    @Test
    void createTag_shouldPropagateException() {
        TagResp tag = new TagResp();
        when(tagsService.save(any())).thenThrow(new RuntimeException("error"));

        // 瘦身后 Controller 不再 try-catch，异常直接抛出由 GlobalExceptionHandler 统一兜底
        assertThrows(RuntimeException.class, () -> controller.createTag(tag));
    }

    // ========== updateTag ==========

    @Test
    void updateTag_shouldReturnSuccess() {
        TagResp tag = new TagResp();
        tag.setName("Updated");
        when(tagsService.updateById(any())).thenReturn(true);

        Result<String> result = controller.updateTag(1L, tag);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1L, tag.getId());
    }

    @Test
    void updateTag_shouldReturnErrorWhenServiceFails() {
        TagResp tag = new TagResp();
        when(tagsService.updateById(any())).thenReturn(false);

        Result<String> result = controller.updateTag(1L, tag);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== deleteTag ==========

    @Test
    void deleteTag_shouldReturnSuccess() {
        when(tagsService.removeByIds(List.of(1L))).thenReturn(true);

        Result<String> result = controller.deleteTag(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void deleteTag_shouldReturnErrorWhenServiceFails() {
        when(tagsService.removeByIds(List.of(1L))).thenReturn(false);

        Result<String> result = controller.deleteTag(1L);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== batchDeleteTags ==========

    @Test
    void batchDeleteTags_shouldReturnSuccess() {
        when(tagsService.removeByIds(List.of(1L, 2L))).thenReturn(true);

        Result<String> result = controller.batchDeleteTags(List.of(1L, 2L));

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void batchDeleteTags_shouldReturnErrorWhenServiceFails() {
        when(tagsService.removeByIds(any())).thenReturn(false);

        Result<String> result = controller.batchDeleteTags(List.of(1L));

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== restoreTag ==========

    @Test
    void restoreTag_shouldReturnSuccess() {
        when(tagsService.restoreTag(1L)).thenReturn(true);

        Result<String> result = controller.restoreTag(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void restoreTag_shouldReturnErrorWhenServiceFails() {
        when(tagsService.restoreTag(1L)).thenReturn(false);

        Result<String> result = controller.restoreTag(1L);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== permanentDeleteTag ==========

    @Test
    void permanentDeleteTag_shouldReturnSuccess() {
        when(tagsService.permanentDeleteTag(1L)).thenReturn(true);

        Result<String> result = controller.permanentDeleteTag(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void permanentDeleteTag_shouldReturnErrorWhenServiceFails() {
        when(tagsService.permanentDeleteTag(1L)).thenReturn(false);

        Result<String> result = controller.permanentDeleteTag(1L);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== batchPermanentDeleteTags ==========

    @Test
    void batchPermanentDeleteTags_shouldReturnSuccess() {
        when(tagsService.batchPermanentDeleteTags(List.of(1L, 2L))).thenReturn(true);

        Result<String> result = controller.batchPermanentDeleteTags(List.of(1L, 2L));

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void batchPermanentDeleteTags_shouldReturnErrorWhenServiceFails() {
        when(tagsService.batchPermanentDeleteTags(any())).thenReturn(false);

        Result<String> result = controller.batchPermanentDeleteTags(List.of(1L));

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }
}
