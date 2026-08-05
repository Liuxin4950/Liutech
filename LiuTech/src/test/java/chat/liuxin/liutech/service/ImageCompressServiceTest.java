package chat.liuxin.liutech.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 图片压缩服务测试
 * 重点验证：JDK ImageIO 无法解码的图片（如 CMYK 色彩空间 JPEG）
 * 必须降级为原样保存，而不是抛异常导致上传失败
 */
class ImageCompressServiceTest {

    private final ImageCompressService service = new ImageCompressService();

    @Test
    void compress_解码失败时降级为null而非抛异常() throws IOException {
        // 模拟 JPEG 解码器抛 IIOException 的字节（真实 CMYK JPEG 抛 "Bogus input colorspace"，
        // 异常类型同为 IIOException）：生成正常 JPEG 后篡改 SOF 段分量数
        BufferedImage base = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        ImageIO.write(base, "jpg", tmp);
        byte[] broken = corruptJpegSof(tmp.toByteArray());

        // 前置验证：该字节确实让 ImageIO 抛 IIOException
        try {
            ImageIO.read(new ByteArrayInputStream(broken));
            throw new IllegalStateException("前置条件失败：篡改后的 JPEG 竟然可以正常解码");
        } catch (javax.imageio.IIOException expected) {
            // 预期：解码器抛 IIOException
        }

        byte[] result = service.compress(broken, "blobid0.png");

        // 关键断言：不抛异常，返回 null 表示原样保存
        assertNull(result, "无法解码的图片应降级为 null（原样保存），而不是抛异常");
    }

    /** 篡改 JPEG SOF 段：把分量数改为 4，使解码器抛出 IIOException */
    private static byte[] corruptJpegSof(byte[] jpeg) {
        byte[] patched = jpeg.clone();
        int pos = 2;
        while (pos < patched.length - 4) {
            if ((patched[pos] & 0xFF) != 0xFF) { pos++; continue; }
            int marker = patched[pos + 1] & 0xFF;
            if (marker == 0xC0 || marker == 0xC2) {
                // 分量数在 pos+9（精度1 + 高2 + 宽2 之后）
                patched[pos + 9] = 4;
                break;
            }
            if (marker == 0xDA) break;
            int segLen = ((patched[pos + 2] & 0xFF) << 8) | (patched[pos + 3] & 0xFF);
            pos += 2 + segLen;
        }
        return patched;
    }

    @Test
    void compress_正常PNG转JPEG() throws IOException {
        BufferedImage image = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);

        byte[] result = service.compress(baos.toByteArray(), "blobid0.png");

        assertNotNull(result, "PNG 应被转换为 JPEG");
        BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(result));
        assertNotNull(decoded, "压缩结果应能被正常解码");
    }

    @Test
    void compress_RGBA透明PNG转JPEG() throws IOException {
        // 回归测试：带 alpha 通道的 PNG（Word 粘贴常见）直接写 JPEG 会抛 "Bogus input colorspace"
        BufferedImage rgba = new BufferedImage(50, 40, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = rgba.createGraphics();
        g.setColor(new java.awt.Color(255, 0, 0, 128));
        g.fillRect(0, 0, 50, 40);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(rgba, "png", baos);

        byte[] result = service.compress(baos.toByteArray(), "blobid0.png");

        assertNotNull(result, "RGBA PNG 应成功转为 JPEG（透明合成白底），而不是抛异常");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result));
        assertNotNull(decoded, "转换结果应能被正常解码");
    }

    @Test
    void compress_GIF原样返回null() throws IOException {
        try (InputStream is = ImageCompressServiceTest.class.getResourceAsStream("/test.gif")) {
            // 资源不存在时跳过（不构造 GIF 就跳过）
            if (is == null) {
                return;
            }
            byte[] gifBytes = is.readAllBytes();
            byte[] result = service.compress(gifBytes, "test.gif");
            assertNull(result, "GIF 不应被压缩处理");
        }
    }
}
