package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.PostAttachmentsMapper;
import chat.liuxin.liutech.mapper.ResourceDownloadsMapper;
import chat.liuxin.liutech.mapper.ResourcesMapper;
import chat.liuxin.liutech.model.Resources;
import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.resp.ResourcePostResp;
import chat.liuxin.liutech.resp.ResourceResp;
import chat.liuxin.liutech.storage.StorageFileCleanup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.math.BigDecimal;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourcesAdminServiceTest {
    private ResourcesMapper mapper;
    private ResourcesAdminService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ResourcesMapper.class);
        service = new ResourcesAdminService(mapper, mock(ResourceDownloadsMapper.class),
                mock(PostAttachmentsMapper.class), mock(StorageFileCleanup.class));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void creatingFreeResourceClearsStalePrice() {
        ResourceResp input = resource(null);
        input.setDownloadType(0);
        input.setPointsNeeded(new BigDecimal("5"));
        when(mapper.insert(any(Resources.class))).thenReturn(1);
        assertTrue(service.createResource(input));
        var saved = ArgumentCaptor.forClass(Resources.class);
        verify(mapper).insert(saved.capture());
        assertEquals(0, saved.getValue().getPointsNeeded().signum());
    }

    @Test
    void paidResourceRejectsInvalidOrUnrepresentablePriceBeforeSaving() {
        for (BigDecimal price : new BigDecimal[]{null, BigDecimal.ZERO, new BigDecimal("-1"),
                new BigDecimal("0.001"), new BigDecimal("10000000000")}) {
            ResourceResp input = resource(7L);
            input.setDownloadType(1);
            input.setPointsNeeded(price);
            assertThrows(BusinessException.class, () -> service.createResource(input));
            if (price != null) assertThrows(BusinessException.class, () -> service.updateResource(input));
        }
        verify(mapper, never()).insert(any(Resources.class));
        verify(mapper, never()).updateById(any(Resources.class));
    }

    @Test
    void editingFreeResourceClearsPriceAndPaidResourcePreservesDecimals() {
        when(mapper.updateById(any(Resources.class))).thenReturn(1);
        ResourceResp input = resource(7L);
        input.setDownloadType(0);
        input.setPointsNeeded(new BigDecimal("5"));
        assertTrue(service.updateResource(input));
        input.setDownloadType(1);
        input.setPointsNeeded(new BigDecimal("2.50"));
        assertTrue(service.updateResource(input));
        var updates = ArgumentCaptor.forClass(Resources.class);
        verify(mapper, times(2)).updateById(updates.capture());
        assertEquals(0, updates.getAllValues().getFirst().getPointsNeeded().signum());
        assertEquals(new BigDecimal("2.50"), updates.getAllValues().getLast().getPointsNeeded());
    }

    @Test
    void unrelatedPartialUpdateDoesNotResetPaidResource() {
        when(mapper.updateById(any(Resources.class))).thenReturn(1);
        assertTrue(service.updateResource(resource(7L)));
        var updated = ArgumentCaptor.forClass(Resources.class);
        verify(mapper).updateById(updated.capture());
        assertNull(updated.getValue().getDownloadType());
        assertNull(updated.getValue().getPointsNeeded());
        verify(mapper, never()).selectById(anyLong());
    }

    @Test
    void partialPriceUpdateRetainsTheExistingDownloadType() {
        Resources existing = new Resources();
        existing.setDownloadType(1);
        existing.setPointsNeeded(BigDecimal.ONE);
        when(mapper.selectById(7L)).thenReturn(existing);
        when(mapper.updateById(any(Resources.class))).thenReturn(1);
        ResourceResp input = resource(7L);
        input.setPointsNeeded(new BigDecimal("2.50"));
        assertTrue(service.updateResource(input));
        var updated = ArgumentCaptor.forClass(Resources.class);
        verify(mapper).updateById(updated.capture());
        assertEquals(1, updated.getValue().getDownloadType());
        assertEquals(new BigDecimal("2.50"), updated.getValue().getPointsNeeded());
    }

    @Test
    void pageGroupsEveryAssociationWithoutChangingResourcePagination() {
        ResourceResp first = resource(7L);
        ResourceResp second = resource(8L);
        ResourceResp unbound = resource(9L);
        ResourcePostResp deletedPost = post(7L, 20L);
        deletedPost.setDeletedAt(new Date());
        when(mapper.selectResourcesForAdmin(10L, 10, "", null, null, false))
                .thenReturn(List.of(first, second, unbound));
        when(mapper.countResourcesForAdmin("", null, null, false)).thenReturn(23);
        when(mapper.selectAssociatedPostsForAdmin(List.of(7L, 8L, 9L)))
                .thenReturn(List.of(post(8L, 30L), deletedPost, post(7L, 10L)));

        var page = service.getResourceListForAdmin(2, 10, "", null, null, false);

        assertEquals(23, page.getTotal());
        assertEquals(3, page.getRecords().size());
        assertEquals(List.of(20L, 10L), first.getAssociatedPosts().stream().map(ResourcePostResp::getPostId).toList());
        assertNotNull(first.getAssociatedPosts().getFirst().getDeletedAt());
        assertEquals(30L, second.getAssociatedPosts().getFirst().getPostId());
        assertTrue(unbound.getAssociatedPosts().isEmpty());
        verify(mapper, times(1)).selectAssociatedPostsForAdmin(List.of(7L, 8L, 9L));
    }

    @Test
    void detailReturnsAllReferencesBeyondAStandardFirstPage() {
        ResourceResp resource = resource(7L);
        List<ResourcePostResp> references = LongStream.rangeClosed(1L, 35L).mapToObj(id -> post(7L, id)).toList();
        when(mapper.selectResourceById(7L)).thenReturn(resource);
        when(mapper.selectAssociatedPostsForAdmin(List.of(7L))).thenReturn(references);

        assertEquals(35, service.getResourceById(7L).getAssociatedPosts().size());
        verify(mapper).selectAssociatedPostsForAdmin(List.of(7L));
    }

    @Test
    void emptyPageDoesNotIssueAnInvalidEmptyInQuery() {
        when(mapper.selectResourcesForAdmin(0L, 10, null, null, null, false)).thenReturn(List.of());
        when(mapper.countResourcesForAdmin(null, null, null, false)).thenReturn(0);

        assertTrue(service.getResourceListForAdmin(1, 10, null, null, null, false).getRecords().isEmpty());
        verify(mapper, never()).selectAssociatedPostsForAdmin(anyList());
    }

    @Test
    void missingResourceDoesNotFetchOrReturnOrphanReferences() {
        when(mapper.selectResourceById(7L)).thenReturn(null);

        assertNull(service.getResourceById(7L));
        verify(mapper, never()).selectAssociatedPostsForAdmin(anyList());
    }

    private ResourceResp resource(Long id) {
        ResourceResp resource = new ResourceResp();
        resource.setId(id);
        resource.setName("附件 " + id);
        return resource;
    }

    private ResourcePostResp post(Long resourceId, Long postId) {
        ResourcePostResp post = new ResourcePostResp();
        post.setResourceId(resourceId);
        post.setPostId(postId);
        post.setTitle("文章 " + postId);
        post.setStatus("published");
        return post;
    }
}
