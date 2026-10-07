package chat.liuxin.liutech.filter;

import chat.liuxin.liutech.controller.web.PostsController;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.resp.PostDetailResp;
import chat.liuxin.liutech.service.PostInteractionService;
import chat.liuxin.liutech.service.PostsService;
import chat.liuxin.liutech.service.UserAuthLookupService;
import chat.liuxin.liutech.service.ViewHistoryService;
import chat.liuxin.liutech.utils.JwtUtil;
import chat.liuxin.liutech.utils.UserUtils;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实签发/解析 JWT，覆盖公开文章请求穿过过滤器后保留登录身份。 */
class JwtAuthenticationFilterTest {
    private JwtAuthenticationFilter filter;
    private UserMapper userMapper;
    private UserUtils userUtils;
    private Users user;
    private String token;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey", "test-only-secret-key-for-jwt-authentication-filter-0123456789abcdef");
        ReflectionTestUtils.setField(jwtUtil, "expirationTime", 60_000L);
        userMapper = mock(UserMapper.class);
        userUtils = new UserUtils(userMapper, mock(CacheManager.class));
        user = new Users();
        user.setId(42L);
        user.setUsername("reader");
        user.setRole("user");
        user.setStatus(1);
        user.setPasswordHash("test-password-hash");
        when(userMapper.selectById(42L)).thenReturn(user);
        token = jwtUtil.generateToken(42L, "reader", "user", user.getPasswordHash());
        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "jwtUtil", jwtUtil);
        ReflectionTestUtils.setField(filter, "userAuthLookupService", new UserAuthLookupService(userMapper, jwtUtil));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publicArticleRequestRetainsAuthenticatedReader() throws Exception {
        PostsService posts = mock(PostsService.class);
        PostDetailResp detail = new PostDetailResp();
        detail.setId(7L);
        detail.setLikeStatus(1);
        when(posts.getPostDetail(7L, 42L)).thenReturn(detail);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PostsController(posts,
                mock(PostInteractionService.class), mock(ViewHistoryService.class), userUtils))
                .addFilters(filter).build();

        mvc.perform(get("/posts/7").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeStatus").value(1));
        verify(posts).getPostDetail(7L, 42L);
    }

    @Test
    void publicArticleStillAcceptsGuestWithoutLookingUpUser() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/posts/7"), new MockHttpServletResponse(),
                (request, response) -> assertNull(userUtils.getCurrentUserId()));
        verifyNoInteractions(userMapper);
    }

    @Test
    void protectedRequestRetainsAuthenticatedReader() throws Exception {
        filter.doFilter(request("/resource/check/7", token), new MockHttpServletResponse(),
                (request, response) -> assertEquals(42L, userUtils.getCurrentUserId()));
    }

    @Test
    void invalidTokenDoesNotCreateIdentityOnPublicRequest() throws Exception {
        filter.doFilter(request("/posts/7", "invalid-token"), new MockHttpServletResponse(),
                (request, response) -> assertNull(userUtils.getCurrentUserId()));
        verifyNoInteractions(userMapper);
    }

    @Test
    void disabledUserDoesNotCreateIdentityOnPublicRequest() throws Exception {
        user.setStatus(0);
        filter.doFilter(request("/posts/7", token), new MockHttpServletResponse(),
                (request, response) -> assertNull(userUtils.getCurrentUserId()));
    }

    @Test
    void downstreamFailurePropagatesWithoutExecutingRequestTwice() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(ServletException.class, () -> filter.doFilter(
                new MockHttpServletRequest("GET", "/posts/7"), new MockHttpServletResponse(), (request, response) -> {
                    calls.incrementAndGet();
                    throw new ServletException("downstream failure");
                }));
        assertEquals(1, calls.get());
    }

    private MockHttpServletRequest request(String path, String bearer) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("Authorization", "Bearer " + bearer);
        return request;
    }
}
