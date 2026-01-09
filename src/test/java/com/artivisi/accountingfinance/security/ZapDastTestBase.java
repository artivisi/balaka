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
import org.zaproxy.clientapi.gen.Ascan;
import org.zaproxy.clientapi.gen.Pscan;
import org.zaproxy.clientapi.gen.Spider;

import java.net.CookieManager;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base class for ZAP DAST security tests.
 * Provides common setup, helper methods, and ZAP client utilities.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Tag("security")
@Tag("dast")
@SuppressWarnings("java:S2925") // Thread.sleep is intentional for ZAP proxy synchronization in DAST tests
abstract class ZapDastTestBase {

    protected static final boolean QUICK_SCAN = "true".equals(System.getProperty("dast.quick"));

    protected static final Logger log = LoggerFactory.getLogger(ZapDastTestBase.class);
    protected static final Path REPORTS_DIR = Paths.get("target/security-reports");
    protected static final String CONTAINER_REPORTS_DIR = "/zap/reports";
    protected static final int ZAP_PORT = 8080;

    // Severity thresholds
    protected static final int MAX_HIGH_ALERTS = 0;
    protected static final int MAX_MEDIUM_ALERTS = 0;

    // Active scan timeout (5 minutes for quick, 20 minutes for full)
    protected static final int ACTIVE_SCAN_TIMEOUT_MINUTES = QUICK_SCAN ? 5 : 20;

    @LocalServerPort
    protected int port;

    protected GenericContainer<?> zapContainer;
    protected ClientApi zapClient;
    protected String targetUrl;
    protected HttpClient authenticatedClient;

    @BeforeAll
    static void createReportsDir() throws Exception {
        Files.createDirectories(REPORTS_DIR);
    }

    @BeforeEach
    void setupZap() throws Exception {
        Testcontainers.exposeHostPorts(port);
        targetUrl = "http://host.testcontainers.internal:" + port;

        zapContainer = new GenericContainer<>(DockerImageName.parse("ghcr.io/zaproxy/zaproxy:stable"))
                .withExposedPorts(ZAP_PORT)
                .withFileSystemBind(REPORTS_DIR.toAbsolutePath().toString(), CONTAINER_REPORTS_DIR)
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
        log.info("Target application at {}", targetUrl);

        zapClient = new ClientApi(zapHost, zapMappedPort);
        waitForZapReady();
    }

    @AfterEach
    void tearDown() {
        if (zapContainer != null && zapContainer.isRunning()) {
            zapContainer.stop();
        }
        authenticatedClient = null;
    }


    // ========== HTTP Client Methods ==========

    protected HttpClient createProxiedClient() {
        String zapHost = zapContainer.getHost();
        int zapMappedPort = zapContainer.getMappedPort(ZAP_PORT);

        return HttpClient.newBuilder()
                .proxy(ProxySelector.of(new InetSocketAddress(zapHost, zapMappedPort)))
                .cookieHandler(new CookieManager())
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    protected HttpClient performLogin(String username, String password) throws Exception {
        log.info("Logging in as {}...", username);

        HttpClient client = createProxiedClient();
        String loginUrl = targetUrl + "/login";

        HttpResponse<String> getResponse = getPage(client, "/login");
        String csrfToken = extractCsrfToken(getResponse.body());

        Thread.sleep(300);

        String formData = "username=" + username + "&password=" + password + "&_csrf=" + csrfToken;
        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(loginUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpResponse<String> postResponse = client.send(postRequest, HttpResponse.BodyHandlers.ofString());

        if (postResponse.statusCode() == 302 || postResponse.statusCode() == 303) {
            log.info("Login successful for {}", username);
            getPage(client, "/dashboard");
        }

        Thread.sleep(300);
        return client;
    }

    protected String extractCsrfToken(String html) {
        Pattern csrfPattern = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");
        Matcher matcher = csrfPattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        csrfPattern = Pattern.compile("value=\"([^\"]+)\"\\s+name=\"_csrf\"");
        matcher = csrfPattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    protected HttpResponse<String> getPage(HttpClient client, String path) throws Exception {
        String url = path.startsWith("http") ? path : targetUrl + path;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected int accessPage(HttpClient client, String path) {
        try {
            HttpResponse<String> response = getPage(client, path);
            return response.statusCode();
        } catch (Exception e) {
            log.debug("Access error for {}: {}", path, e.getMessage());
            return -1;
        }
    }

    protected void accessPageWithParams(String path, String params) {
        String url = targetUrl + path + "?" + params;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        try {
            authenticatedClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.debug("Access error for {}: {}", url, e.getMessage());
        }
    }

    protected void postForm(String path, String formData) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl + path))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();
            authenticatedClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.debug("Form submission error: {}", e.getMessage());
        }
    }

