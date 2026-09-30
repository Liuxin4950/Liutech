package chat.liuxin.liutech.common;

import java.net.URI;
import java.util.Locale;

/** 网站内容链接的统一规则，供关于页与轮播复用。 */
public final class ContentLinkValidator {
    private ContentLinkValidator() {}

    public static void validateHref(String href, boolean allowBlank, boolean allowMailto) {
        String value = href == null ? null : href.trim();
        if (value == null || value.isEmpty()) {
            if (allowBlank) return;
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "链接地址不能为空");
        }
        if (!value.contains("\\") && value.chars().noneMatch(Character::isISOControl)) {
            if (value.startsWith("/") && !value.startsWith("//")) return;
            try {
                URI uri = URI.create(value);
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                boolean http = ("http".equals(scheme) || "https".equals(scheme)) && uri.getHost() != null;
                boolean mailto = allowMailto && "mailto".equals(scheme) && !uri.getSchemeSpecificPart().isBlank();
                if (http || mailto) return;
            } catch (IllegalArgumentException ignored) { }
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "链接地址仅支持" + (allowMailto ? "站内路径、HTTP(S) 或 mailto" : "站内路径或 HTTP(S)"));
    }
}
