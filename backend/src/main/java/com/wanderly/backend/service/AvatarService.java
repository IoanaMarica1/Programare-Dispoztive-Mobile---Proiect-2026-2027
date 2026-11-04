package com.wanderly.backend.service;

import com.wanderly.backend.dto.user.UserResponse;
import com.wanderly.backend.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AvatarService {

    public static final String AVATAR_URL_PREFIX = "/uploads/avatars/";

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");
    private static final Set<String> ALLOWED_FORMATS = Set.of("jpeg", "jpg", "png");
    private static final int MAX_DIMENSION = 512;
    /**
     * A 5 MB file can still declare e.g. 30000 x 30000 pixels (a "decompression bomb"); decoding that
     * would need gigabytes of memory. Bigger images are refused, smaller ones are decoded subsampled.
     */
    static final long MAX_PIXELS = 100_000_000L;
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
            throw badRequest("Please choose an image.");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw badRequest("Only JPEG and PNG images are allowed.");
        }
        BufferedImage original = read(file);

        User user = userService.getByEmail(email);
        String fileName = "user-" + user.getPublicId() + "-" + UUID.randomUUID() + ".jpg";
        Path newFile = avatarDir.resolve(fileName);
        writeCompressedJpeg(resize(original), newFile);

        String oldUrl = user.getProfilePicture();
        user.setProfilePicture(AVATAR_URL_PREFIX + fileName);
        cleanUpAfterCommit(oldUrl, newFile);
        return userService.toResponse(user);
    }

    /**
     * Decodes the image only after checking what it really is (not what the client claims) and how
     * big it is, reading every n-th pixel so the decoded image stays around twice the avatar size.
     */
    static BufferedImage read(MultipartFile file) {
        try (InputStream in = file.getInputStream(); ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            Iterator<ImageReader> readers = iis == null ? null : ImageIO.getImageReaders(iis);
            if (readers == null || !readers.hasNext()) throw badRequest("The image could not be read.");
            ImageReader reader = readers.next();
            try {
                if (!ALLOWED_FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
                    throw badRequest("Only JPEG and PNG images are allowed.");
                }
                reader.setInput(iis, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw badRequest("The image is too large. Please choose a smaller photo.");
                }
                ImageReadParam param = reader.getDefaultReadParam();
                int step = Math.max(1, Math.max(width, height) / (MAX_DIMENSION * 2));
                param.setSourceSubsampling(step, step, 0, 0);
                BufferedImage image = reader.read(0, param);
                if (image == null) throw badRequest("The image could not be read.");
                return image;
            } finally {
                reader.dispose();
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // Corrupt files make the decoders throw all sorts of exceptions.
            throw badRequest("The image could not be read.");
        }
    }

    /**
     * Files are not part of the transaction: delete the previous avatar only once the new one is
     * saved in the profile, and remove the new file if saving the profile fails.
     */
    private void cleanUpAfterCommit(String oldUrl, Path newFile) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteOldAvatar(oldUrl);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    deleteOldAvatar(oldUrl);
                } else {
                    deleteQuietly(newFile);
                }
            }
        });
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
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
            deleteQuietly(destination);
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
        if (oldFile.startsWith(avatarDir)) {
            deleteQuietly(oldFile);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // A leftover file is harmless.
        }
    }
}
