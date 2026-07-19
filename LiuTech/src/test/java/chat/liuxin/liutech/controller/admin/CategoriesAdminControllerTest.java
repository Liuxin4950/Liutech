package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.CategoryResp;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.service.CategoriesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CategoriesAdminControllerTest {

    private CategoriesAdminController controller;
    private CategoriesService categoriesService;

    @BeforeEach
    void setUp() {
        categoriesService = mock(CategoriesService.class);
        controller = new CategoriesAdminController(categoriesService);
    }

    // ========== getCategoryList ==========

    @Test
    void getCategoryList_shouldReturnPageResult() {
        PageResp<CategoryResp> pageResp = new PageResp<>(Collections.emptyList(), 0L, 1L, 10L);
        when(categoriesService.getCategoryListForAdmin(1, 10, null, false)).thenReturn(pageResp);

        Result<PageResp<CategoryResp>> result = controller.getCategoryList(1, 10, null, false);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals(0L, result.getData().getTotal());
    }

    @Test
    void getCategoryList_shouldPassFilterParams() {
        PageResp<CategoryResp> pageResp = new PageResp<>(Collections.emptyList(), 3L, 1L, 10L);
        when(categoriesService.getCategoryListForAdmin(1, 10, "tech", true)).thenReturn(pageResp);

        Result<PageResp<CategoryResp>> result = controller.getCategoryList(1, 10, "tech", true);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(categoriesService).getCategoryListForAdmin(1, 10, "tech", true);
    }

    @Test
    void getCategoryList_shouldPropagateException() {
        when(categoriesService.getCategoryListForAdmin(anyInt(), anyInt(), any(), anyBoolean()))
                .thenThrow(new RuntimeException("db error"));

        // 瘦身后 Controller 不再 try-catch，异常直接抛出由 GlobalExceptionHandler 统一兜底
        assertThrows(RuntimeException.class, () -> controller.getCategoryList(1, 10, null, false));
    }

    // ========== getCategoryById ==========

    @Test
    void getCategoryById_shouldReturnCategoryWhenExists() {
        CategoryResp category = new CategoryResp();
        category.setName("Java");
        when(categoriesService.getById(1L)).thenReturn(category);

        Result<CategoryResp> result = controller.getCategoryById(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("Java", result.getData().getName());
    }

    @Test
    void getCategoryById_shouldReturnErrorWhenNotFound() {
        when(categoriesService.getById(999L)).thenReturn(null);

        Result<CategoryResp> result = controller.getCategoryById(999L);

        assertEquals(ErrorCode.NOT_FOUND.getCode(), result.getCode());
    }

    @Test
    void getCategoryById_shouldThrowWhenIdInvalid() {
        assertThrows(BusinessException.class, () -> controller.getCategoryById(0L));
    }

    // ========== createCategory ==========

    @Test
    void createCategory_shouldReturnSuccess() {
        CategoryResp category = new CategoryResp();
        category.setName("New Category");
        when(categoriesService.save(category)).thenReturn(true);

        Result<String> result = controller.createCategory(category);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void createCategory_shouldReturnErrorWhenServiceFails() {
        CategoryResp category = new CategoryResp();
        category.setName("Duplicate");
        when(categoriesService.save(category)).thenReturn(false);

        Result<String> result = controller.createCategory(category);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    @Test
    void createCategory_shouldThrowWhenNull() {
        assertThrows(BusinessException.class, () -> controller.createCategory(null));
    }

    // ========== updateCategory ==========

    @Test
    void updateCategory_shouldReturnSuccess() {
        CategoryResp category = new CategoryResp();
        category.setName("Updated");
        when(categoriesService.updateById(any())).thenReturn(true);

        Result<String> result = controller.updateCategory(1L, category);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1L, category.getId());
    }

    @Test
    void updateCategory_shouldReturnErrorWhenServiceFails() {
        CategoryResp category = new CategoryResp();
        when(categoriesService.updateById(any())).thenReturn(false);

        Result<String> result = controller.updateCategory(1L, category);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    @Test
    void updateCategory_shouldThrowWhenIdInvalid() {
        CategoryResp category = new CategoryResp();
        assertThrows(BusinessException.class, () -> controller.updateCategory(0L, category));
    }

    // ========== deleteCategory ==========

    @Test
    void deleteCategory_shouldReturnSuccess() {
        when(categoriesService.removeByIds(List.of(1L))).thenReturn(true);

        Result<String> result = controller.deleteCategory(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void deleteCategory_shouldReturnErrorWhenServiceFails() {
        when(categoriesService.removeByIds(List.of(1L))).thenReturn(false);

        Result<String> result = controller.deleteCategory(1L);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== batchDeleteCategories ==========

    @Test
    void batchDeleteCategories_shouldReturnSuccess() {
        when(categoriesService.removeByIds(List.of(1L, 2L))).thenReturn(true);

        Result<String> result = controller.batchDeleteCategories(List.of(1L, 2L));

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void batchDeleteCategories_shouldThrowWhenEmpty() {
        assertThrows(BusinessException.class, () -> controller.batchDeleteCategories(Collections.emptyList()));
    }

    // ========== restoreCategory ==========

    @Test
    void restoreCategory_shouldReturnSuccess() {
        when(categoriesService.restoreCategory(1L)).thenReturn(true);

        Result<String> result = controller.restoreCategory(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void restoreCategory_shouldReturnErrorWhenServiceFails() {
        when(categoriesService.restoreCategory(1L)).thenReturn(false);

        Result<String> result = controller.restoreCategory(1L);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== permanentDeleteCategory ==========

    @Test
    void permanentDeleteCategory_shouldReturnSuccess() {
        when(categoriesService.permanentDeleteCategory(1L)).thenReturn(true);

        Result<String> result = controller.permanentDeleteCategory(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void permanentDeleteCategory_shouldReturnErrorWhenServiceFails() {
        when(categoriesService.permanentDeleteCategory(1L)).thenReturn(false);

        Result<String> result = controller.permanentDeleteCategory(1L);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== batchPermanentDeleteCategories ==========

    @Test
    void batchPermanentDeleteCategories_shouldReturnSuccess() {
        when(categoriesService.batchPermanentDeleteCategories(List.of(1L, 2L))).thenReturn(true);

        Result<String> result = controller.batchPermanentDeleteCategories(List.of(1L, 2L));

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void batchPermanentDeleteCategories_shouldThrowWhenEmpty() {
        assertThrows(BusinessException.class, () -> controller.batchPermanentDeleteCategories(Collections.emptyList()));
    }
}
