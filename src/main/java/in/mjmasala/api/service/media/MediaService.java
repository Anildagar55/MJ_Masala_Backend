package in.mjmasala.api.service.media;

import in.mjmasala.api.config.AppProperties;
import in.mjmasala.api.exception.BadRequestException;
import in.mjmasala.api.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Product photography: validating it, storing it, and naming it.
 *
 * Three decisions worth stating, because they are the ones that bite later:
 *
 * 1. **The name is generated, never taken from the upload.** A client-supplied filename is
 *    a path-traversal and an overwrite waiting to happen, and it leaks whatever the
 *    photographer's camera called the file.
 * 2. **Every upload gets a new name.** Reusing the name would mean browsers and CDNs keep
 *    serving the old photo after a replacement — the single most confusing bug in an
 *    image pipeline. A fresh name is its own cache-buster.
 * 3. **The bytes are inspected, not the header.** {@code Content-Type} is whatever the
 *    client typed; the magic bytes are what the file actually is.
 */
@Service
public class MediaService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MediaStorage storage;
    private final ImageProcessor images;
    private final AppProperties.Media config;

    public MediaService(MediaStorage storage, ImageProcessor images, AppProperties properties) {
        this.storage = storage;
        this.images = images;
        this.config = properties.media();
    }

    /** The URL a browser should fetch for a stored file. */
    public String publicUrl(String filename) {
        if (filename == null || filename.isBlank()) return null;
        String base = config.publicPath() == null ? "/media" : config.publicPath();
        return (base.endsWith("/") ? base : base + "/") + filename;
    }

    /**
     * Validates, shrinks and stores an upload for a catalogue item.
     *
     * @param slug  used only to make the generated name readable
     * @param previous the filename being replaced, deleted once the new one is safely stored
     * @return the new filename
     */
    public String store(String slug, MultipartFile file, String previous) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Pick an image to upload");
        }
        if (file.getSize() > config.maxUploadBytes()) {
            throw new BadRequestException("That image is %.1fMB — the limit is %dMB"
                    .formatted(file.getSize() / 1_048_576.0, config.maxUploadBytes() / 1_048_576));
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("That upload could not be read");
        }

        ImageProcessor.Detected detected = images.detect(bytes)
                .orElseThrow(() -> new BadRequestException(
                        "That file is not a JPEG, PNG or WebP image"));

        byte[] stored = images.shrink(bytes, detected.format(), config.maxDimension());
        String filename = nameFor(slug, detected.extension());

        storage.store(filename, stored);

        // only now is it safe to drop the previous one — the new file is already in place
        if (previous != null && !previous.isBlank() && !previous.equals(filename)) {
            storage.delete(previous);
        }
        return filename;
    }

    public byte[] read(String filename) {
        return storage.read(filename)
                .orElseThrow(() -> new NotFoundException("No such image"));
    }

    public void delete(String filename) {
        storage.delete(filename);
    }

    /** Slug plus a short random suffix — readable in a directory listing, unique per upload. */
    private String nameFor(String slug, String extension) {
        byte[] salt = new byte[4];
        RANDOM.nextBytes(salt);
        String safe = slug == null ? "image" : slug.replaceAll("[^a-zA-Z0-9-]", "-");
        return "%s-%s.%s".formatted(safe, HexFormat.of().formatHex(salt), extension);
    }
}
