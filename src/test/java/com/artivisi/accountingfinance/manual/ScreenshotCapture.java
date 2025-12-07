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

    // Seed data IDs for detail pages
    // Template from V004__app_seed_data.sql
    private static final String TEMPLATE_ID = "e0000000-0000-0000-0000-000000000001"; // Pendapatan Jasa Konsultasi
    // Test data from V905__profitability_test_data.sql (loaded via SPRING_FLYWAY_LOCATIONS)
    private static final String TRANSACTION_ID = "90500000-0000-0000-1000-000000000001"; // TRX-PRJ-T001-01
    private static final String CLIENT_CODE = "CLI-001"; // PT ABC Technology
    private static final String PROJECT_CODE = "PRJ-TEST-001"; // Website Development ABC

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

            // Journal Entries (Ledger View - read-only)
            new PageDefinition("journals-list", "Buku Besar", "/journals", true,
                    "Tampilan buku besar dengan saldo berjalan", "laporan-harian"),

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
            new PageDefinition("clients-detail", "Detail Klien", "/clients/" + CLIENT_CODE, true,
                    "Detail klien dengan daftar proyek", "kelola-klien"),
            new PageDefinition("clients-form", "Form Klien", "/clients/new", true,
                    "Form untuk menambah klien baru", "kelola-klien"),

            // Projects
            new PageDefinition("projects-list", "Daftar Proyek", "/projects", true,
                    "Daftar proyek dengan filter status dan klien", "tracking-proyek"),
            new PageDefinition("projects-detail", "Detail Proyek", "/projects/" + PROJECT_CODE, true,
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
                    "Detail payroll run dengan daftar karyawan dan perhitungan", "payroll-processing"),

            // User Management
            new PageDefinition("users-list", "Daftar Pengguna", "/users", true,
                    "Daftar pengguna dengan filter dan pencarian", "kelola-pengguna"),
            new PageDefinition("users-form", "Form Pengguna", "/users/new", true,
                    "Form untuk menambah atau mengubah pengguna", "kelola-pengguna"),
            new PageDefinition("users-detail", "Detail Pengguna", "/users", true,
                    "Detail pengguna dengan role dan hak akses", "kelola-pengguna"),

            // Self-Service
            new PageDefinition("self-service-payslips", "Slip Gaji Saya", "/self-service/payslips", true,
                    "Daftar slip gaji karyawan", "layanan-mandiri"),
            new PageDefinition("self-service-bukti-potong", "Bukti Potong Saya", "/self-service/bukti-potong", true,
                    "Bukti potong PPh 21 (1721-A1)", "layanan-mandiri"),
            new PageDefinition("self-service-profile", "Profil Saya", "/self-service/profile", true,
                    "Informasi profil karyawan", "layanan-mandiri"),

            // Inventory - Products
            new PageDefinition("products-list", "Daftar Produk", "/products", true,
                    "Daftar produk dengan pencarian dan filter kategori", "kelola-produk"),
            new PageDefinition("products-form", "Form Produk", "/products/new", true,
                    "Form untuk menambah atau mengubah produk", "kelola-produk"),

            // Inventory - Categories
            new PageDefinition("product-categories-list", "Kategori Produk", "/products/categories", true,
                    "Daftar kategori produk", "kelola-produk"),

            // Inventory - Stock
            new PageDefinition("stock-list", "Stok Barang", "/inventory/stock", true,
                    "Daftar stok dengan peringatan stok minimum", "kartu-stok"),

            // Inventory - Transactions
            new PageDefinition("inventory-transactions", "Transaksi Inventori", "/inventory/transactions", true,
                    "Daftar transaksi inventori dengan filter", "transaksi-inventori"),
            new PageDefinition("inventory-purchase", "Pembelian", "/inventory/purchase", true,
                    "Form pencatatan pembelian barang", "transaksi-inventori"),
            new PageDefinition("inventory-sale", "Penjualan", "/inventory/sale", true,
                    "Form pencatatan penjualan barang", "transaksi-inventori"),
            new PageDefinition("inventory-adjustment", "Penyesuaian Stok", "/inventory/adjustment", true,
                    "Form penyesuaian stok barang", "transaksi-inventori"),

            // Inventory - BOM
            new PageDefinition("bom-list", "Bill of Materials", "/inventory/bom", true,
                    "Daftar BOM dengan pencarian", "produksi-bom"),
            new PageDefinition("bom-form", "Form BOM", "/inventory/bom/create", true,
                    "Form untuk membuat BOM baru", "produksi-bom"),

            // Inventory - Production
            new PageDefinition("production-list", "Production Orders", "/inventory/production", true,
                    "Daftar production order dengan filter status", "produksi-bom"),
            new PageDefinition("production-form", "Form Production Order", "/inventory/production/create", true,
                    "Form untuk membuat production order baru", "produksi-bom"),

            // Inventory - Reports
            new PageDefinition("inventory-reports", "Laporan Persediaan", "/inventory/reports", true,
                    "Daftar laporan persediaan yang tersedia", "kartu-stok"),
            new PageDefinition("inventory-reports-profitability", "Profitabilitas Produk", "/inventory/reports/profitability", true,
                    "Analisis margin dan profit per produk", "analisis-profitabilitas-produk"),
            new PageDefinition("inventory-stock-balance", "Laporan Saldo Stok", "/inventory/reports/stock-balance", true,
                    "Laporan saldo stok per tanggal", "kartu-stok"),
            new PageDefinition("inventory-stock-movement", "Laporan Pergerakan Stok", "/inventory/reports/stock-movement", true,
                    "Laporan pergerakan stok per periode", "kartu-stok"),

            // Fixed Assets
            new PageDefinition("assets-list", "Daftar Aset Tetap", "/assets", true,
                    "Daftar aset tetap dengan filter status dan kategori", "kelola-aset"),
            new PageDefinition("assets-form", "Form Aset Tetap", "/assets/new", true,
                    "Form untuk menambah aset tetap baru", "kelola-aset"),
            new PageDefinition("assets-depreciation", "Penyusutan Aset", "/assets/depreciation", true,
                    "Daftar penyusutan aset dengan filter periode", "penyusutan-aset"),

            // Asset Categories
            new PageDefinition("asset-categories-list", "Kategori Aset", "/asset-categories", true,
                    "Daftar kategori aset dengan pengaturan penyusutan default", "kelola-aset"),

            // Depreciation Report
            new PageDefinition("reports-depreciation", "Laporan Penyusutan", "/reports/depreciation", true,
                    "Laporan penyusutan untuk SPT Tahunan (Lampiran 1A)", "laporan-penyusutan"),

            // Fiscal Year Closing
            new PageDefinition("reports-fiscal-closing", "Penutupan Tahun Buku", "/reports/fiscal-closing", true,
                    "Proses penutupan tahun buku dengan jurnal penutup", "penutupan-tahun-buku"),

            // Calculators
            new PageDefinition("bpjs-calculator", "Kalkulator BPJS", "/bpjs-calculator", true,
                    "Kalkulator iuran BPJS Kesehatan dan Ketenagakerjaan", "kalkulator-bpjs"),
            new PageDefinition("pph21-calculator", "Kalkulator PPh 21", "/pph21-calculator", true,
                    "Kalkulator pajak penghasilan karyawan", "kalkulator-pph21"),

            // Salary Components
            new PageDefinition("salary-components-list", "Komponen Gaji", "/salary-components", true,
                    "Daftar komponen gaji dengan tipe dan rate default", "komponen-gaji"),
            new PageDefinition("salary-components-form", "Form Komponen Gaji", "/salary-components/new", true,
                    "Form untuk menambah komponen gaji baru", "komponen-gaji"),

            // Settings - Security
            new PageDefinition("settings-audit-logs", "Log Audit Keamanan", "/settings/audit-logs", true,
                    "Daftar log audit keamanan dengan filter event dan tanggal", "keamanan"),
            new PageDefinition("settings-data-subjects", "Hak Subjek Data", "/settings/data-subjects", true,
                    "Daftar subjek data untuk kepatuhan GDPR/UU PDP", "kebijakan-data"),
            new PageDefinition("settings-privacy", "Kebijakan Privasi", "/settings/privacy", false,
                    "Halaman kebijakan privasi aplikasi", "kebijakan-data"),

            // Fiscal Periods
            new PageDefinition("fiscal-periods-list", "Periode Fiskal", "/fiscal-periods", true,
                    "Daftar periode fiskal dengan status tutup buku", "kelola-periode-fiskal"),

            // Tax Calendar
            new PageDefinition("tax-calendar", "Kalender Pajak", "/tax-calendar", true,
                    "Kalender deadline pajak bulanan", "kalender-pajak"),
            new PageDefinition("tax-calendar-yearly", "Kalender Pajak Tahunan", "/tax-calendar/yearly", true,
                    "Overview deadline pajak setahun penuh", "kalender-pajak")
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
