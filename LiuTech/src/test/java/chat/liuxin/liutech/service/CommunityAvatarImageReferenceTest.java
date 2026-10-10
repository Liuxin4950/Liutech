package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.*;
import chat.liuxin.liutech.model.*;
import chat.liuxin.liutech.storage.StorageFileCleanup;
import chat.liuxin.liutech.utils.FileUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 历史漏记头像可由对账恢复，并在引用明细中解释为何不能作为孤立图片清理。 */
class CommunityAvatarImageReferenceTest {
    @Test void reconciliationRestoresActiveAndRetiredAvatarsUsingTheSameNormalizedPath() {
        ImagesMapper images = mock(ImagesMapper.class);
        PostsMapper posts = mock(PostsMapper.class);
        CommunityMapper community = mock(CommunityMapper.class);
        FileUtil fileUtil = mock(FileUtil.class);
        ImagesService imageService = mock(ImagesService.class);
        ImageReferenceService references = new ImageReferenceService(imageService, fileUtil);
        ImageUsageReconcileService reconcile = new ImageUsageReconcileService(images, posts,
            mock(UserMapper.class), mock(CarouselMapper.class), mock(MusicMapper.class),
            mock(PostSeriesMapper.class), fileUtil, references, community);
        CommunityBot active = role(1L, "/uploads/images/avatar.png", false);
        CommunityBot retired = role(2L, "https://liuxin.chat/uploads/images/avatar.png", true);
        when(community.botsForImageReferences()).thenReturn(List.of(active, retired));
        when(fileUtil.normalizeToRelativePath(active.getAvatarUrl())).thenReturn("images/avatar.png");
        when(fileUtil.normalizeToRelativePath(retired.getAvatarUrl())).thenReturn("images/avatar.png");
        Images image = new Images(); image.setId(24L); image.setFilePath("images/avatar.png");
        when(images.selectList(any())).thenReturn(List.of(image));
        var result = reconcile.reconcileUsageCount();
        verify(images).resetUsageCount();
        verify(images).incrementUsageCount(24L, 2);
        assertEquals(1, result.getReferencedPaths());
        assertEquals(1, result.getUpdatedImages());
        assertEquals(0, result.getMissingImages());
    }

    @Test void referenceDetailsIncludeRetiredIdentityButDoNotMatchSimilarFileNames() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> type : List.of(Posts.class, Users.class, Carousel.class, Music.class, PostSeries.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
        ImagesMapper images = mock(ImagesMapper.class);
        CommunityMapper community = mock(CommunityMapper.class);
        FileUtil fileUtil = mock(FileUtil.class);
        ImagesAdminService admin = new ImagesAdminService(images, mock(StorageFileCleanup.class),
            mock(PostsMapper.class), mock(UserMapper.class), mock(CarouselMapper.class),
            mock(MusicMapper.class), mock(PostSeriesMapper.class), community, fileUtil);
        Images image = new Images(); image.setId(24L); image.setFilePath("images/avatar.png");
        when(images.selectById(24L)).thenReturn(image);
        CommunityBot retired = role(1L, "https://liuxin.chat/uploads/images/avatar.png", true);
        CommunityBot other = role(2L, "/uploads/images/avatar.png-other.png", false);
        when(community.botsForImageReferences()).thenReturn(List.of(retired, other));
        when(fileUtil.normalizeToRelativePath(retired.getAvatarUrl())).thenReturn("images/avatar.png");
        when(fileUtil.normalizeToRelativePath(other.getAvatarUrl())).thenReturn("images/avatar.png-other.png");
        var refs = admin.getReferences(24L);
        assertEquals(1, refs.size());
        assertEquals("community_avatar", refs.getFirst().getSourceType());
        assertEquals(1L, refs.getFirst().getSourceId());
        assertTrue(refs.getFirst().getSourceField().contains("保留历史身份"));
    }

    private CommunityBot role(Long id, String url, boolean retired) {
        CommunityBot bot = new CommunityBot(); bot.setId(id); bot.setName("角色" + id); bot.setAvatarUrl(url);
        if (retired) bot.setDeletedAt(new Date());
        return bot;
    }
}
