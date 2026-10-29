package in.mjmasala.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The back office: creating products and combos, changing prices and discounts, working
 * the order and payment panels.
 *
 * The assertions that matter are the ones that check the *shop* after an admin edit — a
 * price change in the panel that does not move the storefront's filters would be worse
 * than not having the panel at all.
 */
@SpringBootTest(properties = {
        // its own database: these tests create products and combos, and the catalogue
        // suite asserts on the pristine seed. Sharing one in-memory database between them
        // would make the catalogue's counts depend on test order.
        "spring.datasource.url=jdbc:h2:mem:mjadmin;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private String adminToken;

    @BeforeEach
    void signInAsAdmin() throws Exception {
        MvcResult result = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@mjmasala.in","password":"ChangeMe@123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        adminToken = json.readTree(body(result)).get("accessToken").asText();
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private JsonNode read(String url) throws Exception {
        return json.readTree(body(mvc.perform(get(url)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()));
    }

    private static final String NEW_PRODUCT = """
            {
              "name": "Kashmiri Red Chilli",
              "hindi": "कश्मीरी मिर्च",
              "kind": "POWDER",
              "categorySlug": "masala",
              "origin": "Kashmir Valley",
              "blurb": "Bright colour, gentle heat.",
              "description": "A mild, deep-red chilli grown in the Kashmir valley.",
              "imageKey": "red-chilli-powder",
              "bestseller": false,
              "badges": ["New"],
              "packs": [
                { "label": "100 g", "weightGrams": 100, "price": 220, "mrp": 280 },
                { "label": "500 g", "weightGrams": 500, "price": 940, "mrp": 1180 }
              ],
              "specs": [ { "label": "Heat", "value": "Mild" } ]
            }
            """;

    /* ----------------------------- products ----------------------------- */

    @Test
    @DisplayName("an admin can create a product and it appears in the shop")
    void createProduct() throws Exception {
        MvcResult created = mvc.perform(post("/admin/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("kashmiri-red-chilli"))
                .andExpect(jsonPath("$.packs.length()").value(2))
                .andExpect(jsonPath("$.specs[0].label").value("Heat"))
                .andReturn();

        JsonNode product = json.readTree(body(created));

        // the derived columns must already be right, or the shop filters would miss it
        assertThat(product.get("fromPrice").asDouble()).isEqualTo(220.0);
        assertThat(product.get("offPct").asInt()).isEqualTo(21);

        // and it is reachable on the public catalogue
        mvc.perform(get("/products/kashmiri-red-chilli"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kashmiri Red Chilli"));
    }

    @Test
    @DisplayName("a duplicate slug is refused rather than silently overwriting")
    void duplicateSlugIsRefused() throws Exception {
        // this test owns its own slug so it cannot be affected by run order
        String unique = NEW_PRODUCT.replace("Kashmiri Red Chilli", "Duplicate Slug Probe");
        mvc.perform(post("/admin/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(unique))
                .andExpect(status().isCreated());

        mvc.perform(post("/admin/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(unique))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("an MRP below the selling price is rejected")
    void mrpBelowPriceIsRejected() throws Exception {
        String bad = NEW_PRODUCT.replace("\"price\": 220, \"mrp\": 280", "\"price\": 300, \"mrp\": 250");

        mvc.perform(post("/admin/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("MRP below its selling price")));
    }

    @Test
    @DisplayName("changing a price moves the shop's price and discount")
    void priceChangeMovesTheShop() throws Exception {
        String slug = "price-move-test";
        String payload = NEW_PRODUCT
                .replace("Kashmiri Red Chilli", "Price Move Test")
                .replace("\"slug\":", "\"ignored\":");

        mvc.perform(post("/admin/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.replace("\"name\": \"Price Move Test\"",
                                "\"name\": \"Price Move Test\", \"slug\": \"" + slug + "\"")))
                .andExpect(status().isCreated());

        // now halve the cheapest pack's price via a full replace
        String updated = payload
                .replace("\"name\": \"Price Move Test\"", "\"name\": \"Price Move Test\"")
                .replace("\"price\": 220", "\"price\": 110");
        mvc.perform(put("/admin/products/" + slug)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updated.replace("\"name\": \"Price Move Test\"",
                                "\"name\": \"Price Move Test\", \"slug\": \"" + slug + "\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromPrice").value(110.00))
                .andExpect(jsonPath("$.offPct").value(61));

        mvc.perform(get("/products/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromPrice").value(110.00));
    }

    @Test
    @DisplayName("the product list includes switched-off products; the shop does not")
    void deactivatedProductsLeaveTheShop() throws Exception {
        mvc.perform(post("/admin/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_PRODUCT.replace("\"name\": \"Kashmiri Red Chilli\"",
                                "\"name\": \"Kashmiri Red Chilli\", \"slug\": \"hide-me\"")))
                .andExpect(status().isCreated());

        mvc.perform(patch("/admin/products/hide-me")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": false}
                                """))
                .andExpect(status().isOk());

        mvc.perform(get("/products/hide-me")).andExpect(status().isNotFound());

        JsonNode adminList = read("/admin/products");
        boolean stillListed = false;
        for (JsonNode p : adminList) {
            if ("hide-me".equals(p.get("slug").asText())) stillListed = true;
        }
        assertThat(stillListed).as("the admin list keeps switched-off products").isTrue();
    }

    /* ------------------------------ combos ------------------------------ */

    @Test
    @DisplayName("an admin can create a combo and change its discount")
    void createAndDiscountCombo() throws Exception {
        String payload = """
                {
                  "name": "Weekend Biryani Box",
                  "blurb": "Everything for a Sunday biryani.",
                  "price": 649,
                  "mrp": 899,
                  "badges": ["New"],
                  "items": [
                    { "productSlug": "turmeric-powder", "packLabel": "100 g", "quantity": 1 },
                    { "productSlug": "garam-masala", "packLabel": "100 g", "quantity": 2 }
                  ],
                  "specs": [ { "label": "Jars", "value": "3" } ]
                }
                """;

        MvcResult created = mvc.perform(post("/admin/combos")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("weekend-biryani-box"))
                .andExpect(jsonPath("$.contents.length()").value(2))
                .andExpect(jsonPath("$.save").value("Save ₹250"))
                .andReturn();

        assertThat(json.readTree(body(created)).get("offPct").asInt()).isEqualTo(28);

        // deepen the discount and confirm the shop shows the new numbers
        mvc.perform(put("/admin/combos/weekend-biryani-box")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.replace("\"price\": 649", "\"price\": 549")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(549.00))
                .andExpect(jsonPath("$.save").value("Save ₹350"))
                .andExpect(jsonPath("$.offPct").value(39));

        mvc.perform(get("/combos/weekend-biryani-box"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(549.00));
    }

    @Test
    @DisplayName("a combo pointing at an unknown product is refused")
    void comboWithUnknownProductIsRefused() throws Exception {
        mvc.perform(post("/admin/combos")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Broken Box", "price": 100, "mrp": 200,
                                  "items": [ { "productSlug": "does-not-exist", "packLabel": "100 g", "quantity": 1 } ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    /* ------------------------- orders and payments ------------------------- */

    @Test
    @DisplayName("the order panel lists orders and can move one along")
    void orderPanel() throws Exception {
        String reference = placeAnOrder();

        JsonNode orders = read("/admin/orders");
        assertThat(orders.size()).isGreaterThan(0);

        mvc.perform(patch("/admin/orders/" + reference)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SHIPPED","paymentStatus":"PAID"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }

    @Test
    @DisplayName("the payments panel totals collected, pending and refunded")
    void paymentsPanel() throws Exception {
        String reference = placeAnOrder();

        mvc.perform(patch("/admin/payments/" + reference)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentStatus":"PAID"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        JsonNode payments = read("/admin/payments");
        JsonNode row = null;
        for (JsonNode p : payments) {
            if (reference.equals(p.get("reference").asText())) row = p;
        }
        assertThat(row).as("the payment row for " + reference).isNotNull();
        assertThat(row.get("status").asText()).isEqualTo("PAID");

        JsonNode summary = read("/admin/payments/summary");
        assertThat(summary.get("collected").asDouble()).isGreaterThan(0);
        assertThat(summary.get("byMethod").has("UPI")).isTrue();

        // a refund moves it out of collected
        mvc.perform(patch("/admin/payments/" + reference)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentStatus":"REFUNDED","note":"Customer cancelled"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
    }

    /* ------------------------------- access ------------------------------- */

    @Test
    @DisplayName("a normal customer cannot reach any of it")
    void customersAreLockedOut() throws Exception {
        String customer = TestAccounts.registerAndVerify(mvc, json, "Nosy",
                TestAccounts.uniqueEmail("nosy"));

        for (String url : new String[]{"/admin/products", "/admin/combos", "/admin/orders",
                "/admin/payments", "/admin/stats", "/admin/coupons", "/admin/enquiries"}) {
            mvc.perform(get(url).header("Authorization", "Bearer " + customer))
                    .andExpect(status().isForbidden());
        }

        mvc.perform(post("/admin/products")
                        .header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT))
                .andExpect(status().isForbidden());
    }

    /* ------------------------------ helpers ------------------------------ */

    /** Places an order as a fresh customer and returns its reference. */
    private String placeAnOrder() throws Exception {
        String email = TestAccounts.uniqueEmail("adminpanel");
        String token = TestAccounts.registerAndVerify(mvc, json, "Panel Buyer", email);

        JsonNode product = json.readTree(body(mvc.perform(get("/products/jeera-whole"))
                .andReturn()));
        long packId = product.get("packs").get(0).get("id").asLong();

        MvcResult order = mvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contactName":"Panel Buyer","contactPhone":"9811122233",
                                 "contactEmail":"%s","shipLine":"12 MG Road","shipCity":"Gurugram",
                                 "shipState":"Haryana","shipPin":"122001","paymentMethod":"UPI",
                                 "items":[{"itemType":"PRODUCT","packId":%d,"quantity":1}]}
                                """.formatted(email, packId)))
                .andExpect(status().isCreated())
                .andReturn();

        return json.readTree(body(order)).get("reference").asText();
    }
}
