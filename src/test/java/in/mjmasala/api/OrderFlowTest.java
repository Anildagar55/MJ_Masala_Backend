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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The money path, end to end: cart → order → read back.
 *
 * The assertions that matter are the arithmetic ones. A cart that shows one total and an
 * order that charges another is the single worst bug this service could ship, so the
 * totals are recomputed here from the raw line prices rather than trusted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    /* ------------------------------- helpers ------------------------------- */

    /** Sign-up is register-then-verify now; TestAccounts runs both steps. */
    private String token(String email) throws Exception {
        return TestAccounts.registerAndVerify(mvc, json, "Test Buyer", email);
    }

    private long packId(String slug, String label) throws Exception {
        JsonNode product = json.readTree(mvc.perform(get("/products/" + slug))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        for (JsonNode pack : product.get("packs")) {
            if (pack.get("label").asText().equals(label)) {
                return pack.get("id").asLong();
            }
        }
        throw new IllegalStateException("No pack " + label + " on " + slug);
    }

    private JsonNode addToCart(String token, long packId, int qty) throws Exception {
        MvcResult result = mvc.perform(post("/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemType":"PRODUCT","packId":%d,"quantity":%d}
                                """.formatted(packId, qty)))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static final String ADDRESS = """
            "contactName":"Test Buyer","contactPhone":"9811122233",
            "contactEmail":"buyer@example.com","shipLine":"12 MG Road, Sector 14",
            "shipCity":"Gurugram","shipState":"Haryana","shipPin":"122001"
            """;

    /* -------------------------------- tests -------------------------------- */

    @Test
    @DisplayName("the cart is server-side and totals itself")
    void cartAddsAndTotals() throws Exception {
        String token = token("cart@example.com");
        long pack = packId("turmeric-powder", "500 g");

        JsonNode cart = addToCart(token, pack, 2);
        assertThat(cart.get("itemCount").asInt()).isEqualTo(1);
        assertThat(cart.get("lines").get(0).get("packLabel").asText()).isEqualTo("500 g");
        assertThat(cart.get("lines").get(0).get("quantity").asInt()).isEqualTo(2);

        BigDecimal unit = new BigDecimal(cart.get("lines").get(0).get("unitPrice").asText());
        BigDecimal line = new BigDecimal(cart.get("lines").get(0).get("lineTotal").asText());
        BigDecimal subtotal = new BigDecimal(cart.get("subtotal").asText());

        assertThat(line).isEqualByComparingTo(unit.multiply(BigDecimal.valueOf(2)));
        assertThat(subtotal).isEqualByComparingTo(line);
    }

    @Test
    @DisplayName("adding the same pack twice merges into one line")
    void addingTheSamePackMerges() throws Exception {
        String token = token("merge@example.com");
        long pack = packId("jeera-whole", "200 g");

        addToCart(token, pack, 1);
        JsonNode cart = addToCart(token, pack, 3);

        assertThat(cart.get("itemCount").asInt()).isEqualTo(1);
        assertThat(cart.get("lines").get(0).get("quantity").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("an order is priced by the server, not the request")
    void orderTotalsAreComputedServerSide() throws Exception {
        String token = token("order@example.com");
        long pack = packId("garam-masala", "100 g");
        addToCart(token, pack, 2);

        MvcResult result = mvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {%s,"paymentMethod":"UPI","items":[{"itemType":"PRODUCT","packId":%d,"quantity":2}]}
                                """.formatted(ADDRESS, pack)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode order = json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        BigDecimal subtotal = new BigDecimal(order.get("subtotal").asText());
        BigDecimal shipping = new BigDecimal(order.get("shippingFee").asText());
        BigDecimal total = new BigDecimal(order.get("total").asText());

        assertThat(order.get("reference").asText()).startsWith("MJ-");
        assertThat(subtotal.subtract(BigDecimal.ZERO).add(shipping).add(new BigDecimal(order.get("codFee").asText())))
                .isEqualByComparingTo(total);
        assertThat(order.get("items")).hasSize(1);
    }

    @Test
    @DisplayName("cash on delivery adds its fee to the total — the bug the frontend once had")
    void codFeeIsCharged() throws Exception {
        String token = token("cod@example.com");
        long pack = packId("jeera-whole", "100 g");   // ₹170, well under the free-delivery line

        JsonNode prepaid = placeOrder(token, pack, "UPI", null);
        JsonNode cod = placeOrder(token, pack, "COD", null);

        assertThat(new BigDecimal(prepaid.get("codFee").asText())).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(new BigDecimal(cod.get("codFee").asText())).isEqualByComparingTo(new BigDecimal("40.00"));

        BigDecimal difference = new BigDecimal(cod.get("total").asText())
                .subtract(new BigDecimal(prepaid.get("total").asText()));
        assertThat(difference).isEqualByComparingTo(new BigDecimal("40.00"));
    }

    @Test
    @DisplayName("delivery is free above the threshold and charged below it")
    void shippingThreshold() throws Exception {
        String token = token("ship@example.com");

        JsonNode small = placeOrder(token, packId("jeera-whole", "100 g"), "UPI", null);
        assertThat(new BigDecimal(small.get("shippingFee").asText())).isEqualByComparingTo(new BigDecimal("79.00"));

        JsonNode large = placeOrder(token, packId("turmeric-powder", "1 kg"), "UPI", null);
        assertThat(new BigDecimal(large.get("shippingFee").asText())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("a valid coupon is applied and a bad one is refused loudly")
    void coupons() throws Exception {
        String token = token("coupon@example.com");
        long pack = packId("turmeric-powder", "500 g");

        JsonNode withCoupon = placeOrder(token, pack, "UPI", "MJ10");
        BigDecimal subtotal = new BigDecimal(withCoupon.get("subtotal").asText());
        BigDecimal discount = new BigDecimal(withCoupon.get("discount").asText());
        assertThat(discount).isEqualByComparingTo(subtotal.multiply(new BigDecimal("0.10")).setScale(2, java.math.RoundingMode.HALF_UP));

        mvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {%s,"paymentMethod":"UPI","couponCode":"NOPE",
                                  "items":[{"itemType":"PRODUCT","packId":%d,"quantity":1}]}
                                """.formatted(ADDRESS, pack)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("NOPE")));
    }

    @Test
    @DisplayName("a coupon below its minimum is refused with the shortfall")
    void couponMinimum() throws Exception {
        mvc.perform(post("/coupons/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"MJ15","subtotal":200}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("more to use")));
    }

    @Test
    @DisplayName("an empty cart cannot be ordered")
    void emptyCartIsRejected() throws Exception {
        String token = token("empty@example.com");
        mvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {%s,"paymentMethod":"UPI"}
                                """.formatted(ADDRESS)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a bad pin code and a bad phone are rejected before anything is charged")
    void validationIsEnforced() throws Exception {
        String token = token("validation@example.com");
        long pack = packId("jeera-whole", "100 g");

        mvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contactName":"A","contactPhone":"123","contactEmail":"not-an-email",
                                 "shipLine":"x","shipCity":"y","shipState":"z","shipPin":"12",
                                 "paymentMethod":"UPI","items":[{"itemType":"PRODUCT","packId":%d,"quantity":1}]}
                                """.formatted(pack)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").exists());
    }

    @Test
    @DisplayName("an order is visible to its owner and nobody else")
    void ordersAreScopedToTheOwner() throws Exception {
        String owner = token("owner@example.com");
        String stranger = token("stranger@example.com");

        JsonNode order = placeOrder(owner, packId("jeera-whole", "100 g"), "UPI", null);
        String reference = order.get("reference").asText();

        mvc.perform(get("/orders/" + reference).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(reference));

        // same reference, different account — a 404, not someone else's address
        mvc.perform(get("/orders/" + reference).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the cart and orders need a token")
    void endpointsRequireAuth() throws Exception {
        mvc.perform(get("/cart")).andExpect(status().isUnauthorized());
        mvc.perform(get("/orders/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/wishlist")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("admin routes are closed to ordinary customers")
    void adminRoutesAreProtected() throws Exception {
        String token = token("notadmin@example.com");
        mvc.perform(get("/admin/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/orders")).andExpect(status().isUnauthorized());
    }

    private JsonNode placeOrder(String token, long pack, String method, String coupon) throws Exception {
        String couponPart = coupon == null ? "" : ",\"couponCode\":\"" + coupon + "\"";
        MvcResult result = mvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {%s,"paymentMethod":"%s"%s,
                                  "items":[{"itemType":"PRODUCT","packId":%d,"quantity":1}]}
                                """.formatted(ADDRESS, method, couponPart, pack)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
}
