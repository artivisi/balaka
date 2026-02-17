package com.artivisi.accountingfinance.manual;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Captures screenshots of application pages using Playwright.
 * Used for generating user manual documentation.
 */
public class ScreenshotCapture {

    private final String baseUrl;
    private final Path screenshotsDir;

    public ScreenshotCapture(String baseUrl, Path screenshotsDir) {
        this.baseUrl = baseUrl;
        this.screenshotsDir = screenshotsDir;
    }

    /**
     * Page definitions for screenshot capture
     */
    public record PageDefinition(
            String id,
            String name,
            String url,
            boolean requiresAuth,
            String description,
            String section
    ) {}

    public static List<PageDefinition> getPageDefinitions() {
        return List.of(
            // Tax Reports
            new PageDefinition("reports-pph23-withholding", "Pemotongan PPh 23", "/reports/pph23-withholding", true,
                    "Laporan pemotongan PPh 23 dari vendor", "laporan-pajak"),

            // Depreciation Report
            new PageDefinition("reports-depreciation", "Laporan Penyusutan", "/reports/depreciation", true,
                    "Laporan penyusutan untuk SPT Tahunan (Lampiran 1A)", "laporan-penyusutan"),

            // Self-Service
            new PageDefinition("self-service-payslips", "Slip Gaji Saya", "/self-service/payslips", true,
                    "Daftar slip gaji karyawan", "layanan-mandiri"),
            new PageDefinition("self-service-bukti-potong", "Bukti Potong Saya", "/self-service/bukti-potong", true,
                    "Bukti potong PPh 21 (1721-A1)", "layanan-mandiri"),
            new PageDefinition("self-service-profile", "Profil Saya", "/self-service/profile", true,
                    "Informasi profil karyawan", "layanan-mandiri"),

            // AI Transaction (screenshots taken by AiTransactionFlowTest)
            new PageDefinition("ai-transaction/00-device-authorization", "Halaman Otorisasi Device", "/device", true,
                    "Halaman otorisasi perangkat AI via OAuth 2.0 Device Flow", "bantuan-ai"),
            new PageDefinition("ai-transaction/04-transactions-list", "Daftar Transaksi", "/transactions", true,
                    "Daftar transaksi yang dibuat via AI assistant", "bantuan-ai"),

            // Analysis Reports (screenshots taken by AnalysisReportTest)
            new PageDefinition("analysis-reports/list", "Daftar Laporan Analisis AI", "/analysis-reports", true,
                    "Daftar laporan analisis yang dipublikasikan oleh AI tools", "analisis-ai"),
            new PageDefinition("analysis-reports/detail-top", "Detail Laporan Analisis (Header + Metrik)", "/analysis-reports", true,
                    "Header laporan, ringkasan eksekutif, dan indikator utama", "analisis-ai"),
            new PageDefinition("analysis-reports/detail-bottom", "Detail Laporan Analisis (Temuan + Rekomendasi)", "/analysis-reports", true,
                    "Temuan, rekomendasi, dan penilaian risiko", "analisis-ai")
        );
    }

    /**
     * Captures screenshots of all defined pages
     */
    public void captureAll() {
        screenshotsDir.toFile().mkdirs();

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true)
            );

            BrowserContext context = browser.newContext(
                    new Browser.NewContextOptions()
                            .setViewportSize(1280, 800)
                            .setLocale("id-ID")
            );

            Page page = context.newPage();

            // Login first
            if (login(page)) {
                System.out.println("Login successful");
            } else {
                System.out.println("Login failed, continuing with unauthenticated pages only");
            }

            // Capture each page
            for (PageDefinition pageDef : getPageDefinitions()) {
                capturePageScreenshot(page, pageDef);
            }

            browser.close();
        }

        System.out.println("Screenshot capture complete!");
    }

    private boolean login(Page page) {
        try {
            page.navigate(baseUrl + "/login");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            page.fill("input[name='username']", "admin");
            page.fill("input[name='password']", "admin");
            page.click("button[type='submit']");

            page.waitForURL("**/dashboard", new Page.WaitForURLOptions().setTimeout(10000));
            return true;
        } catch (Exception e) {
            System.err.println("Login failed: " + e.getMessage());
            return false;
        }
    }

    private void capturePageScreenshot(Page page, PageDefinition pageDef) {
        System.out.printf("Capturing: %s (%s)%n", pageDef.name(), pageDef.url());

        try {
            page.navigate(baseUrl + pageDef.url());
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // Wait for page to stabilize
            page.waitForTimeout(500);

            // Take screenshot
            Path screenshotPath = screenshotsDir.resolve(pageDef.id() + ".png");
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(screenshotPath)
                    .setFullPage(false));

            System.out.printf("  Saved: %s%n", screenshotPath);
        } catch (Exception e) {
            System.err.printf("  Failed to capture %s: %s%n", pageDef.name(), e.getMessage());
        }
    }

    public static void main(String[] args) {
        String baseUrl = System.getenv().getOrDefault("APP_URL", "http://localhost:8080");
        Path screenshotsDir = Paths.get("target", "user-manual", "screenshots");

        ScreenshotCapture capture = new ScreenshotCapture(baseUrl, screenshotsDir);
        capture.captureAll();
    }
}
