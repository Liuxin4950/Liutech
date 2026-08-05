package chat.liuxin.liutech.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * CosStorageProperties.getBaseUrl 测试：默认 COS 域名 / 自定义域名覆盖 / 配置不全
 */
class CosStoragePropertiesTest {

    @Test
    void 默认返回COS默认域名() {
        CosStorageProperties props = new CosStorageProperties();
        props.setRegion("ap-chongqing");
        props.setBucket("liutech-1341692466");
        assertEquals("https://liutech-1341692466.cos.ap-chongqing.myqcloud.com", props.getBaseUrl());
    }

    @Test
    void 配置自定义域名时优先使用() {
        CosStorageProperties props = new CosStorageProperties();
        props.setRegion("ap-chongqing");
        props.setBucket("liutech-1341692466");
        props.setBaseUrl("https://static.liuxin.chat");
        assertEquals("https://static.liuxin.chat", props.getBaseUrl());
    }

    @Test
    void 配置不全时返回null() {
        CosStorageProperties props = new CosStorageProperties();
        assertNull(props.getBaseUrl());
    }
}
