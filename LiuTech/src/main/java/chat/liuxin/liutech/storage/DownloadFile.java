package chat.liuxin.liutech.storage;

import java.io.InputStream;

/** 已验证可下载的文件；调用方负责消费并关闭流。 */
public record DownloadFile(String fileName, long contentLength, InputStream stream) {}
