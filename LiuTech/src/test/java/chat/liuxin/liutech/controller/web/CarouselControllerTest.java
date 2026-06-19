package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.CarouselResp;
import chat.liuxin.liutech.service.CarouselService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CarouselControllerTest {

    private CarouselController controller;
    private CarouselService carouselService;

    @BeforeEach
    void setUp() {
        controller = new CarouselController();
        carouselService = mock(CarouselService.class);
        ReflectionTestUtils.setField(controller, "carouselService", carouselService);
    }

    // ========== getActiveCarousels ==========

    @Test
    void getActiveCarousels_shouldReturnList() {
        CarouselResp carousel = new CarouselResp();
        carousel.setTitle("Banner 1");
        carousel.setImageUrl("https://example.com/banner1.jpg");
        when(carouselService.getActiveCarousels()).thenReturn(List.of(carousel));

        Result<List<CarouselResp>> result = controller.getActiveCarousels();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("Banner 1", result.getData().get(0).getTitle());
    }

    @Test
    void getActiveCarousels_shouldReturnEmptyListWhenNoneExist() {
        when(carouselService.getActiveCarousels()).thenReturn(Collections.emptyList());

        Result<List<CarouselResp>> result = controller.getActiveCarousels();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().isEmpty());
    }
}
