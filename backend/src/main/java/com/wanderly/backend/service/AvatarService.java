package com.wanderly.backend.service;

import com.wanderly.backend.dto.user.UserResponse;
import com.wanderly.backend.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

@Service
public class AvatarService {

    public static final String AVATAR_URL_PREFIX = "/uploads/avatars/";

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");
    private static final int MAX_DIMENSION = 512;
    private static final float JPEG_QUALITY = 0.8f;

    private final UserService userService;
    private final Path avatarDir;

    public AvatarService(UserService userService, @Value("${app.upload-dir}") String uploadDir) {
        this.userService = userService;
        this.avatarDir = Path.of(uploadDir, "avatars").toAbsolutePath().normalize();
    }

    @Transactional
    public UserResponse uploadAvatar(String email, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please choose an image.");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only JPEG and PNG images are allowed.");
        }

        BufferedImage original;
        try (InputStream in = file.getInputStream()) {
            original = ImageIO.read(in);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The image could not be read.");
        }
        if (original == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The image could not be read.");
        }

        User user = userService.getByEmail(email);
        String fileName = "user-" + user.getPublicId() + "-" + UUID.randomUUID() + ".jpg";
        writeCompressedJpeg(resize(original), avatarDir.resolve(fileName));

        deleteOldAvatar(user.getProfilePicture());
        user.setProfilePicture(AVATAR_URL_PREFIX + fileName);
        return userService.toResponse(user);
    }

    private BufferedImage resize(BufferedImage source) {
        double scale = Math.min(1.0, (double) MAX_DIMENSION / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

        // JPEG has no alpha channel, so transparent PNG areas become white.
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return target;
    }

    private void writeCompressedJpeg(BufferedImage image, Path destination) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(JPEG_QUALITY);

        try {
            Files.createDirectories(destination.getParent());
            try (ImageOutputStream out = ImageIO.createImageOutputStream(destination.toFile())) {
                writer.setOutput(out);
                writer.write(null, new IIOImage(image, null, null), params);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save avatar", e);
        } finally {
            writer.dispose();
        }
    }

    private void deleteOldAvatar(String oldUrl) {
        if (oldUrl == null || !oldUrl.startsWith(AVATAR_URL_PREFIX)) {
            return;
        }
        Path oldFile = avatarDir.resolve(oldUrl.substring(AVATAR_URL_PREFIX.length())).normalize();
        if (!oldFile.startsWith(avatarDir)) {
            return;
        }
        try {
            Files.deleteIfExists(oldFile);
        } catch (IOException ignored) {
            // A leftover file is harmless; the profile already points to the new one.
        }
    }
}
