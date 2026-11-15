package com.wanderly.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarServiceTest {

    private static MockMultipartFile file(byte[] bytes, String type) {
        return new MockMultipartFile("file", "avatar", type, bytes);
    }

    /** A PNG header that claims the given size, with no real pixel data behind it. */
    private static byte[] pngHeader(int width, int height) {
        ByteBuffer ihdr = ByteBuffer.allocate(17);
        ihdr.put("IHDR".getBytes(StandardCharsets.US_ASCII)).putInt(width).putInt(height)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        CRC32 crc = new CRC32();
        crc.update(ihdr.array());
        return ByteBuffer.allocate(8 + 4 + 17 + 4)
                .put(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'})
                .putInt(13).put(ihdr.array()).putInt((int) crc.getValue())
                .array();
    }

    @Test
    @DisplayName("PNG mic ca fișier dar declarat 30000x30000 → refuzat înainte de decodare")
    void read_decompressionBomb_isRejected() {
        assertThatThrownBy(() -> AvatarService.read(file(pngHeader(30_000, 30_000), "image/png")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getReason()).contains("too large"));
    }

    @Test
    @DisplayName("Fișier text trimis ca image/png → refuzat, conținutul contează, nu header-ul")
    void read_notAnImage_isRejected() {
        assertThatThrownBy(() -> AvatarService.read(file("hello".getBytes(), "image/png")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getReason()).isEqualTo("The image could not be read."));
    }

    @Test
    @DisplayName("Imagine mare dar validă (3000x1500) → decodată redusă, nu la rezoluția întreagă")
    void read_largeImage_isSubsampled() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(3000, 1500, BufferedImage.TYPE_INT_RGB), "png", out);

        BufferedImage image = AvatarService.read(file(out.toByteArray(), "image/png"));

        assertThat(image.getWidth()).isLessThanOrEqualTo(1500);
        assertThat(image.getWidth() * 2).isEqualTo(image.getHeight() * 4);
    }
}
