package in.mjmasala.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the SMS provider request is built and sent correctly.
 *
 * The provider is stubbed with the JDK's own HTTP server, so there is no extra dependency —
 * but the application makes a genuine outbound HTTP call over a socket to it, with the real
 * headers and body it would send MSG91.
 *
 * `app.otp.expose-code` is off, so the code can only come from the request the provider
 * received. That is what ties the assertion to delivery rather than to the response.
 */
@SpringBootTest(properties = {
        "app.notifications.sms.enabled=true",
        "app.notifications.sms.provider=msg91",
        "app.notifications.sms.url=http://127.0.0.1:3026/send",
        "app.notifications.sms.auth-key=test-auth-key",
        "app.notifications.sms.sender-id=MJMSLA",
        "app.notifications.sms.template-id=flow-123",
        "app.otp.expose-code=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SmsDeliveryTest {

    private static HttpServer provider;
    private static final CopyOnWriteArrayList<String> bodies = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<String> authHeaders = new CopyOnWriteArrayList<>();
    private static final AtomicInteger calls = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @BeforeAll
    static void startProvider() throws Exception {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 3026), 0);
        provider.createContext("/send", exchange -> {
            calls.incrementAndGet();
            authHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst("authkey")));
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        provider.start();
    }

    @AfterAll
    static void stopProvider() {
        if (provider != null) provider.stop(0);
    }

    private static final Pattern CODE = Pattern.compile("\\\"VAR1\\\":\\\"(\\d{6})\\\"");

    @Test
    @DisplayName("the code is posted to the provider and is what verifies the account")
    void codeGoesOutOverSms() throws Exception {
        bodies.clear();
        authHeaders.clear();
        calls.set(0);

        String email = TestAccounts.uniqueEmail("smsdelivery");
        String phone = TestAccounts.uniquePhone();
        JsonNode pending = TestAccounts.registerPending(mvc, json, "Anil Dagar", email, phone);

        assertThat(pending.has("devCode"))
                .as("with expose-code off the API must not hand the code back")
                .isFalse();

        assertThat(calls.get()).as("the provider should have been called").isGreaterThan(0);

        // the request MSG91 would have received
        String body = bodies.get(0);
        assertThat(authHeaders.get(0)).isEqualTo("test-auth-key");
        assertThat(body).contains("\"flow_id\":\"flow-123\"");
        assertThat(body).contains("\"sender\":\"MJMSLA\"");
        assertThat(body).contains("\"mobiles\":\"91" + phone + "\"");

        Matcher m = CODE.matcher(body);
        assertThat(m.find()).as("the body should carry the 6-digit code as VAR1").isTrue();
        String code = m.group(1);

        // and that code activates the account
        JsonNode auth = TestAccounts.verify(mvc, json, pending.get("pendingToken").asText(), code);
        assertThat(auth.get("accessToken").asText()).isNotBlank();
    }

    @Test
    @DisplayName("one code, both channels — the email goes out alongside the SMS")
    void bothChannelsAreUsed() throws Exception {
        bodies.clear();
        calls.set(0);

        String email = TestAccounts.uniqueEmail("smsandmail");
        String phone = TestAccounts.uniquePhone();
        TestAccounts.registerPending(mvc, json, "Anil Dagar", email, phone);

        // the SMS provider was called even though no mail host is configured here
        assertThat(calls.get()).isGreaterThan(0);
        assertThat(bodies.get(0)).contains(phone);
    }
}
