package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Forwarded headers must reach the application over a real Tomcat connector, so
 * this test talks HTTP to the running server instead of reading properties back.
 *
 * <p>{@code server.forward-headers-strategy=native} installs Tomcat's
 * {@code RemoteIpValve}, which trusts {@code X-Forwarded-*} only from internal
 * or loopback peers. The test client connects from loopback, the same position a
 * reverse proxy occupies, so the scheme advertised by the proxy is the scheme the
 * device flow publishes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DisplayName("Forwarded headers from a reverse proxy")
class DeviceAuthForwardedHeadersTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("a proxied https request publishes an https verification uri")
    void forwardedProtoIsHonoured() throws Exception {
        JsonNode body = requestDeviceCode("X-Forwarded-Proto", "https");

        assertThat(body.path("verificationUri").asText())
                .as("verification_uri must follow the scheme the proxy advertises")
                .startsWith("https://")
                .endsWith("/device");
        assertThat(body.path("verificationUriComplete").asText())
                .startsWith("https://");
    }

    @Test
    @DisplayName("a direct request without forwarded headers stays on http")
    void plainRequestKeepsItsOwnScheme() throws Exception {
        JsonNode body = requestDeviceCode(null, null);

        assertThat(body.path("verificationUri").asText())
                .startsWith("http://")
                .endsWith("/device");
    }

    private JsonNode requestDeviceCode(String headerName, String headerValue) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/device/code"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"clientId\":\"forwarded-headers-test\"}"));

        if (headerName != null) {
            builder.header(headerName, headerValue);
        }

        HttpResponse<String> response = httpClient.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode())
                .as("POST /api/device/code body: %s", response.body())
                .isEqualTo(200);
        return objectMapper.readTree(response.body());
    }
}
