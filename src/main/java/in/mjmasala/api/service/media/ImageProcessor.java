package in.mjmasala.api.service.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

/**
 * Checks that an upload really is an image, and shrinks it for the web.
 *
 * Validation reads the file's own bytes rather than trusting the {@code Content-Type}
 * header, which a client controls. A renamed executable or an SVG carrying script is
 * rejected at the door.
 *
 * Resizing uses the JDK's own image support, so there is no image library to add. It is
 * attempted defensively: on a runtime without AWT the original is stored unchanged rather
 * than the upload failing. A big photo is a performance problem; a failed upload is a
 * broken admin panel.
 */
@Component
public class ImageProcessor {

    private static final Logger log = LoggerFactory.getLogger(ImageProcessor.class);

    /** What the bytes actually are, independent of what the client claimed. */
    public record Detected(String format, String extension, String contentType) {
    }

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    /**
     * @return the real format, or empty when the bytes are not an image we accept
     */
    public Optional<Detected> detect(byte[] bytes) {
        if (startsWith(bytes, JPEG_MAGIC)) {
            return Optional.of(new Detected("jpeg", "jpg", "image/jpeg"));
        }
        if (startsWith(bytes, PNG_MAGIC)) {
            return Optional.of(new Detected("png", "png", "image/png"));
        }
        // WEBP: "RIFF" .... "WEBP"
        if (bytes.length > 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return Optional.of(new Detected("webp", "webp", "image/webp"));
        }
        return Optional.empty();
    }

    /**
     * Scales an image down so its long edge is at most {@code maxDimension}.
     *
     * @return the resized bytes, or the original when it is already small enough or when
     *         this runtime cannot process images
     */
    public byte[] shrink(byte[] bytes, String format, int maxDimension) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
            if (source == null) return bytes;

            int longEdge = Math.max(source.getWidth(), source.getHeight());
            if (longEdge <= maxDimension) return bytes;

            double scale = (double) maxDimension / longEdge;
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

            // TYPE_INT_RGB for jpeg (no alpha channel to encode), ARGB otherwise
            int type = "jpeg".equals(format) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
            BufferedImage scaled = new BufferedImage(width, height, type);

            Graphics2D g = scaled.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(source, 0, 0, width, height, null);
            } finally {
                g.dispose();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(scaled, format, out)) return bytes;

            byte[] result = out.toByteArray();
            log.info("Shrank an upload from {}KB to {}KB ({}x{})",
                    bytes.length / 1024, result.length / 1024, width, height);
            return result;
        } catch (Throwable t) {
            // HeadlessException, NoClassDefFoundError on a JRE without AWT, a corrupt
            // image — none of these should stop the upload
            log.warn("Could not resize an upload, storing it as-is: {}", t.toString());
            return bytes;
        }
    }

    private static boolean startsWith(byte[] bytes, byte[] magic) {
        if (bytes.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (bytes[i] != magic[i]) return false;
        }
        return true;
    }
}
