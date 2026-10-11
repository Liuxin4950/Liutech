package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.PostAttachmentsMapper;
import chat.liuxin.liutech.mapper.ResourceDownloadsMapper;
import chat.liuxin.liutech.mapper.ResourcesMapper;
import chat.liuxin.liutech.resp.ResourcePostResp;
import chat.liuxin.liutech.resp.ResourceResp;
import chat.liuxin.liutech.storage.StorageFileCleanup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
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
