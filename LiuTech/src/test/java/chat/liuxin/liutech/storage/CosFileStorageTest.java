package chat.liuxin.liutech.storage;

import chat.liuxin.liutech.config.CosStorageProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CosFileStorage 测试：逻辑路径生成（与 LocalFileStorage 一致）、URL 生成、COS 调用委托
 */
class CosFileStorageTest {

    private CosStorageProperties props;
    private COSClient cosClient;
    private CosFileStorage storage;

    @BeforeEach
    void setUp() {
        props = new CosStorageProperties();
        props.setSecretId("test-secret-id");
        props.setSecretKey("test-secret-key");
        props.setRegion("ap-chongqing");
        props.setBucket("liutech-1341692466");
        cosClient = mock(COSClient.class);
        storage = new CosFileStorage(props);
        // 注入 mock，跳过 @PostConstruct 的真实客户端创建
        ReflectionTestUtils.setField(storage, "cosClient", cosClient);
    }

    @Test
    void save_生成逻辑路径并上传() throws IOException {
        String relativePath = storage.save(new byte[]{1, 2, 3}, "images", "a.png");

        // 目录结构 <subPath>/yyyy/MM/dd/<timestamp>_<uuid>.<ext>，与 LocalFileStorage 一致
        String[] parts = relativePath.split("/");
        assertEquals(5, parts.length);
        assertEquals("images", parts[0]);
        assertTrue(parts[4].matches("\\d{14}_[0-9a-f]{32}\\.png"));

        verify(cosClient).putObject(eq("liutech-1341692466"), eq(relativePath),
                any(ByteArrayInputStream.class), any());
    }

    @Test
    void generateUrl_返回COS默认域名完整URL() {
        assertEquals("https://liutech-1341692466.cos.ap-chongqing.myqcloud.com/images/2026/08/05/x.jpg",
                storage.generateUrl("images/2026/08/05/x.jpg"));
    }

    @Test
    void generateDownloadUrl_生成带过期时间和下载响应头的预签名URL() throws Exception {
        when(cosClient.generatePresignedUrl(any(GeneratePresignedUrlRequest.class)))
                .thenReturn(new URL("https://liutech-1341692466.cos.ap-chongqing.myqcloud.com/resources/a.zip?sign=abc"));

        String url = storage.generateDownloadUrl("resources/a.zip", "a.zip", 600);

        assertEquals("https://liutech-1341692466.cos.ap-chongqing.myqcloud.com/resources/a.zip?sign=abc", url);

        ArgumentCaptor<GeneratePresignedUrlRequest> captor = ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
        verify(cosClient).generatePresignedUrl(captor.capture());
        GeneratePresignedUrlRequest request = captor.getValue();
        assertEquals("liutech-1341692466", request.getBucketName());
        assertEquals("resources/a.zip", request.getKey());
        assertEquals(HttpMethodName.GET, request.getMethod());
        assertNotNull(request.getResponseHeaders());
        assertTrue(request.getResponseHeaders().getContentDisposition().contains("attachment"));
        assertTrue(request.getExpiration().getTime() > System.currentTimeMillis());
    }

    @Test
    void delete_委托COS删除() {
        storage.delete("images/2026/08/05/x.jpg");
        verify(cosClient).deleteObject("liutech-1341692466", "images/2026/08/05/x.jpg");
    }

    @Test
    void exists_委托COS判断() {
        when(cosClient.doesObjectExist("liutech-1341692466", "images/a.jpg")).thenReturn(true);
        assertTrue(storage.exists("images/a.jpg"));
        assertFalse(storage.exists("images/b.jpg"));
    }
}
