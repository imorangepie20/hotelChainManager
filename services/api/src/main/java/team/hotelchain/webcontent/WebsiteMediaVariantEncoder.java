package team.hotelchain.webcontent;

import com.luciad.imageio.webp.WebPWriteParam;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

@Component
public final class WebsiteMediaVariantEncoder {
    public Result encode(Path source, Path temporaryTarget, int targetWidth) throws IOException {
        Path normalizedSource = source.toAbsolutePath().normalize();
        Path normalizedTarget = temporaryTarget.toAbsolutePath().normalize();
        if (normalizedSource.equals(normalizedTarget)) {
            throw new IOException("원본 이미지와 variant 출력 경로는 달라야 합니다.");
        }

        BufferedImage original = ImageIO.read(normalizedSource.toFile());
        if (original == null) throw new IOException("원본 이미지를 읽을 수 없습니다.");
        if (targetWidth <= 0 || targetWidth > original.getWidth()) {
            throw new IOException("variant 폭은 원본 이미지 폭 이하여야 합니다.");
        }

        int targetHeight = Math.max(1, (int) Math.round(
                (double) original.getHeight() * targetWidth / original.getWidth()));
        BufferedImage resized = resize(original, targetWidth, targetHeight);
        ImageWriter writer = webpWriter();
        try {
            Files.createDirectories(normalizedTarget.getParent());
            WebPWriteParam parameters = new WebPWriteParam(writer.getLocale());
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionType(parameters.getCompressionTypes()[WebPWriteParam.LOSSY_COMPRESSION]);
            parameters.setCompressionQuality(0.82f);
            try (ImageOutputStream output = ImageIO.createImageOutputStream(normalizedTarget.toFile())) {
                if (output == null) throw new IOException("WebP 출력 스트림을 열 수 없습니다.");
                writer.setOutput(output);
                writer.write(null, new IIOImage(resized, null, null), parameters);
            }
        } finally {
            writer.dispose();
        }

        BufferedImage decoded = ImageIO.read(normalizedTarget.toFile());
        if (decoded == null || decoded.getWidth() != targetWidth || decoded.getHeight() != targetHeight) {
            throw new IOException("생성된 WebP 이미지의 크기를 검증할 수 없습니다.");
        }
        return new Result("image/webp", Files.size(normalizedTarget), targetWidth, targetHeight);
    }

    private BufferedImage resize(BufferedImage source, int width, int height) {
        int imageType = source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(width, height, imageType);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private ImageWriter webpWriter() throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
        if (!writers.hasNext()) throw new IOException("WebP writer를 찾을 수 없습니다.");
        return writers.next();
    }

    public record Result(String mimeType, long byteSize, int width, int height) {}
}
