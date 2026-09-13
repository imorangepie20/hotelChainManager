package team.hotelchain.webcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebsiteMediaVariantEncoderTest {
    @TempDir Path tempDir;

    @Test
    void writesAReadableWebpAtTheRequestedWidthWithoutChangingTheOriginal() throws Exception {
        assertThat(ImageIO.getImageWritersByMIMEType("image/webp").hasNext())
                .as("WebP writer must be available in production").isTrue();
        Path source = tempDir.resolve("source.png");
        ImageIO.write(new BufferedImage(1600, 900, BufferedImage.TYPE_INT_RGB), "png", source.toFile());
        byte[] before = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source));
        Path target = tempDir.resolve("result.webp.tmp");
        var result = new WebsiteMediaVariantEncoder().encode(source, target, 640);
        assertThat(result.mimeType()).isEqualTo("image/webp");
        assertThat(result.width()).isEqualTo(640);
        assertThat(result.height()).isEqualTo(360);
        assertThat(result.byteSize()).isEqualTo(Files.size(target));
        assertThat(ImageIO.read(target.toFile()).getWidth()).isEqualTo(640);
        byte[] bytes = Files.readAllBytes(target);
        assertThat(new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("RIFF");
        assertThat(new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("WEBP");
        assertThat(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))).isEqualTo(before);
    }

    @Test
    void refusesToUpscaleOrOverwriteTheOriginal() throws IOException {
        Path source = tempDir.resolve("source.png");
        ImageIO.write(new BufferedImage(500, 281, BufferedImage.TYPE_INT_RGB), "png", source.toFile());
        var encoder = new WebsiteMediaVariantEncoder();
        assertThatThrownBy(() -> encoder.encode(source, tempDir.resolve("large.webp"), 640)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> encoder.encode(source, source, 320)).isInstanceOf(IOException.class);
    }
}
