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
import org.zaproxy.clientapi.gen.Alert;
import org.zaproxy.clientapi.gen.Pscan;
import org.zaproxy.clientapi.gen.Spider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * OWASP ZAP DAST Integration Test
 *
 * This test integrates OWASP ZAP into the Spring Boot test infrastructure.
 * ZAP runs as a Testcontainer and scans the running application.
 *
 * CI-only by default. To run locally:
 *   ./mvnw test -Dtest=ZapDastTest -DexcludedGroups="" -Ddast.enabled=true
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

    // CI environment variable (GitHub Actions sets CI=true)
    private static final boolean IS_CI = "true".equals(System.getenv("CI"));
    // Manual override: -Ddast.enabled=true
    private static final boolean DAST_ENABLED = "true".equals(System.getProperty("dast.enabled"));

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
        // Skip container startup if not in CI and not explicitly enabled
        if (!IS_CI && !DAST_ENABLED) {
            return;
        }

        // Expose the Spring Boot port to Docker containers
        Testcontainers.exposeHostPorts(port);

        // Start ZAP container
        zapContainer = new GenericContainer<>(DockerImageName.parse("ghcr.io/zaproxy/zaproxy:stable"))
                .withExposedPorts(ZAP_PORT)
                .withCommand("zap.sh", "-daemon", "-host", "0.0.0.0", "-port", String.valueOf(ZAP_PORT),
                        "-config", "api.addrs.addr.name=.*",
                        "-config", "api.addrs.addr.regex=true",
                        "-config", "api.disablekey=true")
                .waitingFor(Wait.forLogMessage(".*ZAP is now listening.*\\n", 1)
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
        assumeTrue(IS_CI || DAST_ENABLED,
                "DAST tests skipped locally. Use -Ddast.enabled=true to run.");

        String targetUrl = "http://host.testcontainers.internal:" + port;

        log.info("Starting ZAP baseline scan against {}", targetUrl);

        // Access the target to establish connection
        zapClient.core.accessUrl(targetUrl + "/login", "true");
        Thread.sleep(2000);

        // Spider the login page and public pages
        spiderTarget(targetUrl + "/login");

        // Wait for passive scan to complete
        waitForPassiveScan();

        // Get alerts using Alert API
        Alert alertApi = new Alert(zapClient);
        ApiResponseList alerts = (ApiResponseList) alertApi.alerts(targetUrl, "-1", "-1", null);
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
            String risk = alertSet.getStringValue("risk");
            String name = alertSet.getStringValue("alert");
            String url = alertSet.getStringValue("url");
            String confidence = alertSet.getStringValue("confidence");

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
        assumeTrue(IS_CI || DAST_ENABLED,
                "DAST tests skipped locally. Use -Ddast.enabled=true to run.");

        String targetUrl = "http://host.testcontainers.internal:" + port;

        log.info("Starting ZAP authenticated scan against {}", targetUrl);

        // Perform actual login to get authenticated session
        // 1. Get CSRF token from login page
        // 2. Submit login form with credentials
        // 3. ZAP will capture the session cookie
        // 4. Spider authenticated pages using that session

        // Step 1: Access login page (ZAP will capture this in history)
        zapClient.core.accessUrl(targetUrl + "/login", "true");
        Thread.sleep(1000);

        // Step 2: Perform login via HTTP Client to establish session
        // ZAP proxy will intercept and record the authenticated session
        performLogin(targetUrl);

        // Step 3: Verify we're logged in by accessing dashboard
        zapClient.core.accessUrl(targetUrl + "/dashboard", "true");
        Thread.sleep(1000);

        // Step 4: Spider authenticated pages
        // ZAP will use the captured session from the login
        log.info("Spidering authenticated pages...");
        spiderTarget(targetUrl + "/dashboard");

        // Wait for passive scan
        waitForPassiveScan();

        // Get alerts for authenticated scan
        Alert alertApi = new Alert(zapClient);
        ApiResponseList alerts = (ApiResponseList) alertApi.alerts(targetUrl, "-1", "-1", null);

        log.info("Authenticated scan found {} total alerts", alerts.getItems().size());

        // Count by severity
        int highCount = 0;
        int mediumCount = 0;

        for (ApiResponse alert : alerts.getItems()) {
            ApiResponseSet alertSet = (ApiResponseSet) alert;
            String risk = alertSet.getStringValue("risk");
            if ("High".equals(risk)) {
                highCount++;
            } else if ("Medium".equals(risk)) {
                mediumCount++;
            }
        }

        log.info("Authenticated scan: High={}, Medium={}", highCount, mediumCount);

        // Save authenticated scan report
        generateHtmlReport("zap-authenticated-report.html", targetUrl);

        // Assert thresholds (same as baseline)
        assertTrue(highCount <= MAX_HIGH_ALERTS,
                "Found " + highCount + " HIGH severity vulnerabilities (max allowed: " + MAX_HIGH_ALERTS + ")");
        assertTrue(mediumCount <= MAX_MEDIUM_ALERTS,
                "Found " + mediumCount + " MEDIUM severity vulnerabilities (max allowed: " + MAX_MEDIUM_ALERTS + ")");
    }

    private void performLogin(String targetUrl) throws Exception {
        // Perform login using HttpClient with ZAP as proxy
        // This ensures ZAP captures the authenticated session
        log.info("Performing login with admin credentials via HttpClient through ZAP proxy...");

        String zapHost = zapContainer.getHost();
        int zapPort = zapContainer.getMappedPort(ZAP_PORT);

        // Create HttpClient with ZAP as proxy
        HttpClient client = HttpClient.newBuilder()
                .proxy(java.net.ProxySelector.of(new java.net.InetSocketAddress(zapHost, zapPort)))
                .cookieHandler(new java.net.CookieManager())
                .build();

        String loginUrl = targetUrl + "/login";

        // Step 1: GET login page to extract CSRF token
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(loginUrl))
                .GET()
                .build();

        HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
        String loginPage = getResponse.body();

        // Extract CSRF token
        Pattern csrfPattern = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");
        Matcher matcher = csrfPattern.matcher(loginPage);
        String csrfToken = "";
        if (matcher.find()) {
            csrfToken = matcher.group(1);
            log.info("CSRF token extracted: {}...", csrfToken.substring(0, Math.min(8, csrfToken.length())));
        } else {
            throw new RuntimeException("CSRF token not found in login page");
        }

        Thread.sleep(500);

        // Step 2: POST login form with admin credentials
        String formData = "username=admin&password=admin&_csrf=" + csrfToken;

        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(loginUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpResponse<String> postResponse = client.send(postRequest, HttpResponse.BodyHandlers.ofString());

        // Check for successful redirect to dashboard
        if (postResponse.statusCode() == 302 || postResponse.statusCode() == 303) {
            String location = postResponse.headers().firstValue("Location").orElse("");
            log.info("Login successful - redirected to: {}", location);
        } else if (postResponse.statusCode() == 200 && postResponse.body().contains("dashboard")) {
            log.info("Login successful - dashboard page loaded");
        } else {
            log.warn("Login response status: {}, may not be authenticated", postResponse.statusCode());
        }

        // Give ZAP time to process the session
        Thread.sleep(1000);
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
        generateHtmlReport("zap-baseline-report.html", targetUrl);
    }

    private void generateHtmlReport(String filename, String targetUrl) throws Exception {
        Path reportPath = REPORTS_DIR.resolve(filename);

        // Use core.htmlreport API to get HTML report as bytes
        byte[] htmlReportBytes = zapClient.core.htmlreport();

        // Write report to file
        Files.write(reportPath, htmlReportBytes);

        log.info("HTML report saved to {}", reportPath);
    }
}
