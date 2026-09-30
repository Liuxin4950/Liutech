package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ContentLinkValidator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContentLinkValidatorTest {
    @Test void linksAcceptTheDocumentedSchemesAndRejectExecutableOrMalformedValues() {
        assertDoesNotThrow(() -> ContentLinkValidator.validateHref("/post/1",false,false));
        assertDoesNotThrow(() -> ContentLinkValidator.validateHref("https://example.com",false,false));
        assertDoesNotThrow(() -> ContentLinkValidator.validateHref("mailto:reader@example.com",false,true));
        assertThrows(BusinessException.class, () -> ContentLinkValidator.validateHref("javascript:alert(1)",false,false));
        assertThrows(BusinessException.class, () -> ContentLinkValidator.validateHref("//example.com",false,false));
        assertThrows(BusinessException.class, () -> ContentLinkValidator.validateHref("/\\example.com",false,false));
        assertThrows(BusinessException.class, () -> ContentLinkValidator.validateHref("mailto:reader@example.com",false,false));
    }
}
