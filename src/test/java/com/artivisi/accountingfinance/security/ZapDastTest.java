package com.artivisi.accountingfinance.security;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.zaproxy.clientapi.core.*;
import org.zaproxy.clientapi.gen.Pscan;
import org.zaproxy.clientapi.gen.Spider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OWASP ZAP DAST Integration Test
 *
 * This test integrates OWASP ZAP into the Spring Boot test infrastructure.
 * ZAP runs as a Testcontainer and scans the running application.
 *
 * Run with: ./mvnw test -Dtest=ZapDastTest
 *
 * The test will:
 * 1. Start the Spring Boot app on a random port
 * 2. Start ZAP as a Docker container
 * 3. Spider the application to discover endpoints
 * 4. Run passive scanning on all discovered pages
 * 5. Report any security vulnerabilities found
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DisplayName("OWASP ZAP DAST Security Scan")
@Tag("security")
@Tag("dast")
class ZapDastTest {

    private static final Logger log = LoggerFactory.getLogger(ZapDastTest.class);
    private static final Path REPORTS_DIR = Paths.get("target/security-reports");
    private static final int ZAP_PORT = 8080;

    // Severity thresholds - fail test if vulnerabilities exceed these
    private static final int MAX_HIGH_ALERTS = 0;
    private static final int MAX_MEDIUM_ALERTS = 5;

    @LocalServerPort
    private int port;

    private static GenericContainer<?> zapContainer;
    private static ClientApi zapClient;

    @BeforeAll
    static void startZap() throws Exception {
        // Create reports directory
        Files.createDirectories(REPORTS_DIR);
    }

    @BeforeEach
    void setupZap() throws Exception {
        // Expose the Spring Boot port to Docker containers
        Testcontainers.exposeHostPorts(port);

        // Start ZAP container
        zapContainer = new GenericContainer<>(DockerImageName.parse("ghcr.io/zaproxy/zaproxy:stable"))
                .withExposedPorts(ZAP_PORT)
                .withCommand("zap.sh", "-daemon", "-host", "0.0.0.0", "-port", String.valueOf(ZAP_PORT),
                        "-config", "api.addrs.addr.name=.*",
                        "-config", "api.addrs.addr.regex=true",
                        "-config", "api.disablekey=true")
                .waitingFor(Wait.forHttp("/JSON/core/view/version/")
                        .forPort(ZAP_PORT)
                        .withStartupTimeout(Duration.ofMinutes(2)));

        zapContainer.start();

        String zapHost = zapContainer.getHost();
        int zapMappedPort = zapContainer.getMappedPort(ZAP_PORT);

        log.info("ZAP started at {}:{}", zapHost, zapMappedPort);
        log.info("Target application at host.testcontainers.internal:{}", port);

        // Initialize ZAP client
        zapClient = new ClientApi(zapHost, zapMappedPort);

        // Wait for ZAP to be fully ready
        waitForZapReady();
    }

    @AfterEach
    void tearDown() {
        if (zapContainer != null && zapContainer.isRunning()) {
            zapContainer.stop();
        }
    }

    @Test
    @DisplayName("Should pass baseline security scan")
    void shouldPassBaselineSecurityScan() throws Exception {
        String targetUrl = "http://host.testcontainers.internal:" + port;

        log.info("Starting ZAP baseline scan against {}", targetUrl);

        // Access the target to establish connection
        zapClient.core.accessUrl(targetUrl + "/login", "true");
        Thread.sleep(2000);

        // Spider the login page and public pages
        spiderTarget(targetUrl + "/login");

        // Wait for passive scan to complete
        waitForPassiveScan();

        // Get alerts
        ApiResponseList alerts = (ApiResponseList) zapClient.core.alerts(targetUrl, "-1", "-1", null);
        List<ApiResponse> alertList = alerts.getItems();

        // Count by severity
        int highCount = 0;
        int mediumCount = 0;
        int lowCount = 0;
        int infoCount = 0;

        StringBuilder report = new StringBuilder();
        report.append("\n=== OWASP ZAP Security Scan Results ===\n");
        report.append("Target: ").append(targetUrl).append("\n\n");

        for (ApiResponse alert : alertList) {
            ApiResponseSet alertSet = (ApiResponseSet) alert;
            String risk = alertSet.getAttribute("risk");
            String name = alertSet.getAttribute("alert");
            String url = alertSet.getAttribute("url");
            String confidence = alertSet.getAttribute("confidence");

            switch (risk) {
                case "High" -> {
                    highCount++;
                    report.append("[HIGH] ");
                }
                case "Medium" -> {
                    mediumCount++;
                    report.append("[MEDIUM] ");
                }
                case "Low" -> {
                    lowCount++;
                    report.append("[LOW] ");
                }
                default -> {
                    infoCount++;
                    report.append("[INFO] ");
                }
            }
            report.append(name).append("\n");
            report.append("  URL: ").append(url).append("\n");
            report.append("  Confidence: ").append(confidence).append("\n\n");
        }

        report.append("=== Summary ===\n");
        report.append("High: ").append(highCount).append("\n");
        report.append("Medium: ").append(mediumCount).append("\n");
        report.append("Low: ").append(lowCount).append("\n");
        report.append("Informational: ").append(infoCount).append("\n");

        log.info(report.toString());

        // Save HTML report
        saveHtmlReport(targetUrl);

        // Assert thresholds
        assertTrue(highCount <= MAX_HIGH_ALERTS,
                "Found " + highCount + " HIGH severity vulnerabilities (max allowed: " + MAX_HIGH_ALERTS + ")");
        assertTrue(mediumCount <= MAX_MEDIUM_ALERTS,
                "Found " + mediumCount + " MEDIUM severity vulnerabilities (max allowed: " + MAX_MEDIUM_ALERTS + ")");
    }

