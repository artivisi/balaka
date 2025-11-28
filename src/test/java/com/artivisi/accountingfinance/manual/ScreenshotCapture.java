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

    // Seed data UUIDs for detail pages
    private static final String TEMPLATE_ID = "e0000000-0000-0000-0000-000000000001"; // Pendapatan Jasa Konsultasi
    private static final String TRANSACTION_ID = "a0000000-0000-0000-0000-000000000002"; // TRX-TEST-0002
    private static final String JOURNAL_ID = "b0000000-0000-0000-0000-000000000001"; // JE-TEST-0001
    private static final String CLIENT_ID = "c0500000-0000-0000-0000-000000000001"; // PT ABC Technology
    private static final String PROJECT_ID = "a0500000-0000-0000-0000-000000000001"; // Website Development ABC

    public static List<PageDefinition> getPageDefinitions() {
        return List.of(
            // Authentication
            new PageDefinition("login", "Halaman Login", "/login", false,
                    "Halaman login untuk masuk ke aplikasi", "pendahuluan"),

            // Dashboard
            new PageDefinition("dashboard", "Dashboard", "/dashboard", true,
                    "Tampilan utama dengan KPI keuangan bulanan", "laporan-harian"),

            // Chart of Accounts
            new PageDefinition("accounts-list", "Daftar Akun", "/accounts", true,
                    "Daftar semua akun dalam bagan akun", "setup-awal"),
            new PageDefinition("accounts-form", "Form Akun", "/accounts/new", true,
                    "Form untuk menambah atau mengubah akun", "setup-awal"),

            // Templates
            new PageDefinition("templates-list", "Daftar Template", "/templates", true,
                    "Daftar template jurnal dengan kategori dan pencarian", "kelola-template"),
            new PageDefinition("templates-detail", "Detail Template", "/templates/" + TEMPLATE_ID, true,
                    "Konfigurasi dan formula template", "kelola-template"),
            new PageDefinition("templates-form", "Form Template", "/templates/new", true,
                    "Form untuk membuat template baru dengan formula", "kelola-template"),

            // Transactions
            new PageDefinition("transactions-list", "Daftar Transaksi", "/transactions", true,
                    "Daftar transaksi dengan filter status, periode, dan proyek", "mencatat-pengeluaran"),
            new PageDefinition("transactions-detail", "Detail Transaksi", "/transactions/" + TRANSACTION_ID, true,
                    "Detail transaksi dengan jurnal dan audit trail", "mencatat-pendapatan"),
            new PageDefinition("transactions-form", "Form Transaksi", "/transactions/new", true,
                    "Form untuk membuat transaksi baru", "mencatat-pendapatan"),

            // Journal Entries
            new PageDefinition("journals-list", "Buku Besar", "/journals", true,
                    "Tampilan buku besar dengan saldo berjalan", "laporan-harian"),
            new PageDefinition("journals-detail", "Detail Jurnal", "/journals/" + JOURNAL_ID, true,
                    "Detail entri jurnal dengan dampak akun", "laporan-harian"),

            // Reports
            new PageDefinition("reports-trial-balance", "Neraca Saldo", "/reports/trial-balance", true,
                    "Laporan neraca saldo per tanggal", "laporan-bulanan"),
            new PageDefinition("reports-balance-sheet", "Neraca", "/reports/balance-sheet", true,
                    "Laporan posisi keuangan (Neraca)", "laporan-bulanan"),
            new PageDefinition("reports-income-statement", "Laba Rugi", "/reports/income-statement", true,
                    "Laporan laba rugi per periode", "laporan-bulanan"),

            // Amortization Schedules
            new PageDefinition("amortization-list", "Daftar Jadwal Amortisasi", "/amortization", true,
                    "Daftar jadwal amortisasi dengan filter tipe dan status", "jadwal-amortisasi"),
            new PageDefinition("amortization-form", "Form Jadwal Amortisasi", "/amortization/new", true,
                    "Form untuk membuat jadwal amortisasi baru", "jadwal-amortisasi"),

            // Clients
            new PageDefinition("clients-list", "Daftar Klien", "/clients", true,
                    "Daftar klien dengan pencarian", "kelola-klien"),
            new PageDefinition("clients-detail", "Detail Klien", "/clients/" + CLIENT_ID, true,
                    "Detail klien dengan daftar proyek", "kelola-klien"),
            new PageDefinition("clients-form", "Form Klien", "/clients/new", true,
                    "Form untuk menambah klien baru", "kelola-klien"),

            // Projects
            new PageDefinition("projects-list", "Daftar Proyek", "/projects", true,
                    "Daftar proyek dengan filter status dan klien", "tracking-proyek"),
            new PageDefinition("projects-detail", "Detail Proyek", "/projects/" + PROJECT_ID, true,
                    "Detail proyek dengan milestone dan termin pembayaran", "tracking-proyek"),
            new PageDefinition("projects-form", "Form Proyek", "/projects/new", true,
                    "Form untuk membuat proyek baru", "setup-proyek"),

            // Invoices
            new PageDefinition("invoices-list", "Daftar Invoice", "/invoices", true,
                    "Daftar invoice dengan filter status dan klien", "invoice-penagihan"),

            // Profitability Reports
            new PageDefinition("reports-project-profitability", "Profitabilitas Proyek", "/reports/project-profitability", true,
                    "Laporan profitabilitas per proyek", "analisis-profitabilitas"),
            new PageDefinition("reports-client-profitability", "Profitabilitas Klien", "/reports/client-profitability", true,
                    "Laporan profitabilitas per klien", "analisis-profitabilitas"),

            // Tax Reports
            new PageDefinition("reports-ppn-summary", "Ringkasan PPN", "/reports/ppn-summary", true,
                    "Laporan ringkasan PPN Keluaran dan Masukan", "laporan-pajak"),
            new PageDefinition("reports-pph23-withholding", "Pemotongan PPh 23", "/reports/pph23-withholding", true,
                    "Laporan pemotongan PPh 23 dari vendor", "laporan-pajak"),
            new PageDefinition("reports-tax-summary", "Ringkasan Pajak", "/reports/tax-summary", true,
                    "Overview semua akun pajak", "laporan-pajak"),

            // Employees
            new PageDefinition("employees-list", "Daftar Karyawan", "/employees", true,
                    "Daftar karyawan dengan filter status dan departemen", "kelola-karyawan"),
            new PageDefinition("employees-form", "Form Karyawan", "/employees/new", true,
                    "Form untuk menambah atau mengubah data karyawan", "kelola-karyawan"),

            // Payroll
            new PageDefinition("payroll-list", "Daftar Payroll", "/payroll", true,
                    "Daftar payroll run dengan filter periode dan status", "payroll-processing"),
            new PageDefinition("payroll-form", "Form Payroll", "/payroll/new", true,
                    "Form untuk membuat payroll run baru", "payroll-processing"),
            new PageDefinition("payroll-detail", "Detail Payroll", "/payroll", true,
                    "Detail payroll run dengan daftar karyawan dan perhitungan", "payroll-processing")
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
