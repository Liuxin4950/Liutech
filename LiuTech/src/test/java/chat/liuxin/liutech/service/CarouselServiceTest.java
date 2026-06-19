package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.CarouselMapper;
import chat.liuxin.liutech.mapper.ImagesMapper;
import chat.liuxin.liutech.model.Carousel;
import chat.liuxin.liutech.resp.CarouselResp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * CarouselService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class CarouselServiceTest {

    @Mock
    private CarouselMapper carouselMapper;

    @Mock
    private ImagesMapper imagesMapper;

    @Mock
    private ImagesService imagesService;

    @InjectMocks
    private CarouselService carouselService;

    // ========== 辅助方法 ==========

    private Carousel createCarousel(Long id, String title, String imageUrl, Integer sortOrder, Integer status) {
        Carousel carousel = new Carousel();
        carousel.setId(id);
        carousel.setTitle(title);
        carousel.setImageUrl(imageUrl);
        carousel.setLinkUrl("https://example.com");
        carousel.setSortOrder(sortOrder);
        carousel.setStatus(status);
        carousel.setCreatedAt(new Date());
        carousel.setUpdatedAt(new Date());
        return carousel;
    }

    // ========== getActiveCarousels ==========

    @Test
    void getActiveCarousels_shouldReturnActiveList() {
        Carousel c1 = createCarousel(1L, "轮播1", "/img/1.jpg", 10, 1);
        Carousel c2 = createCarousel(2L, "轮播2", "/img/2.jpg", 5, 1);

        when(carouselMapper.selectActiveCarousels()).thenReturn(List.of(c1, c2));

        List<CarouselResp> result = carouselService.getActiveCarousels();

        assertNotNull(result);
        assertEquals(2, result.size());

        CarouselResp r1 = result.get(0);
        assertEquals(1L, r1.getId());
        assertEquals("轮播1", r1.getTitle());
        assertEquals("/img/1.jpg", r1.getImageUrl());
        assertEquals("https://example.com", r1.getLinkUrl());
        assertEquals(10, r1.getSortOrder());
        assertEquals(1, r1.getStatus());
        assertEquals("启用", r1.getStatusName());
        assertEquals("正常", r1.getDeleteStatus());
        assertNotNull(r1.getCreatedAt());
        assertNotNull(r1.getUpdatedAt());

        CarouselResp r2 = result.get(1);
        assertEquals(2L, r2.getId());
        assertEquals("轮播2", r2.getTitle());
        assertEquals("启用", r2.getStatusName());
        assertEquals("正常", r2.getDeleteStatus());

        verify(carouselMapper).selectActiveCarousels();
    }

    @Test
    void getActiveCarousels_shouldReturnEmptyList() {
        when(carouselMapper.selectActiveCarousels()).thenReturn(Collections.emptyList());

        List<CarouselResp> result = carouselService.getActiveCarousels();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(carouselMapper).selectActiveCarousels();
    }

    @Test
    void getActiveCarousels_shouldMapStatusNames() {
        Carousel enabled = createCarousel(1L, "启用轮播", "/img/1.jpg", 10, 1);
        Carousel disabled = createCarousel(2L, "禁用轮播", "/img/2.jpg", 5, 0);

        when(carouselMapper.selectActiveCarousels()).thenReturn(List.of(enabled, disabled));

        List<CarouselResp> result = carouselService.getActiveCarousels();

        assertEquals("启用", result.get(0).getStatusName());
        assertEquals("禁用", result.get(1).getStatusName());
    }

    @Test
    void getActiveCarousels_shouldSetDeleteStatusNormal() {
        Carousel carousel = createCarousel(1L, "轮播", "/img/1.jpg", 1, 1);

        when(carouselMapper.selectActiveCarousels()).thenReturn(List.of(carousel));

        List<CarouselResp> result = carouselService.getActiveCarousels();

        assertEquals("正常", result.get(0).getDeleteStatus());
        assertNull(result.get(0).getDeletedAt());
    }

    @Test
    void getActiveCarousels_shouldMapAllFields() {
        Carousel carousel = createCarousel(42L, "完整轮播", "/uploads/banner.png", 100, 1);
        carousel.setLinkUrl("https://blog.example.com/post/1");

        when(carouselMapper.selectActiveCarousels()).thenReturn(List.of(carousel));

        List<CarouselResp> result = carouselService.getActiveCarousels();
        CarouselResp resp = result.get(0);

        assertEquals(42L, resp.getId());
        assertEquals("完整轮播", resp.getTitle());
        assertEquals("/uploads/banner.png", resp.getImageUrl());
        assertEquals("https://blog.example.com/post/1", resp.getLinkUrl());
        assertEquals(100, resp.getSortOrder());
        assertEquals(1, resp.getStatus());
        assertEquals("启用", resp.getStatusName());
        assertEquals("正常", resp.getDeleteStatus());
    }
}
