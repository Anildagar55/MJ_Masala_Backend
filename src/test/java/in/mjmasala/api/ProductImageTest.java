package in.mjmasala.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Product photography: uploading it, refusing what is not an image, serving it, and
 * replacing it.
 *
 * The stored directory is a throwaway under target/, so the test never touches real media
 * and can assert on the files themselves.
 */
@SpringBootTest(properties = {
        "app.media.dir=target/test-media",
        "app.media.max-upload-bytes=1048576",
        "app.media.max-dimension=800"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductImageTest {

    private static final Path MEDIA = Path.of("target/test-media");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private String adminToken() throws Exception {
        MvcResult result = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@mjmasala.in","password":"ChangeMe@123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(TestAccounts.body(result)).get("accessToken").asText();
    }

    /** A real JPEG of the given size — big enough to prove the resize runs. */
    private static byte[] jpeg(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(200, 150, 30));
        g.fillRect(0, 0, width, height);
        g.setColor(Color.WHITE);
        g.drawString("MJ", width / 3, height / 2);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", out);
        return out.toByteArray();
    }

    private JsonNode upload(String slug, byte[] content, String filename, String contentType)
            throws Exception {
        MvcResult result = mvc.perform(multipart("/admin/products/" + slug + "/image")
                        .file(new MockMultipartFile("file", filename, contentType, content))
                        .header("Authorization", "Bearer " + adminToken()))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("an upload is stored, shrunk, and served with a cacheable URL")
    void uploadStoreAndServe() throws Exception {
        byte[] big = jpeg(2000, 1500);   // long edge 2000, limit is 800

        MvcResult result = mvc.perform(multipart("/admin/products/garam-masala/image")
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", big))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").isNotEmpty())
                .andReturn();

        String url = json.readTree(TestAccounts.body(result)).get("imageUrl").asText();
        assertThat(url).startsWith("/api/media/").endsWith(".jpg");

        String filename = url.substring(url.lastIndexOf('/') + 1);
        Path stored = MEDIA.resolve(filename);
        assertThat(Files.exists(stored)).as("the file should be on disk").isTrue();

        // the resize ran: the stored long edge is the configured limit, not 2000
        BufferedImage saved = ImageIO.read(stored.toFile());
        assertThat(Math.max(saved.getWidth(), saved.getHeight())).isEqualTo(800);
        assertThat(Files.size(stored)).as("and it is smaller than the upload").isLessThan(big.length);

        // served publicly, with a long cache
        mvc.perform(get("/media/" + filename))
                .andExpect(status().isOk())
                .andExpect(result1 -> assertThat(result1.getResponse().getContentType())
                        .isEqualTo("image/jpeg"))
                .andExpect(result1 -> assertThat(
                        result1.getResponse().getHeader("Cache-Control")).contains("max-age=31536000"));

        // and the shop sees it
        mvc.perform(get("/products/garam-masala"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(url));
    }

    @Test
    @DisplayName("a file that is not really an image is refused")
    void nonImageIsRefused() throws Exception {
        byte[] notAnImage = "#!/bin/sh\necho hello\n".getBytes(StandardCharsets.UTF_8);

        MvcResult result = mvc.perform(multipart("/admin/products/turmeric-powder/image")
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", notAnImage))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(TestAccounts.body(result)).contains("not a JPEG, PNG or WebP");
    }

    @Test
    @DisplayName("an oversized upload is refused")
    void oversizedIsRefused() throws Exception {
        byte[] huge = new byte[2 * 1024 * 1024];      // the test limit is 1MB
        huge[0] = (byte) 0xFF; huge[1] = (byte) 0xD8; huge[2] = (byte) 0xFF;   // look like a jpeg

        MvcResult result = mvc.perform(multipart("/admin/products/turmeric-powder/image")
                        .file(new MockMultipartFile("file", "big.jpg", "image/jpeg", huge))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(TestAccounts.body(result)).contains("limit is 1MB");
    }

    @Test
    @DisplayName("replacing a photo gives a new URL and removes the old file")
    void replacingRemovesTheOldFile() throws Exception {
        JsonNode first = upload("jeera-whole", jpeg(900, 900), "one.jpg", "image/jpeg");
        String firstUrl = first.get("imageUrl").asText();
        String firstFile = firstUrl.substring(firstUrl.lastIndexOf('/') + 1);

        JsonNode second = upload("jeera-whole", jpeg(900, 900), "two.jpg", "image/jpeg");
        String secondUrl = second.get("imageUrl").asText();

        assertThat(secondUrl)
                .as("a new name is what stops browsers serving the replaced photo from cache")
                .isNotEqualTo(firstUrl);
        assertThat(Files.exists(MEDIA.resolve(firstFile)))
                .as("the replaced file should be gone").isFalse();

        mvc.perform(get("/products/jeera-whole"))
                .andExpect(jsonPath("$.imageUrl").value(secondUrl));
    }

    @Test
    @DisplayName("removing a photo clears the URL and deletes the file")
    void removalClearsEverything() throws Exception {
        JsonNode uploaded = upload("cloves", jpeg(600, 600), "cloves.jpg", "image/jpeg");
        String url = uploaded.get("imageUrl").asText();
        String filename = url.substring(url.lastIndexOf('/') + 1);

        mvc.perform(delete("/admin/products/cloves/image")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());

        assertThat(Files.exists(MEDIA.resolve(filename))).isFalse();
        mvc.perform(get("/media/" + filename)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the media path cannot be walked out of")
    void pathTraversalIsRefused() throws Exception {
        mvc.perform(get("/media/..%2F..%2Fpom.xml")).andExpect(status().isBadRequest());
        mvc.perform(get("/media/..%5C..%5Capplication.yml")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a customer cannot upload or delete photography")
    void customersCannotManageImages() throws Exception {
        String customer = TestAccounts.newUserToken(mvc, json, "imagecustomer");

        mvc.perform(multipart("/admin/products/garlic/image")
                        .file(new MockMultipartFile("file", "x.jpg", "image/jpeg", jpeg(100, 100)))
                        .header("Authorization", "Bearer " + customer))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/admin/products/garam-masala/image")
                        .header("Authorization", "Bearer " + customer))
                .andExpect(status().isForbidden());
    }
}
