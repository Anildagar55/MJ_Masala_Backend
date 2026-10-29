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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The account lifecycle: signing up, proving the contact details, signing in, and getting
 * back in after a forgotten password.
 *
 * The property that runs through all of it is that **an account is inert until the code
 * comes back**. Registering does not hand out a token; only verifying does. If that ever
 * stops being true the OTP is decoration, so it is asserted directly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Posts JSON and parses the reply. Named apart from the static import of the same name. */
    private JsonNode postJson(String url, String payload) throws Exception {
        return json.readTree(body(mvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andReturn()));
    }

    private JsonNode register(String email, String phone) throws Exception {
        return postJson("/auth/register", """
                {"name":"Anil Dagar","email":"%s","phone":"%s","password":"supersecret1"}
                """.formatted(email, phone));
    }

    /* ------------------------------ sign up ------------------------------ */

    @Test
    @DisplayName("registering does NOT hand out a token — only the code step does")
    void registerOnlySendsACode() throws Exception {
        String email = TestAccounts.uniqueEmail("register");

        MvcResult result = mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Anil Dagar","email":"%s","phone":"%s","password":"supersecret1"}
                                """.formatted(email, TestAccounts.uniquePhone())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pendingToken").isNotEmpty())
                .andExpect(jsonPath("$.codeLength").value(6))
                // the destinations come back masked, never in full
                .andExpect(jsonPath("$.email").value(org.hamcrest.Matchers.containsString("***")))
                .andReturn();

        JsonNode pending = json.readTree(body(result));
        assertThat(pending.has("accessToken"))
                .as("an unverified account must not receive a session")
                .isFalse();

        // and it cannot sign in yet
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersecret1"}
                                """.formatted(email)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("not verified")));
    }

    @Test
    @DisplayName("verifying the code switches the account on and signs the user in")
    void verifyActivatesTheAccount() throws Exception {
        String email = TestAccounts.uniqueEmail("verify");
        JsonNode pending = register(email, TestAccounts.uniquePhone());

        JsonNode auth = TestAccounts.verify(mvc, json,
                pending.get("pendingToken").asText(), pending.get("devCode").asText());

        assertThat(auth.get("accessToken").asText()).isNotBlank();
        assertThat(auth.get("user").get("email").asText()).isEqualTo(email);

        // the access token works
        mvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + auth.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        // and a normal login now succeeds
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersecret1"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("a wrong code is refused and does not verify the account")
    void wrongCodeIsRefused() throws Exception {
        String email = TestAccounts.uniqueEmail("wrongcode");
        JsonNode pending = register(email, TestAccounts.uniquePhone());

        mvc.perform(post("/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pendingToken":"%s","code":"000000"}
                                """.formatted(pending.get("pendingToken").asText())))
                .andExpect(status().isBadRequest());

        // still unverified — and the client can tell that apart from a wrong password
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersecret1"}
                                """.formatted(email)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the same email cannot register twice")
    void duplicateEmailIsRejected() throws Exception {
        String email = TestAccounts.uniqueEmail("dupe");
        register(email, TestAccounts.uniquePhone());

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Someone Else","email":"%s","phone":"%s","password":"supersecret1"}
                                """.formatted(email, TestAccounts.uniquePhone())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already registered")));
    }

    @Test
    @DisplayName("the same mobile number cannot register twice either")
    void duplicatePhoneIsRejected() throws Exception {
        String phone = TestAccounts.uniquePhone();
        register(TestAccounts.uniqueEmail("phonetaken"), phone);

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Someone Else","email":"%s","phone":"%s","password":"supersecret1"}
                                """.formatted(TestAccounts.uniqueEmail("phoneother"), phone)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("mobile number")));
    }

    @Test
    @DisplayName("a malformed mobile number is rejected at sign-up")
    void badPhoneIsRejected() throws Exception {
        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Anil","email":"%s","phone":"12345","password":"supersecret1"}
                                """.formatted(TestAccounts.uniqueEmail("badphone"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.phone").exists());
    }

    @Test
    @DisplayName("asking for another code immediately is rate limited")
    void resendIsRateLimited() throws Exception {
        JsonNode pending = register(TestAccounts.uniqueEmail("resend"), TestAccounts.uniquePhone());

        mvc.perform(post("/auth/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pendingToken":"%s"}
                                """.formatted(pending.get("pendingToken").asText())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", containsString("wait")));
    }

    /* --------------------------- forgot password --------------------------- */

    @Test
    @DisplayName("forgot-password says the same thing whether or not the account exists")
    void forgotPasswordDoesNotRevealAccounts() throws Exception {
        String unknown = postJson("/auth/forgot-password", """
                {"emailOrPhone":"nobody-here@example.com"}
                """).toString();
        assertThat(unknown).doesNotContain("pendingToken\":\"");

        // a registered account does get a pending token
        String email = TestAccounts.uniqueEmail("forgot");
        TestAccounts.registerAndVerify(mvc, json, "Anil Dagar", email);

        JsonNode known = postJson("/auth/forgot-password", """
                {"emailOrPhone":"%s"}
                """.formatted(email));
        assertThat(known.get("pendingToken").asText()).isNotBlank();
    }

    @Test
    @DisplayName("a reset code sets a new password and the old one stops working")
    void resetPasswordChangesThePassword() throws Exception {
        String email = TestAccounts.uniqueEmail("reset");
        TestAccounts.registerAndVerify(mvc, json, "Anil Dagar", email);

        JsonNode pending = postJson("/auth/forgot-password", """
                {"emailOrPhone":"%s"}
                """.formatted(email));

        mvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pendingToken":"%s","code":"%s","newPassword":"brandnewpass9"}
                                """.formatted(pending.get("pendingToken").asText(),
                                pending.get("devCode").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // new password works
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"brandnewpass9"}
                                """.formatted(email)))
                .andExpect(status().isOk());

        // the old one does not
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersecret1"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("resetting a password kills refresh tokens issued before it")
    void resetInvalidatesOldRefreshTokens() throws Exception {
        String email = TestAccounts.uniqueEmail("killrefresh");
        JsonNode pendingRegister = TestAccounts.registerPending(mvc, json, "Anil Dagar", email,
                TestAccounts.uniquePhone());
        JsonNode auth = TestAccounts.verify(mvc, json,
                pendingRegister.get("pendingToken").asText(), pendingRegister.get("devCode").asText());
        String oldRefresh = auth.get("refreshToken").asText();

        // the token is good now
        mvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(oldRefresh)))
                .andExpect(status().isOk());

        // reset the password
        JsonNode pending = postJson("/auth/forgot-password", """
                {"emailOrPhone":"%s"}
                """.formatted(email));
        mvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pendingToken":"%s","code":"%s","newPassword":"brandnewpass9"}
                                """.formatted(pending.get("pendingToken").asText(),
                                pending.get("devCode").asText())))
                .andExpect(status().isOk());

        // the refresh token minted before the reset must be dead — otherwise a stolen one
        // would survive the very reset it was meant to trigger
        mvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(oldRefresh)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("password was changed")));
    }

    @Test
    @DisplayName("a sign-up code cannot be used to reset a password")
    void codesAreBoundToTheirPurpose() throws Exception {
        String email = TestAccounts.uniqueEmail("purpose");
        JsonNode pending = register(email, TestAccounts.uniquePhone());

        // the pending token names a REGISTER step, so the reset endpoint refuses it
        mvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pendingToken":"%s","code":"%s","newPassword":"brandnewpass9"}
                                """.formatted(pending.get("pendingToken").asText(),
                                pending.get("devCode").asText())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("different step")));
    }

    /* -------------------------------- tokens ------------------------------- */

    @Test
    @DisplayName("a wrong password is a 401 and does not say whether the email exists")
    void badPasswordIsOpaque() throws Exception {
        String email = TestAccounts.uniqueEmail("opaque");
        TestAccounts.registerAndVerify(mvc, json, "Anil Dagar", email);

        String wrongPassword = body(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"definitely-wrong"}
                                """.formatted(email)))
                .andReturn());
        String noSuchUser = body(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody-at-all@example.com","password":"definitely-wrong"}
                                """))
                .andReturn());

        // compare the messages: the bodies carry a timestamp, which always differs
        assertThat(json.readTree(wrongPassword).get("message").asText())
                .isEqualTo(json.readTree(noSuchUser).get("message").asText());
    }

    @Test
    @DisplayName("/auth/me without a token is a 401")
    void meRequiresAToken() throws Exception {
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a refresh token cannot be used as an access token")
    void refreshTokenIsNotAnAccessToken() throws Exception {
        String email = TestAccounts.uniqueEmail("refreshkind");
        JsonNode pending = TestAccounts.registerPending(mvc, json, "Anil Dagar", email,
                TestAccounts.uniquePhone());
        JsonNode auth = TestAccounts.verify(mvc, json,
                pending.get("pendingToken").asText(), pending.get("devCode").asText());

        mvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + auth.get("refreshToken").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a garbage token is rejected rather than crashing")
    void malformedTokenIsRejected() throws Exception {
        mvc.perform(get("/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pendingToken":"not-a-jwt","code":"123456"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
