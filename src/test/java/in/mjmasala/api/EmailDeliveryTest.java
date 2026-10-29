package in.mjmasala.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the one-time code is actually delivered by email.
 *
 * Two things make this a real test rather than a mock:
 *
 * 1. **A real SMTP server.** GreenMail runs in-process on a socket, and the application
 *    connects to it exactly as it would to a mail host — JavaMailSender, SMTP, the lot.
 * 2. **The code is not in the API response.** `app.otp.expose-code` is switched off here,
 *    so the only way to learn the code is to read it out of the delivered message. If
 *    delivery broke, this test could not pass.
 */
@SpringBootTest(properties = {
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=3025",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
        "app.notifications.email.enabled=true",
        "app.notifications.email.from=no-reply@mjmasala.test",
        // off on purpose: the code must come out of the email, not the response
        "app.otp.expose-code=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailDeliveryTest {

    private static GreenMail smtp;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @BeforeAll
    static void startSmtp() {
        smtp = new GreenMail(new ServerSetup(3025, null, "smtp"));
        smtp.start();
    }

    @AfterAll
    static void stopSmtp() {
        if (smtp != null) smtp.stop();
    }

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    private String codeFromEmail(String to) throws Exception {
        assertThat(smtp.waitForIncomingEmail(5000, 1))
                .as("the verification email should have arrived at the SMTP server")
                .isTrue();

        MimeMessage[] received = smtp.getReceivedMessages();
        MimeMessage match = null;
        for (MimeMessage message : received) {
            if (message.getAllRecipients()[0].toString().equals(to)) match = message;
        }
        assertThat(match).as("a message addressed to %s", to).isNotNull();

        String body = (String) match.getContent();
        Matcher m = CODE.matcher(body);
        assertThat(m.find()).as("the email body should carry a 6-digit code").isTrue();
        return m.group(1);
    }

    @Test
    @DisplayName("the sign-up code arrives by email and is what verifies the account")
    void codeIsDeliveredByEmail() throws Exception {
        smtp.purgeEmailFromAllMailboxes();

        String email = TestAccounts.uniqueEmail("maildelivery");
        JsonNode pending = TestAccounts.registerPending(mvc, json, "Anil Dagar", email,
                TestAccounts.uniquePhone());

        // the response must NOT carry the code — and with non_null serialisation the key
        // is not even present, which is a stronger guarantee than a null value
        assertThat(pending.has("devCode"))
                .as("with expose-code off the API must not hand the code back")
                .isFalse();

        String code = codeFromEmail(email);

        // and that code is the one that activates the account
        JsonNode auth = TestAccounts.verify(mvc, json, pending.get("pendingToken").asText(), code);
        assertThat(auth.get("accessToken").asText()).isNotBlank();
        assertThat(auth.get("user").get("email").asText()).isEqualTo(email);
    }

    @Test
    @DisplayName("the email says what it is for and warns against sharing the code")
    void emailReadsLikeARealOne() throws Exception {
        smtp.purgeEmailFromAllMailboxes();

        String email = TestAccounts.uniqueEmail("mailcopy");
        TestAccounts.registerPending(mvc, json, "Anil Dagar", email, TestAccounts.uniquePhone());
        codeFromEmail(email);

        MimeMessage message = smtp.getReceivedMessages()[0];
        String body = (String) message.getContent();

        assertThat(message.getSubject()).contains("MJ MASALA");
        assertThat(body).contains("verify your account");
        assertThat(body.toLowerCase()).contains("never share");
        // a code that never expires is not much of a code
        assertThat(body).contains("10 minutes");
    }

    @Test
    @DisplayName("a password-reset email reads differently from a sign-up one")
    void resetEmailIsDistinct() throws Exception {
        smtp.purgeEmailFromAllMailboxes();

        String email = TestAccounts.uniqueEmail("mailreset");
        // expose-code is off in this context, so sign up by reading the code from the mail
        JsonNode pending = TestAccounts.registerPending(mvc, json, "Anil Dagar", email,
                TestAccounts.uniquePhone());
        TestAccounts.verify(mvc, json, pending.get("pendingToken").asText(), codeFromEmail(email));

        smtp.purgeEmailFromAllMailboxes();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/auth/forgot-password")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailOrPhone":"%s"}
                                """.formatted(email)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk());

        assertThat(smtp.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage message = smtp.getReceivedMessages()[0];

        assertThat(message.getSubject()).contains("Reset");
        assertThat((String) message.getContent()).contains("new password");
    }
}
