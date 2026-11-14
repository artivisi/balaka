package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
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
 * The first-run setup wizard is the only unauthenticated path that can create an
 * administrator, so it must be absent unless a profile enables it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.setup.enabled=false")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DisplayName("First-run setup endpoint")
class SetupEndpointDisabledTest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("/setup is not routed when the wizard is disabled")
    void setupReturns404WhenDisabled() throws Exception {
        HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build()
                .send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/setup"))
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("Setup Awal");
    }
}
