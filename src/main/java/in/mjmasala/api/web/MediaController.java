package in.mjmasala.api.web;

import in.mjmasala.api.service.media.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Locale;

/**
 * Serves uploaded product photography. Public — a shop's images are not a secret, and
 * putting them behind auth would stop the browser from caching them properly.
 *
 * The cache header is a year and immutable, which is safe because every upload gets a new
 * filename: a replaced photo is a different URL, so nothing stale can be served.
 */
@RestController
@RequestMapping("/media")
@Tag(name = "Media", description = "Uploaded product photography")
public class MediaController {

    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    @GetMapping("/{filename}")
    @Operation(summary = "Fetch an uploaded image")
    public ResponseEntity<byte[]> serve(@PathVariable String filename) {
        // names are generated, but a path segment should never reach the storage unchecked
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return ResponseEntity.badRequest().build();
        }

        byte[] content = media.read(filename);

        return ResponseEntity.ok()
                .contentType(mediaTypeFor(filename))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(content);
    }

    private MediaType mediaTypeFor(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }
}