    @Test
    @DisplayName("Should pass authenticated scan")
    void shouldPassAuthenticatedScan() throws Exception {
        String targetUrl = "http://host.testcontainers.internal:" + port;

        log.info("Starting ZAP authenticated scan against {}", targetUrl);

        // First, access login page
        zapClient.core.accessUrl(targetUrl + "/login", "true");
        Thread.sleep(1000);

        // Perform form-based authentication
        // Note: ZAP will capture the session from successful login
        ApiResponse authResponse = zapClient.authentication.setAuthenticationMethod(
                "Default Context",
                "formBasedAuthentication",
                "loginUrl=" + targetUrl + "/login" +
                        "&loginRequestData=username%3D{%username%}%26password%3D{%password%}"
        );

        // Set login credentials
        zapClient.users.newUser("Default Context", "admin");
        zapClient.users.setAuthenticationCredentials(
                "Default Context", "0",
                "username=admin&password=admin"
        );
        zapClient.users.setUserEnabled("Default Context", "0", "true");

        // Spider authenticated pages
        spiderAsUser(targetUrl, "Default Context", "0");

        // Wait for passive scan
        waitForPassiveScan();

        // Get alerts for authenticated scan
        ApiResponseList alerts = (ApiResponseList) zapClient.core.alerts(targetUrl, "-1", "-1", null);

        log.info("Authenticated scan found {} total alerts", alerts.getItems().size());

        // Save authenticated scan report
        byte[] htmlReport = zapClient.core.htmlreport();
        Files.write(REPORTS_DIR.resolve("zap-authenticated-report.html"), htmlReport);
    }

    private void waitForZapReady() throws Exception {
        int maxAttempts = 30;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                ApiResponse version = zapClient.core.version();
                log.info("ZAP version: {}", ((ApiResponseElement) version).getValue());
                return;
            } catch (Exception e) {
                Thread.sleep(1000);
            }
        }
        throw new RuntimeException("ZAP failed to start within timeout");
    }

    private void spiderTarget(String url) throws Exception {
        log.info("Starting spider on {}", url);

        Spider spider = new Spider(zapClient);
        ApiResponse response = spider.scan(url, null, null, null, null);
        String scanId = ((ApiResponseElement) response).getValue();

        // Wait for spider to complete
        int progress = 0;
        while (progress < 100) {
            Thread.sleep(1000);
            progress = Integer.parseInt(((ApiResponseElement) spider.status(scanId)).getValue());
            log.debug("Spider progress: {}%", progress);
        }

        log.info("Spider completed, discovered URLs:");
        ApiResponseList results = (ApiResponseList) spider.results(scanId);
        for (ApiResponse result : results.getItems()) {
            log.debug("  {}", ((ApiResponseElement) result).getValue());
        }
    }

    private void spiderAsUser(String url, String contextName, String userId) throws Exception {
        log.info("Starting authenticated spider on {}", url);

        Spider spider = new Spider(zapClient);
        ApiResponse response = spider.scanAsUser(contextName, userId, url, null, null, null, null);
        String scanId = ((ApiResponseElement) response).getValue();

        int progress = 0;
        while (progress < 100) {
            Thread.sleep(1000);
            progress = Integer.parseInt(((ApiResponseElement) spider.status(scanId)).getValue());
            log.debug("Authenticated spider progress: {}%", progress);
        }

        log.info("Authenticated spider completed");
    }

    private void waitForPassiveScan() throws Exception {
        log.info("Waiting for passive scan to complete...");

        Pscan pscan = new Pscan(zapClient);
        int recordsRemaining = 1;
        while (recordsRemaining > 0) {
            Thread.sleep(500);
            recordsRemaining = Integer.parseInt(((ApiResponseElement) pscan.recordsToScan()).getValue());
            log.debug("Records remaining to scan: {}", recordsRemaining);
        }

        log.info("Passive scan completed");
    }

    private void saveHtmlReport(String targetUrl) throws Exception {
        byte[] htmlReport = zapClient.core.htmlreport();
        Path reportPath = REPORTS_DIR.resolve("zap-baseline-report.html");
        Files.write(reportPath, htmlReport);
        log.info("HTML report saved to {}", reportPath);
    }
}
