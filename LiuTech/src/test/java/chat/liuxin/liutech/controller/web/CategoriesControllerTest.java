package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.CategoryResp;
import chat.liuxin.liutech.service.CategoriesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CategoriesControllerTest {

    private CategoriesController controller;
    private CategoriesService categoriesService;

    @BeforeEach
    void setUp() {
        categoriesService = mock(CategoriesService.class);
        controller = new CategoriesController(categoriesService);
    }

    // ========== getAllCategories ==========

    @Test
    void getAllCategories_shouldReturnList() {
        CategoryResp cat = new CategoryResp();
        cat.setName("Java");
        when(categoriesService.getAllCategoriesWithPostCount()).thenReturn(List.of(cat));

        Result<List<CategoryResp>> result = controller.getAllCategories();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("Java", result.getData().get(0).getName());
    }

    @Test
    void getAllCategories_shouldReturnEmptyListWhenNoneExist() {
        when(categoriesService.getAllCategoriesWithPostCount()).thenReturn(Collections.emptyList());

        Result<List<CategoryResp>> result = controller.getAllCategories();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().isEmpty());
    }

    // ========== getCategoryById ==========

    @Test
    void getCategoryById_shouldReturnCategoryWhenExists() {
        CategoryResp cat = new CategoryResp();
        cat.setName("Spring Boot");
        when(categoriesService.getById(1L)).thenReturn(cat);

        Result<CategoryResp> result = controller.getCategoryById(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("Spring Boot", result.getData().getName());
    }

    @Test
    void getCategoryById_shouldReturnErrorWhenNotFound() {
        when(categoriesService.getById(999L)).thenReturn(null);

        Result<CategoryResp> result = controller.getCategoryById(999L);

        assertEquals(ErrorCode.CATEGORY_NOT_FOUND.getCode(), result.getCode());
        assertNull(result.getData());
    }
}
