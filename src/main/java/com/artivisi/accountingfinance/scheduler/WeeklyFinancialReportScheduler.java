package com.artivisi.accountingfinance.scheduler;

import com.artivisi.accountingfinance.service.WeeklyFinancialReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@RequiredArgsConstructor
@Slf4j
public class WeeklyFinancialReportScheduler {

    private final WeeklyFinancialReportService reportService;

    @Value("${app.whatsapp.report.enabled:false}")
    private boolean enabled;

    @Value("${app.whatsapp.report.url:}")
    private String url;

    @Value("${app.whatsapp.report.phone:}")
    private String phone;

    @Value("${app.whatsapp.report.auth:}")
    private String auth;

    @Scheduled(cron = "${app.whatsapp.report.schedule:0 0 8 * * MON}")
    public void sendWeeklyReport() {
        if (!enabled) {
            log.debug("Weekly financial report disabled");
            return;
        }
        if (url.isBlank() || phone.isBlank()) {
            log.warn("Weekly report enabled but url/phone not configured");
            return;
        }

        try {
            String report = reportService.generateReport();
            String body = "{\"phone\":\"" + phone + "\",\"message\":" + jsonString(report) + "}";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

            if (!auth.isBlank()) {
                String encoded = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                builder.header("Authorization", "Basic " + encoded);
            }

            HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                log.info("Weekly financial report sent: {}", resp.body());
            } else {
                log.warn("Failed to send weekly report: {} - {}", resp.statusCode(), resp.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error sending weekly financial report", e);
        } catch (Exception e) {
            log.error("Error sending weekly financial report", e);
        }
    }

    private String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }
}
