package in.mjmasala.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The shop reads, against the seeded catalogue.
 *
 * These assert on the data the frontend ships, which is the point: the seeder loads the
 * same JSON the UI was built from, so if these numbers move, the two have drifted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogueApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    @DisplayName("the catalogue is public and paged")
    void productsArePublic() throws Exception {
        mvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(17))
                .andExpect(jsonPath("$.items[0].slug").isNotEmpty())
                .andExpect(jsonPath("$.items[0].fromPrice").isNumber());
    }

    @Test
    @DisplayName("every card carries a rating derived from its real reviews")
    void cardsCarryDerivedRatings() throws Exception {
        JsonNode page = json.readTree(mvc.perform(get("/products").param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));

        for (JsonNode item : page.get("items")) {
            assertThat(item.get("reviewCount").asInt())
                    .as("review count for " + item.get("slug").asText())
                    .isGreaterThan(0);
            assertThat(item.get("rating").asDouble()).isBetween(1.0, 5.0);
        }
    }

    @Test
    @DisplayName("category filter narrows the list")
    void categoryFilter() throws Exception {
        JsonNode all = page("/products?size=100");
        JsonNode whole = page("/products?category=whole&size=100");

        assertThat(whole.get("totalItems").asInt()).isLessThan(all.get("totalItems").asInt());
        assertThat(whole.get("totalItems").asInt()).isGreaterThan(0);
        for (JsonNode item : whole.get("items")) {
            assertThat(item.get("category").asText()).isEqualTo("whole");
        }
    }

    @Test
    @DisplayName("price, rating and bestseller filters compose")
    void filtersCompose() throws Exception {
        JsonNode cheap = page("/products?maxPrice=200&size=100");
        for (JsonNode item : cheap.get("items")) {
            assertThat(item.get("fromPrice").asDouble()).isLessThanOrEqualTo(200.0);
        }

        JsonNode best = page("/products?bestseller=true&size=100");
        for (JsonNode item : best.get("items")) {
            assertThat(item.get("bestseller").asBoolean()).isTrue();
        }

        JsonNode rated = page("/products?minRating=4.5&size=100");
        for (JsonNode item : rated.get("items")) {
            assertThat(item.get("rating").asDouble()).isGreaterThanOrEqualTo(4.5);
        }
    }

    @Test
    @DisplayName("sorting by price ascends and by discount descends")
    void sorting() throws Exception {
        JsonNode asc = page("/products?sort=price-asc&size=100");
        double previous = 0;
        for (JsonNode item : asc.get("items")) {
            double price = item.get("fromPrice").asDouble();
            assertThat(price).isGreaterThanOrEqualTo(previous);
            previous = price;
        }

        JsonNode off = page("/products?sort=discount&size=100");
        int lastOff = Integer.MAX_VALUE;
        for (JsonNode item : off.get("items")) {
            int pct = item.get("offPct").asInt();
            assertThat(pct).isLessThanOrEqualTo(lastOff);
            lastOff = pct;
        }
    }

    @Test
    @DisplayName("search matches the name")
    void search() throws Exception {
        JsonNode found = page("/products?q=turmeric");
        assertThat(found.get("totalItems").asInt()).isEqualTo(1);
        assertThat(found.get("items").get(0).get("slug").asText()).isEqualTo("turmeric-powder");
    }

    @Test
    @DisplayName("a product detail carries its pack ladder and specs")
    void productDetail() throws Exception {
        mvc.perform(get("/products/turmeric-powder"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Turmeric Powder"))
                .andExpect(jsonPath("$.packs.length()").value(4))
                .andExpect(jsonPath("$.packs[0].label").value("100 g"))
                .andExpect(jsonPath("$.packs[3].label").value("1 kg"))
                .andExpect(jsonPath("$.specs.length()").value(3))
                .andExpect(jsonPath("$.imageKey").value("turmeric-powder"));
    }

    @Test
    @DisplayName("an unknown slug is a 404 with a readable message")
    void unknownSlug() throws Exception {
        mvc.perform(get("/products/not-a-spice"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not-a-spice")));
    }

    @Test
    @DisplayName("combos list with their contents resolved to products")
    void combos() throws Exception {
        JsonNode list = json.readTree(mvc.perform(get("/combos"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(list).hasSize(7);
        JsonNode first = list.get(0);
        assertThat(first.get("contents")).isNotEmpty();
        assertThat(first.get("save").asText()).startsWith("Save ₹");
    }

    @Test
    @DisplayName("the kitchen combo lists all six jars")
    void comboDetail() throws Exception {
        mvc.perform(get("/combos/complete-kitchen-combo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contents.length()").value(6))
                .andExpect(jsonPath("$.price").value(899.00));
    }

    @Test
    @DisplayName("the shelves are exposed for the nav")
    void categories() throws Exception {
        mvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("masala"))
                .andExpect(jsonPath("$[2].slug").value("combos"));
    }

    private JsonNode page(String url) throws Exception {
        return json.readTree(mvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
}
