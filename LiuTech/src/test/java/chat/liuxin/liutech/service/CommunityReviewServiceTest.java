package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.CommentsMapper;
import chat.liuxin.liutech.mapper.CommunityMapper;
import chat.liuxin.liutech.model.Comments;
import chat.liuxin.liutech.model.CommunityBot;
import chat.liuxin.liutech.req.CommunityReq;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityReviewServiceTest {
    @Mock CommunityMapper mapper;
    @Mock CommentsMapper comments;
    @Mock ImageReferenceService imageReferenceService;
    @InjectMocks CommunityService service;

    @Test void deletedRoleHistoryIncludesDeletedCommentsWithoutPublicVisibilityCheck() {
        CommunityBot bot = new CommunityBot(); bot.setId(1L); bot.setEnabled(false); bot.setDeletedAt(new Date());
        Comments deleted = new Comments(); deleted.setId(10L); deleted.setBotId(1L); deleted.setDeletedAt(new Date());
        when(mapper.botForAdmin(1L)).thenReturn(bot);
        when(comments.selectCommentsByBotForAdmin(1L,20L,20)).thenReturn(List.of(deleted));
        when(comments.countCommentsByBotForAdmin(1L)).thenReturn(25L);
        var page = service.botCommentsForAdmin(1L,2,20);
        assertEquals(List.of(deleted),page.getRecords()); assertEquals(25L,page.getTotal());
        assertEquals(2L,page.getCurrent()); assertFalse(page.getHasNext());
        verify(mapper,never()).bot(any()); verify(mapper,never()).publicPost(any());
    }

    @Test void invalidPaginationIsRejectedBeforeQueryAndOffsetCannotOverflowInt() {
        when(mapper.botForAdmin(1L)).thenReturn(new CommunityBot());
        assertThrows(BusinessException.class,()->service.botCommentsForAdmin(1L,0,20));
        assertThrows(BusinessException.class,()->service.botCommentsForAdmin(1L,1,101));
        service.botCommentsForAdmin(1L,Integer.MAX_VALUE,100);
        verify(comments).selectCommentsByBotForAdmin(1L,214748364600L,100);
    }

    @Test void absentRoleCannotBeUsedToQueryAnotherAuthorsHistory() {
        var error = assertThrows(BusinessException.class,()->service.botCommentsForAdmin(999L,1,20));
        assertEquals(ErrorCode.COMMUNITY_BOT_NOT_FOUND.getCode(),error.getCode());
        verifyNoInteractions(comments);
    }

    @Test void threadIncludesRootOtherRoleAndHumanEvenWhenRequestedCommentDeleted() {
        Comments root = comment(10L,null,null); root.setUserId(7L);
        Comments selected = comment(11L,10L,1L); selected.setDeletedAt(new Date());
        Comments other = comment(12L,11L,2L);
        when(comments.selectCommentsForAdminById(11L)).thenReturn(selected);
        when(comments.selectRootCommentIdForAdmin(11L,2L)).thenReturn(10L);
        when(comments.countThreadCommentsForAdmin(10L,2L)).thenReturn(3L);
        when(comments.selectThreadCommentsForAdmin(10L,2L,200)).thenReturn(List.of(root,selected,other));
        var thread = service.commentThreadForAdmin(11L);
        assertEquals(10L,thread.rootCommentId()); assertEquals(2L,thread.postId());
        assertEquals("文章",thread.postTitle()); assertEquals(3,thread.total());
        assertFalse(thread.truncated()); assertEquals(List.of(root,selected,other),thread.comments());
        verifyNoInteractions(mapper);
    }

    @Test void oversizedThreadHasExplicitTruncationAndFixedLimit() {
        Comments selected = comment(11L,10L,1L);
        when(comments.selectCommentsForAdminById(11L)).thenReturn(selected);
        when(comments.selectRootCommentIdForAdmin(11L,2L)).thenReturn(10L);
        when(comments.countThreadCommentsForAdmin(10L,2L)).thenReturn(240L);
        var thread = service.commentThreadForAdmin(11L);
        assertTrue(thread.truncated()); assertEquals(240L,thread.total());
        verify(comments).selectThreadCommentsForAdmin(10L,2L,200);
    }

    @Test void absentCommentIsExplicit404AndSkipsThreadQuery() {
        var error=assertThrows(BusinessException.class,()->service.commentThreadForAdmin(999L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(),error.getCode());
        verify(comments,never()).selectRootCommentIdForAdmin(any(),any());
    }

    @Test void administratorPromptIsSavedAndLimitedWithoutBreakingLegacyConstructor() {
        CommunityBot bot = new CommunityBot(); bot.setId(1L);
        when(mapper.lockBot(1L)).thenReturn(bot); when(mapper.bot(1L)).thenReturn(bot);
        var req = new CommunityReq.Bot("角色",null,"性格",null,null,false,50,"先交流想法，不使用表情");
        assertEquals("先交流想法，不使用表情",service.saveBot(1L,req).getSystemPrompt());
        verify(mapper).updateBot(bot);
        assertNull(new CommunityReq.Bot("角色",null,"性格",null,null,false,50).systemPrompt());
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var tooLong = new CommunityReq.Bot("角色",null,"性格",null,null,false,50,"x".repeat(10001));
            assertTrue(validatorFactory.getValidator().validate(tooLong).stream()
                .anyMatch(v->v.getPropertyPath().toString().equals("systemPrompt")));
        }
    }

    private static Comments comment(Long id, Long parentId, Long botId) {
        Comments comment = new Comments(); comment.setId(id); comment.setPostId(2L); comment.setPostTitle("文章");
        comment.setParentId(parentId); comment.setBotId(botId); return comment;
    }
}
