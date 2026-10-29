package in.mjmasala.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A real HTTP smoke test.
 *
 * The other suites drive the controllers through MockMvc, which bypasses the servlet
 * container. This one starts the actual server and speaks to it over the wire, so it is
 * the test that would catch a wrong context path, a serialisation setting or a filter
 * that only misbehaves in a real request.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HttpSmokeTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    ObjectMapper json;

    @Test
    @DisplayName("the catalogue answers over HTTP under the /api context path")
    void catalogueOverHttp() throws Exception {
        ResponseEntity<String> response = http.getForEntity("/products?size=3", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.get("totalItems").asInt()).isEqualTo(17);
        assertThat(body.get("items")).hasSize(3);
    }

    @Test
    @DisplayName("a UTF-8 display string survives the round trip intact")
    void utf8SurvivesTheWire() throws Exception {
        ResponseEntity<byte[]> response = http.getForEntity("/combos", byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = new String(response.getBody(), StandardCharsets.UTF_8);
        // the "Save ₹300" chip — if the encoding were wrong this would be mojibake
        assertThat(body).contains("Save ₹");
    }

    @Test
    @DisplayName("register → verify → me works end to end with a real bearer token")
    void registerThenMeOverHttp() throws Exception {
        String email = "wire" + System.nanoTime() + "@example.com";

        ResponseEntity<String> registered = http.postForEntity("/auth/register",
                jsonEntity("""
                        {"name":"Wire Test","email":"%s","phone":"%s","password":"supersecret1"}
                        """.formatted(email, "9" + String.format("%09d", Math.abs(System.nanoTime() % 1_000_000_000L)))),
                String.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // the account is inert until the code comes back — no token is handed out here
        JsonNode pending = json.readTree(registered.getBody());
        assertThat(pending.has("accessToken")).isFalse();
        assertThat(pending.get("pendingToken").asText()).isNotBlank();

        ResponseEntity<String> verified = http.postForEntity("/auth/verify",
                jsonEntity("""
                        {"pendingToken":"%s","code":"%s"}
                        """.formatted(pending.get("pendingToken").asText(),
                        pending.get("devCode").asText())),
                String.class);
        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);

        String token = json.readTree(verified.getBody()).get("accessToken").asText();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> me = http.exchange("/auth/me", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(me.getBody()).get("email").asText()).isEqualTo(email);
    }

    @Test
    @DisplayName("a protected route without a token is a 401, not a redirect to a login page")
    void unauthenticatedIsJsonNotARedirect() throws Exception {
        ResponseEntity<String> response = http.getForEntity("/cart", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(json.readTree(response.getBody()).get("status").asInt()).isEqualTo(401);
    }

    @Test
    @DisplayName("an unknown route is a 404, and the error body is ours")
    void errorsAreConsistent() throws Exception {
        ResponseEntity<String> response = http.getForEntity("/products/no-such-spice", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(404);
        assertThat(body.get("message").asText()).contains("no-such-spice");
    }

    private HttpEntity<String> jsonEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
