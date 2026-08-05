package chat.liuxin.liutech.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * 图片压缩服务
 * 职责：纯像素处理，不涉及文件系统操作
 * 使用 Java 原生 ImageIO，无需额外依赖
 *
 * @author 刘鑫
 */
@Slf4j
@Service
public class ImageCompressService {

    /** 压缩后最大宽度（像素） */
    private static final int MAX_WIDTH = 1920;

    /** 压缩 JPEG 质量 (0.0 ~ 1.0) */
    private static final float COMPRESS_QUALITY = 0.85f;

    /**
     * 压缩图片
     * 超过最大宽度时等比缩放，PNG/BMP 转 JPEG，GIF 不处理
     *
     * @param originalBytes 原始图片字节
     * @param originalFilename 原始文件名（用于判断格式）
     * @return 压缩后的字节，无需压缩时返回 null
     * @throws IOException IO异常
     */
    public byte[] compress(byte[] originalBytes, String originalFilename) throws IOException {
        String extension = getExtension(originalFilename).toLowerCase();

        // GIF 不处理（会丢失动画）
        if ("gif".equals(extension)) {
            return null;
        }

        BufferedImage original;
        try {
            original = ImageIO.read(new ByteArrayInputStream(originalBytes));
        } catch (IOException e) {
            // JDK ImageIO 无法解码的图片（如 Word 粘贴的 CMYK 色彩空间 JPEG）会抛异常，
            // 降级为原样保存，浏览器仍能正常显示，不阻塞上传
            log.warn("图片解码失败（{}），降级为原样保存: {}", e.getMessage(), originalFilename);
            return null;
        }
        if (original == null) {
            return null;
        }

        // 超过最大宽度时等比缩放
        if (original.getWidth() > MAX_WIDTH) {
            int newHeight = (int) ((long) original.getHeight() * MAX_WIDTH / original.getWidth());
            BufferedImage resized = resizeImage(original, MAX_WIDTH, newHeight);
            byte[] result = encodeJpeg(resized, COMPRESS_QUALITY);
            log.debug("图片压缩: {}x{} -> {}x{}, {}KB -> {}KB",
                    original.getWidth(), original.getHeight(), MAX_WIDTH, newHeight,
                    originalBytes.length / 1024, result.length / 1024);
            return result;
        }

        // PNG/BMP 转 JPEG（这些格式通常比 JPEG 大很多）
        if ("png".equals(extension) || "bmp".equals(extension)) {
            byte[] result = encodeJpeg(original, COMPRESS_QUALITY);
            log.debug("格式转换: {} -> JPEG, {}KB -> {}KB",
                    extension.toUpperCase(), originalBytes.length / 1024, result.length / 1024);
            return result;
        }

        // JPEG/WebP 等已压缩格式，保持原样
        return null;
    }

    /**
     * 等比缩放图片
     */
    private BufferedImage resizeImage(BufferedImage original, int targetWidth, int targetHeight) {
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, targetWidth, targetHeight);
        g.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        g.dispose();
        return resized;
    }

    /**
     * 编码为 JPEG 格式
     */
    private byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        // JPEG 不支持 alpha 通道：带透明的图片（如 RGBA PNG）直接写会抛 "Bogus input colorspace"，
        // 先合成到白底 RGB
        if (image.getColorModel().hasAlpha()) {
            BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.drawImage(image, 0, 0, null);
            g.dispose();
            image = rgb;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("JPEG writer not available");
        }
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            ImageOutputStream ios = new MemoryCacheImageOutputStream(baos);
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
            ios.close();
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
