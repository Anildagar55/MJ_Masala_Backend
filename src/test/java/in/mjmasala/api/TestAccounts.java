package in.mjmasala.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Signing a user up in tests.
 *
 * Sign-up is two steps now — register, then verify the code — so every test that needs a
 * signed-in account goes through here rather than repeating the dance. The code is read
 * from {@code devCode}, which the API returns only while {@code app.otp.expose-code} is on
 * (the test profile).
 */
final class TestAccounts {

    static final String PASSWORD = "supersecret1";

    private TestAccounts() {
    }

    static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** A valid, unique Indian mobile number derived from the clock. */
    static String uniquePhone() {
        long n = System.nanoTime();
        return "9" + String.format("%09d", Math.abs(n % 1_000_000_000L));
    }

    static String uniqueEmail(String prefix) {
        return prefix + System.nanoTime() + "@example.com";
    }

    /** Registers and returns the pending response — pendingToken, masked destinations, devCode. */
    static JsonNode registerPending(MockMvc mvc, ObjectMapper json, String name, String email, String phone)
            throws Exception {
        MvcResult result = mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","email":"%s","phone":"%s","password":"%s"}
                                """.formatted(name, email, phone, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(body(result));
    }

    static JsonNode verify(MockMvc mvc, ObjectMapper json, String pendingToken, String code)
            throws Exception {
        MvcResult result = mvc.perform(post("/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pendingToken":"%s","code":"%s"}
                                """.formatted(pendingToken, code)))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(body(result));
    }

    /**
     * The whole sign-up, ending in a usable access token.
     *
     * Needs {@code app.otp.expose-code} on, because the code comes back in the response.
     * Tests that switch it off — the delivery tests — read the code from the message
     * instead and do the two steps themselves.
     */
    static String registerAndVerify(MockMvc mvc, ObjectMapper json, String name, String email)
            throws Exception {
        JsonNode pending = registerPending(mvc, json, name, email, uniquePhone());
        if (!pending.hasNonNull("devCode")) {
            throw new IllegalStateException(
                    "registerAndVerify needs app.otp.expose-code on — this context has it off, "
                            + "so the code is not in the response. Read it from the delivered "
                            + "message and call verify(...) yourself.");
        }
        JsonNode auth = verify(mvc, json, pending.get("pendingToken").asText(),
                pending.get("devCode").asText());
        return auth.get("accessToken").asText();
    }

    /** The code from a pending response, or null when it is not exposed. */
    static String devCode(JsonNode pending) {
        return pending.hasNonNull("devCode") ? pending.get("devCode").asText() : null;
    }

    /** Convenience for tests that do not care about the address. */
    static String newUserToken(MockMvc mvc, ObjectMapper json, String prefix) throws Exception {
        return registerAndVerify(mvc, json, "Test Buyer", uniqueEmail(prefix));
    }
}
