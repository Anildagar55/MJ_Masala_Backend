package in.mjmasala.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Writing reviews and the effect on the product's rating.
 *
 * The rule under test is that the shop's rating filter and the reviews on the page can
 * never disagree: every write recomputes the product's average and count from the rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    /** Sign-up is register-then-verify now; TestAccounts runs both steps. */
    private String token(String email) throws Exception {
        return TestAccounts.registerAndVerify(mvc, json, "Ritika Sharma", email);
    }

    private long productId(String slug) throws Exception {
        JsonNode product = json.readTree(mvc.perform(get("/products/" + slug))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        return product.get("id").asLong();
    }

    private JsonNode summary(long productId) throws Exception {
        return json.readTree(mvc.perform(get("/products/" + productId + "/reviews/summary"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private BigDecimal cardRating(String slug) throws Exception {
        JsonNode product = json.readTree(mvc.perform(get("/products/" + slug))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        return new BigDecimal(product.get("rating").asText());
    }

    @Test
    @DisplayName("the summary always carries all five star buckets")
    void summaryHasFiveBuckets() throws Exception {
        JsonNode summary = summary(productId("turmeric-powder"));

        assertThat(summary.get("total").asInt()).isGreaterThan(0);
        assertThat(summary.get("distribution").size()).isEqualTo(5);
        int summed = 0;
        for (JsonNode bucket : summary.get("distribution")) {
            summed += bucket.asInt();
        }
        assertThat(summed).isEqualTo(summary.get("total").asInt());
    }

    @Test
    @DisplayName("writing a review bumps the product's count and recalculates its average")
    void writingAReviewUpdatesTheProduct() throws Exception {
        String slug = "mustard-seeds";
        long id = productId(slug);
        String token = token("reviewer@example.com");

        int before = summary(id).get("total").asInt();
        BigDecimal ratingBefore = cardRating(slug);

        mvc.perform(post("/products/" + id + "/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":1,"title":"Not for me","body":"Honestly the seeds were fine but I wanted a finer grind.","city":"Gurugram"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.authorName").value("Ritika Sharma"));

        assertThat(summary(id).get("total").asInt()).isEqualTo(before + 1);
        // a one-star review must pull the average down — proving it is recomputed, not cached
        assertThat(cardRating(slug)).isLessThan(ratingBefore);
    }

    @Test
    @DisplayName("one account may review a product only once")
    void oneReviewPerAccount() throws Exception {
        long id = productId("bay-leaf");
        String token = token("once@example.com");
        String body = """
                {"rating":5,"title":"Great","body":"Good quality leaf, strong aroma.","city":"Pune"}
                """;

        mvc.perform(post("/products/" + id + "/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mvc.perform(post("/products/" + id + "/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already reviewed")));
    }

    @Test
    @DisplayName("writing a review needs a token")
    void reviewWriteNeedsAuth() throws Exception {
        mvc.perform(post("/products/" + productId("cloves") + "/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":5,"body":"Anonymous attempt"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a rating outside 1–5 is refused with a field message")
    void ratingIsValidated() throws Exception {
        String token = token("badrating@example.com");
        mvc.perform(post("/products/" + productId("cloves") + "/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":9,"body":"Impossible rating"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.rating").exists());
    }

    @Test
    @DisplayName("marking helpful twice counts once")
    void helpfulIsIdempotent() throws Exception {
        long id = productId("star-anise");
        String author = token("helpfulauthor@example.com");
        String voter = token("helpfulvoter@example.com");

        MvcResult created = mvc.perform(post("/products/" + id + "/reviews")
                        .header("Authorization", "Bearer " + author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":5,"title":"Lovely","body":"Whole star anise, strong and fresh.","city":"Kochi"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long reviewId = json.readTree(created.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("id").asLong();

        int first = helpful(voter, reviewId);
        int second = helpful(voter, reviewId);

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(1);
    }

    @Test
    @DisplayName("a review can be deleted by its author and not by anyone else")
    void deleteIsScopedToTheAuthor() throws Exception {
        long id = productId("fenugreek-seeds");
        String author = token("author@example.com");
        String stranger = token("stranger-review@example.com");

        MvcResult created = mvc.perform(post("/products/" + id + "/reviews")
                        .header("Authorization", "Bearer " + author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":4,"title":"Good","body":"Fresh and aromatic, will reorder.","city":"Jaipur"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long reviewId = json.readTree(created.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("id").asLong();

        mvc.perform(delete("/reviews/" + reviewId).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/reviews/" + reviewId).header("Authorization", "Bearer " + author))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("reviews sort by newest, most helpful and rating")
    void sorting() throws Exception {
        long id = productId("garam-masala");

        JsonNode byHelpful = list(id, "helpful");
        int last = Integer.MAX_VALUE;
        for (JsonNode review : byHelpful.get("items")) {
            int helpful = review.get("helpfulCount").asInt();
            assertThat(helpful).isLessThanOrEqualTo(last);
            last = helpful;
        }

        JsonNode lowest = list(id, "lowest");
        int previous = 0;
        for (JsonNode review : lowest.get("items")) {
            int rating = review.get("rating").asInt();
            assertThat(rating).isGreaterThanOrEqualTo(previous);
            previous = rating;
        }
    }

    private JsonNode list(long productId, String sort) throws Exception {
        return json.readTree(mvc.perform(get("/products/" + productId + "/reviews")
                        .param("sort", sort).param("size", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private int helpful(String token, long reviewId) throws Exception {
        MvcResult result = mvc.perform(post("/reviews/" + reviewId + "/helpful")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("helpfulCount").asInt();
    }
}