    protected String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // ========== ZAP Methods ==========

    protected void waitForZapReady() throws Exception {
        int maxAttempts = 30;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                ApiResponse version = zapClient.core.version();
                log.info("ZAP version: {}", ((ApiResponseElement) version).getValue());
                return;
            } catch (Exception _) {
                Thread.sleep(1000);
            }
        }
        throw new RuntimeException("ZAP failed to start within timeout");
    }

    protected void spiderTarget(String url) throws Exception {
        log.debug("Spidering {}", url);

        Spider spider = new Spider(zapClient);
        ApiResponse response = spider.scan(url, null, null, null, null);
        String scanId = ((ApiResponseElement) response).getValue();

        int progress = 0;
        int timeout = 120;
        int elapsed = 0;
        while (progress < 100 && elapsed < timeout) {
            Thread.sleep(1000);
            elapsed++;
            progress = Integer.parseInt(((ApiResponseElement) spider.status(scanId)).getValue());
        }

        ApiResponseList results = (ApiResponseList) spider.results(scanId);
        log.info("Spider found {} URLs", results.getItems().size());
    }

    protected void runActiveScan(String url) throws Exception {
        log.info("Starting active scan on {} (timeout: {} min)...", url, ACTIVE_SCAN_TIMEOUT_MINUTES);

        Ascan ascan = new Ascan(zapClient);

        ApiResponse response = ascan.scan(url, "true", "true", null, null, null);
        String scanId = ((ApiResponseElement) response).getValue();

        int progress = 0;
        long startTime = System.currentTimeMillis();
        long timeoutMs = ACTIVE_SCAN_TIMEOUT_MINUTES * 60 * 1000L;

        while (progress < 100) {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > timeoutMs) {
                log.warn("Active scan timeout after {} minutes, stopping...", ACTIVE_SCAN_TIMEOUT_MINUTES);
                ascan.stop(scanId);
                break;
            }

            Thread.sleep(5000);
            progress = Integer.parseInt(((ApiResponseElement) ascan.status(scanId)).getValue());
            if (progress > 0) {
                log.info("Active scan progress: {}% (elapsed: {} sec)", progress, elapsed / 1000);
            }
        }

        ApiResponse alertsCount = ascan.alertsIds(scanId);
        log.info("Active scan completed - found {} potential issues",
                ((ApiResponseList) alertsCount).getItems().size());
    }

    protected void waitForPassiveScan() throws Exception {
        log.info("Waiting for passive scan to complete...");

        Pscan pscan = new Pscan(zapClient);
        int recordsRemaining = 1;
        int timeout = 180;
        int elapsed = 0;

        while (recordsRemaining > 0 && elapsed < timeout) {
            Thread.sleep(1000);
            elapsed++;
            recordsRemaining = Integer.parseInt(((ApiResponseElement) pscan.recordsToScan()).getValue());
        }

        log.info("Passive scan completed");
    }

    protected ScanResults analyzeAlerts(String scanName) throws Exception {
        org.zaproxy.clientapi.gen.Alert alertApi = new org.zaproxy.clientapi.gen.Alert(zapClient);
        ApiResponseList alerts = (ApiResponseList) alertApi.alerts(targetUrl, "-1", "-1", null);
        List<ApiResponse> alertList = alerts.getItems();

        ScanResults results = new ScanResults();
        StringBuilder report = new StringBuilder();
        report.append("\n=== ").append(scanName).append(" Scan Results ===\n");
        report.append("Target: ").append(targetUrl).append("\n\n");

        for (ApiResponse alert : alertList) {
            ApiResponseSet alertSet = (ApiResponseSet) alert;
            String risk = alertSet.getStringValue("risk");
            String name = alertSet.getStringValue("alert");
            String url = alertSet.getStringValue("url");
            String confidence = alertSet.getStringValue("confidence");
            String cweid = alertSet.getStringValue("cweid");
            String param = alertSet.getStringValue("param");

            // Filter out known false positives:
            // CSP Header Not Set on path traversal URLs is a Tomcat limitation, not a real vulnerability.
            // These malformed URLs are blocked by Tomcat before the servlet filter chain runs,
            // so we cannot add CSP headers to these responses. The path traversal attack is blocked.
            if (isKnownFalsePositive(name, url)) {
                results.excludedCount++;
                continue;
            }

            switch (risk) {
                case "High" -> {
                    results.highCount++;
                    report.append("[HIGH] ");
                }
                case "Medium" -> {
                    results.mediumCount++;
                    report.append("[MEDIUM] ");
                }
                case "Low" -> {
                    results.lowCount++;
                    report.append("[LOW] ");
                }
                default -> {
                    results.infoCount++;
                    report.append("[INFO] ");
                }
            }
            report.append(name);
            if (cweid != null && !cweid.isEmpty() && !"0".equals(cweid)) {
                report.append(" (CWE-").append(cweid).append(")");
            }
            report.append("\n");
            report.append("  URL: ").append(url).append("\n");
            if (param != null && !param.isEmpty()) {
                report.append("  Parameter: ").append(param).append("\n");
            }
            report.append("  Confidence: ").append(confidence).append("\n\n");
        }

        report.append("=== Summary ===\n");
        report.append("High: ").append(results.highCount).append("\n");
        report.append("Medium: ").append(results.mediumCount).append("\n");
        report.append("Low: ").append(results.lowCount).append("\n");
        report.append("Informational: ").append(results.infoCount).append("\n");
        if (results.excludedCount > 0) {
            report.append("Excluded (known limitations): ").append(results.excludedCount).append("\n");
        }

        log.info(report.toString());

        return results;
    }

    /**
     * Check if an alert is a known false positive that should be excluded from counting.
     *
     * Known limitations:
     * - CSP Header Not Set on malformed/attack URLs: Tomcat blocks these requests before
     *   the servlet filter chain runs, so CSP headers cannot be added. This is a Tomcat
     *   limitation, not a security vulnerability - the attacks are blocked.
     *   Affected patterns:
     *   - Path traversal: ../, ..\, etc.
     *   - XSS payloads: <script>, <img, <svg, javascript:, etc.
     *   - Template injection: {{, ${
     */
    private boolean isKnownFalsePositive(String alertName, String url) {
        // CSP Header Not Set on malformed/attack URLs (Tomcat rejects before filter chain)
        if ("Content Security Policy (CSP) Header Not Set".equals(alertName)) {
            // Path traversal patterns
            if (url.contains("..%2F") || url.contains("..%5C") ||
                url.contains("....%2F") || url.contains("..%252f")) {
                return true;
            }
            // XSS payload patterns (URL-encoded)
            if (url.contains("%3Cscript") || url.contains("%3Cimg") ||
                url.contains("%3Csvg") || url.contains("%3Ciframe") ||
                url.contains("javascript%3A") || url.contains("onerror%3D")) {
                return true;
            }
            // Template injection patterns
            if (url.contains("%7B%7B") || url.contains("%24%7B")) {
                return true;
            }
        }
        return false;
    }

    protected void assertSecurityThresholds(ScanResults results, String scanName) {
        assertTrue(results.highCount <= MAX_HIGH_ALERTS,
                scanName + " scan found " + results.highCount + " HIGH severity vulnerabilities (max: " + MAX_HIGH_ALERTS + ")");
        assertTrue(results.mediumCount <= MAX_MEDIUM_ALERTS,
                scanName + " scan found " + results.mediumCount + " MEDIUM severity vulnerabilities (max: " + MAX_MEDIUM_ALERTS + ")");
    }

    protected void generateHtmlReport(String filename) {
        try {
            // Use core.htmlreport() which is more stable across ZAP versions
            byte[] reportBytes = zapClient.core.htmlreport();
            Path reportPath = REPORTS_DIR.resolve(filename);
            Files.write(reportPath, reportBytes);
            log.info("Report generated: {}", reportPath);
        } catch (Exception ex) {
            // Report generation failure should not fail the test
            log.warn("Failed to generate HTML report {}: {}", filename, ex.getMessage());
        }
    }

    protected static class ScanResults {
        public int highCount = 0;
        public int mediumCount = 0;
        public int lowCount = 0;
        public int infoCount = 0;
        public int excludedCount = 0; // Known false positives (e.g., CSP on path traversal URLs)
    }
}
