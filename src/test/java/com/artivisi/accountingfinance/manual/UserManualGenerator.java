package com.artivisi.accountingfinance.manual;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.util.data.MutableDataSet;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates HTML user manual from Markdown files.
 */
public class UserManualGenerator {

    private final Path markdownDir;
    private final Path outputDir;
    private final Path screenshotsDir;
    private final Parser parser;
    private final HtmlRenderer renderer;

    public UserManualGenerator(Path markdownDir, Path outputDir, Path screenshotsDir) {
        this.markdownDir = markdownDir;
        this.outputDir = outputDir;
        this.screenshotsDir = screenshotsDir;

        // Configure Flexmark with tables extension
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, List.of(TablesExtension.create()));

        this.parser = Parser.builder(options).build();
        this.renderer = HtmlRenderer.builder(options).build();
    }

    /**
     * Section definition for manual structure
     */
    public record Section(
            String id,
            String title,
            String markdownFile,
            List<String> screenshots
    ) {}

    /**
     * Section group for collapsible sidebar navigation
     */
    public record SectionGroup(
            String id,
            String title,
            String icon,
            List<Section> sections
    ) {}

    public static List<SectionGroup> getSectionGroups() {
        // New 12-section structure per user-manual-creation-guidelines.md
        // 1. Setup Awal - sysadmin audience
        // 2. Tutorial Akuntansi - crown jewel, business owner audience
        // 3. Aset Tetap - depreciation
        // 4. Perpajakan - tax compliance
        // 5. Penggajian - payroll & employee
        // 6. Pengantar Industri - industry overview
        // 7-10. Industry-specific sections
        // 11. Keamanan - security & compliance
        // 12. Lampiran - appendix
        return List.of(
            // 1. SETUP AWAL & ADMINISTRASI
            new SectionGroup("setup-awal", "Setup Awal & Administrasi", "M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z M15 12a3 3 0 11-6 0 3 3 0 016 0z", List.of(
                new Section("setup-awal", "Setup Awal", "01-setup-awal.md", List.of("login", "dashboard", "accounts-list", "accounts-form")),
                new Section("import-seed", "Import Seed Data", "01-setup-awal.md", List.of()),
                new Section("user-management", "User Management", "01-setup-awal.md", List.of("users-list", "users-form")),
                new Section("telegram-setup", "Telegram Integration", "01-setup-awal.md", List.of())
            )),

            // 2. TUTORIAL DASAR AKUNTANSI - Crown Jewel
            new SectionGroup("tutorial-akuntansi", "Tutorial Dasar Akuntansi", "M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253", List.of(
                new Section("konsep-dasar", "Konsep Dasar Akuntansi", "02-tutorial-akuntansi.md", List.of()),
                new Section("siklus-akuntansi", "Siklus Akuntansi", "02-tutorial-akuntansi.md", List.of()),
                new Section("transaksi-harian", "Transaksi Harian", "02-tutorial-akuntansi.md", List.of("service/transaction-list")),
                new Section("jurnal-buku-besar", "Jurnal & Buku Besar", "02-tutorial-akuntansi.md", List.of("service/journals-list")),
                new Section("penyesuaian", "Penyesuaian", "02-tutorial-akuntansi.md", List.of("amortization-list", "amortization-form")),
                new Section("laporan-keuangan", "Laporan Keuangan", "02-tutorial-akuntansi.md", List.of("service/report-trial-balance", "service/report-balance-sheet", "service/report-income-statement")),
                new Section("tutup-buku", "Tutup Buku", "02-tutorial-akuntansi.md", List.of("reports-fiscal-closing"))
            )),

            // 3. ASET TETAP
            new SectionGroup("aset-tetap", "Aset Tetap", "M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4", List.of(
                new Section("konsep-depresiasi", "Konsep Depresiasi", "03-aset-tetap.md", List.of()),
                new Section("kategori-aset", "Kategori Aset", "03-aset-tetap.md", List.of("asset-categories-list")),
                new Section("pencatatan-aset", "Pencatatan Aset", "03-aset-tetap.md", List.of("assets-list", "assets-form")),
                new Section("jadwal-depresiasi", "Jadwal Depresiasi", "03-aset-tetap.md", List.of("assets-depreciation", "reports-depreciation"))
            )),

            // 4. PERPAJAKAN
            new SectionGroup("perpajakan", "Perpajakan", "M9 14l6-6m-5.5.5h.01m4.99 5h.01M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16l3.5-2 3.5 2 3.5-2 3.5 2z", List.of(
                new Section("jenis-pajak", "Jenis Pajak di Indonesia", "04-perpajakan.md", List.of()),
                new Section("transaksi-ppn", "Transaksi PPN", "04-perpajakan.md", List.of("reports-ppn-summary")),
                new Section("transaksi-pph", "Transaksi PPh", "04-perpajakan.md", List.of("reports-pph23-withholding", "reports-tax-summary")),
                new Section("periode-fiskal", "Periode Fiskal", "04-perpajakan.md", List.of("fiscal-periods-list")),
                new Section("kalender-pajak", "Kalender Pajak", "04-perpajakan.md", List.of("tax-calendar", "tax-calendar-yearly"))
            )),

            // 5. PENGGAJIAN
            new SectionGroup("penggajian", "Penggajian", "M17 9V7a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2m2 4h10a2 2 0 002-2v-6a2 2 0 00-2-2H9a2 2 0 00-2 2v6a2 2 0 002 2zm7-5a2 2 0 11-4 0 2 2 0 014 0z", List.of(
                new Section("setup-komponen-gaji", "Setup Komponen Gaji", "05-penggajian.md", List.of("salary-components-list", "salary-components-form")),
                new Section("kelola-karyawan", "Kelola Karyawan", "05-penggajian.md", List.of("employees-list", "employees-form")),
                new Section("bpjs", "BPJS", "05-penggajian.md", List.of("bpjs-calculator")),
                new Section("pph21-karyawan", "PPh 21 Karyawan", "05-penggajian.md", List.of("pph21-calculator")),
                new Section("kapan-menggunakan-payroll", "Kapan Menggunakan Fitur Payroll?", "05-penggajian.md", List.of()),
                new Section("proses-penggajian", "Proses Penggajian", "05-penggajian.md", List.of("payroll-list", "payroll-form", "payroll-detail")),
                new Section("pembayaran-kewajiban", "Pembayaran Kewajiban Payroll", "05-penggajian.md", List.of("payroll-lifecycle-bayar-gaji-form", "payroll-lifecycle-bayar-bpjs-form", "payroll-lifecycle-setor-pph21-form")),
                new Section("layanan-mandiri", "Layanan Mandiri Karyawan", "05-penggajian.md", List.of("self-service-payslips", "self-service-bukti-potong", "self-service-profile")),
                new Section("bukti-potong-pph21", "Bukti Potong PPh 21", "05-penggajian.md", List.of()),
                new Section("contoh-payroll-lengkap", "Contoh Lengkap: Proses Payroll Januari 2025", "05-penggajian.md", List.of()),
                new Section("tips-penggajian", "Tips Penggajian", "05-penggajian.md", List.of())
            )),

            // 6. PENGANTAR INDUSTRI
            new SectionGroup("pengantar-industri", "Pengantar Industri", "M21 12a9 9 0 01-9 9m9-9a9 9 0 00-9-9m9 9H3m9 9a9 9 0 01-9-9m9 9c1.657 0 3-4.03 3-9s-1.343-9-3-9m0 18c-1.657 0-3-4.03-3-9s1.343-9 3-9m-9 9a9 9 0 019-9", List.of(
                new Section("jenis-industri", "Jenis Industri", "06-pengantar-industri.md", List.of()),
                new Section("industri-didukung", "Industri yang Didukung", "06-pengantar-industri.md", List.of()),
                new Section("perbedaan-praktik", "Perbedaan Praktik Akuntansi", "06-pengantar-industri.md", List.of())
            )),

            // 7. INDUSTRI JASA (SERVICE)
            new SectionGroup("industri-jasa", "Industri Jasa", "M21 13.255A23.931 23.931 0 0112 15c-3.183 0-6.22-.62-9-1.745M16 6V4a2 2 0 00-2-2h-4a2 2 0 00-2 2v2m4 6h.01M5 20h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z", List.of(
                new Section("karakteristik-jasa", "Karakteristik Industri Jasa", "07-industri-jasa.md", List.of()),
                new Section("client-management", "Client Management", "07-industri-jasa.md", List.of("service/clients-list")),
                new Section("project-management", "Project Management", "07-industri-jasa.md", List.of("service/projects-list")),
                new Section("template-jasa", "Template Transaksi Jasa", "07-industri-jasa.md", List.of("service/templates-list", "service/templates-detail")),
                new Section("invoice-penagihan", "Invoice & Penagihan", "07-industri-jasa.md", List.of()),
                new Section("profitabilitas-proyek", "Profitabilitas Proyek", "07-industri-jasa.md", List.of())
            )),

            // 8. INDUSTRI DAGANG (TRADING/SELLER)
            new SectionGroup("industri-dagang", "Industri Dagang", "M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z", List.of(
                new Section("karakteristik-dagang", "Karakteristik Industri Dagang", "08-industri-dagang.md", List.of()),
                new Section("manajemen-produk", "Manajemen Produk", "08-industri-dagang.md", List.of("seller/product-list")),
                new Section("metode-persediaan", "Metode Penilaian Persediaan", "08-industri-dagang.md", List.of()),
                new Section("transaksi-pembelian", "Transaksi Pembelian", "08-industri-dagang.md", List.of()),
                new Section("transaksi-penjualan", "Transaksi Penjualan", "08-industri-dagang.md", List.of()),
                new Section("laporan-persediaan", "Laporan Persediaan", "08-industri-dagang.md", List.of("seller/report-stock-balance", "seller/report-stock-movement")),
                new Section("profitabilitas-produk", "Profitabilitas Produk", "08-industri-dagang.md", List.of("seller/report-product-profitability"))
            )),

            // 9. INDUSTRI MANUFAKTUR
            new SectionGroup("industri-manufaktur", "Industri Manufaktur", "M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z", List.of(
                new Section("karakteristik-manufaktur", "Karakteristik Manufaktur", "09-industri-manufaktur.md", List.of()),
                new Section("bill-of-materials", "Bill of Materials (BOM)", "09-industri-manufaktur.md", List.of("coffee/bom-list", "coffee/bom-detail-croissant")),
                new Section("production-order", "Production Order", "09-industri-manufaktur.md", List.of("coffee/production-order-list", "coffee/production-order-detail-croissant")),
                new Section("kalkulasi-biaya", "Kalkulasi Biaya Produksi", "09-industri-manufaktur.md", List.of("coffee/report-product-profitability")),
                new Section("laporan-produksi", "Laporan Produksi", "09-industri-manufaktur.md", List.of("coffee/report-production-list", "coffee/report-stock-balance"))
            )),

            // 10. INDUSTRI PENDIDIKAN
            new SectionGroup("industri-pendidikan", "Industri Pendidikan", "M12 14l9-5-9-5-9 5 9 5z M12 14l6.16-3.422a12.083 12.083 0 01.665 6.479A11.952 11.952 0 0012 20.055a11.952 11.952 0 00-6.824-2.998 12.078 12.078 0 01.665-6.479L12 14z M12 14l9-5-9-5-9 5 9 5zm0 0l6.16-3.422a12.083 12.083 0 01.665 6.479A11.952 11.952 0 0012 20.055a11.952 11.952 0 00-6.824-2.998 12.078 12.078 0 01.665-6.479L12 14zm-4 6v-7.5l4-2.222", List.of(
                new Section("karakteristik-pendidikan", "Karakteristik Industri Pendidikan", "10-industri-pendidikan.md", List.of()),
                new Section("coa-pendidikan", "Chart of Accounts Khusus Pendidikan", "10-industri-pendidikan.md", List.of()),
                new Section("template-pendidikan", "Template Transaksi Pendidikan", "10-industri-pendidikan.md", List.of()),
                new Section("transaksi-harian", "Transaksi Harian: Contoh Praktis", "10-industri-pendidikan.md", List.of()),
                new Section("laporan-keuangan", "Laporan Keuangan Khusus Pendidikan", "10-industri-pendidikan.md", List.of()),
                new Section("laporan-piutang", "Laporan Piutang Mahasiswa", "10-industri-pendidikan.md", List.of())
            )),

            // 11. KEAMANAN & KEPATUHAN DATA
            new SectionGroup("keamanan", "Keamanan & Kepatuhan Data", "M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z", List.of(
                new Section("enkripsi-data", "Enkripsi Dokumen & PII", "11-keamanan-kepatuhan.md", List.of()),
                new Section("audit-log", "Audit Log Keamanan", "11-keamanan-kepatuhan.md", List.of("settings-audit-logs")),
                new Section("kebijakan-data", "Kebijakan Data (GDPR/UU PDP)", "11-keamanan-kepatuhan.md", List.of("settings-data-subjects", "settings-privacy")),
                new Section("ekspor-data", "Ekspor Data Subjek (DSAR)", "11-keamanan-kepatuhan.md", List.of())
            )),

            // 12. REKONSILIASI BANK
            new SectionGroup("rekonsiliasi-bank", "Rekonsiliasi Bank", "M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z", List.of(
                new Section("konsep-rekonsiliasi", "Konsep Rekonsiliasi Bank", "14-rekonsiliasi-bank.md", List.of("bank-recon/landing-page")),
                new Section("konfigurasi-parser", "Konfigurasi Parser Bank", "14-rekonsiliasi-bank.md", List.of("bank-recon/parser-configs", "bank-recon/parser-config-form")),
                new Section("import-mutasi", "Import Mutasi Bank", "14-rekonsiliasi-bank.md", List.of("bank-recon/import-form", "bank-recon/statements-list", "bank-recon/statement-detail")),
                new Section("proses-rekonsiliasi", "Proses Rekonsiliasi", "14-rekonsiliasi-bank.md", List.of("bank-recon/recon-form", "bank-recon/recon-detail", "bank-recon/recon-auto-match")),
                new Section("laporan-rekonsiliasi", "Laporan Rekonsiliasi", "14-rekonsiliasi-bank.md", List.of("bank-recon/recon-report")),
                new Section("contoh-kasus-rekon", "Contoh Kasus Lengkap", "14-rekonsiliasi-bank.md", List.of())
            )),

            // 14. BANTUAN AI
            new SectionGroup("bantuan-ai", "Bantuan AI", "M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z", List.of(
                new Section("bantuan-ai", "Bantuan AI untuk Pencatatan Transaksi", "13-bantuan-ai.md", List.of("ai-transaction/00-device-authorization", "ai-transaction/04-transactions-list")),
                new Section("publikasi-analisis", "Publikasi Laporan Analisis", "13-bantuan-ai.md", List.of("analysis-reports/list", "analysis-reports/detail-top", "analysis-reports/detail-bottom"))
            )),

            // 15. LAMPIRAN (last)
            new SectionGroup("lampiran", "Lampiran", "M5 8h14M5 8a2 2 0 110-4h14a2 2 0 110 4M5 8v10a2 2 0 002 2h10a2 2 0 002-2V8m-9 4h4", List.of(
                new Section("glosarium", "Glosarium", "12-lampiran-glosarium.md", List.of()),
                new Section("referensi-template", "Referensi Template", "12-lampiran-template.md", List.of("service/templates-list", "service/templates-metadata-detail", "service/templates-metadata-form")),
                new Section("referensi-amortisasi", "Referensi Amortisasi & Depresiasi", "12-lampiran-amortisasi.md", List.of()),
                new Section("referensi-akun", "Referensi Akun", "12-lampiran-akun.md", List.of("accounts-list"))
            ))
        );
    }

    /**
     * Get all sections (flattened from groups) for backward compatibility
     */
    public static List<Section> getSections() {
        return getSectionGroups().stream()
                .flatMap(g -> g.sections().stream())
                .toList();
    }

    /**
     * Generates the complete user manual
     */
    public void generate() throws IOException {
        // Create output directories
        Files.createDirectories(outputDir);
        Path outputScreenshotsDir = outputDir.resolve("screenshots");
        Files.createDirectories(outputScreenshotsDir);

        // Copy screenshots recursively if they exist
        if (Files.exists(screenshotsDir)) {
            copyScreenshotsRecursively(screenshotsDir, outputScreenshotsDir);
        }

        // Generate HTML
        String html = generateHtml();
        Path indexPath = outputDir.resolve("index.html");
        Files.writeString(indexPath, html, StandardCharsets.UTF_8);

        System.out.println("User manual generated: " + indexPath);
    }

    /**
     * Recursively copy screenshot directories and files
     */
    private void copyScreenshotsRecursively(Path source, Path target) throws IOException {
        try (var stream = Files.walk(source)) {
            stream.forEach(sourcePath -> {
                try {
                    Path targetPath = target.resolve(source.relativize(sourcePath));
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else if (sourcePath.toString().endsWith(".png")) {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    System.err.println("Failed to copy: " + sourcePath + " - " + e.getMessage());
                }
            });
        }
    }

    private String generateHtml() throws IOException {
        StringBuilder sectionsHtml = new StringBuilder();
        StringBuilder tocHtml = new StringBuilder();

        int groupIndex = 0;
        for (SectionGroup group : getSectionGroups()) {
            groupIndex++;

            // Build collapsible group header for TOC
            tocHtml.append(String.format("""
                <li class="mb-1">
                    <button onclick="toggleGroup('%s')" class="w-full flex items-center justify-between text-sm font-medium text-gray-800 hover:text-primary-600 hover:bg-primary-50 px-3 py-2 rounded-lg transition-colors">
                        <div class="flex items-center">
                            <svg class="w-5 h-5 mr-2 text-primary-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="%s"/>
                            </svg>
                            <span>%s</span>
                        </div>
                        <svg id="chevron-%s" class="w-4 h-4 text-gray-400 transform transition-transform" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/>
                        </svg>
                    </button>
                    <ul id="group-%s" class="ml-4 mt-1 space-y-1 overflow-hidden transition-all duration-200" style="max-height: 0;">
                """, group.id(), group.icon(), group.title(), group.id(), group.id()));

            // Build sections within group
            int sectionIndex = 0;
            for (Section section : group.sections()) {
                sectionIndex++;
                String sectionNumber = groupIndex + "." + sectionIndex;

                // Add section link to TOC
                tocHtml.append(String.format("""
                        <li>
                            <a href="#%s" class="flex items-center text-sm text-gray-600 hover:text-primary-600 hover:bg-primary-50 px-3 py-1.5 rounded-lg transition-colors">
                                <span class="w-5 h-5 flex items-center justify-center text-xs text-gray-400 mr-2">%s</span>
                                <span class="truncate">%s</span>
                            </a>
                        </li>
                    """, section.id(), sectionNumber, section.title()));

                // Parse markdown content - extract only this specific section
                String fullMarkdown = readMarkdownFile(section.markdownFile());
                String sectionContent = extractSectionContent(fullMarkdown, section.title());
                String contentHtml = convertMarkdownToHtml(sectionContent);

                // Build screenshots HTML
                String screenshotsHtml = buildScreenshotsHtml(section.screenshots());

                // Build section HTML
                sectionsHtml.append(String.format("""
                    <section id="%s" class="bg-white rounded-lg shadow-sm border border-gray-200 mb-8 overflow-hidden">
                        <div class="bg-gradient-to-r from-primary-600 to-primary-700 px-6 py-4">
                            <div class="flex items-center">
                                <span class="w-10 h-10 flex items-center justify-center bg-white/20 text-white rounded-full text-lg font-bold mr-4">%s</span>
                                <h2 class="text-xl font-bold text-white">%s</h2>
                            </div>
                        </div>
                        <div class="p-6">
                            <div class="prose max-w-none">
                                %s
                            </div>
                            %s
                        </div>
                    </section>
                    """, section.id(), sectionNumber, section.title(), contentHtml, screenshotsHtml));
            }

            // Close group list
            tocHtml.append("""
                    </ul>
                </li>
                """);
        }

        // Read and fill template
        return getHtmlTemplate()
                .replace("{{TOC}}", tocHtml.toString())
                .replace("{{SECTIONS}}", sectionsHtml.toString())
                .replace("{{DATE}}", LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy", new Locale("id", "ID"))));
    }

    private String readMarkdownFile(String filename) throws IOException {
        Path filePath = markdownDir.resolve(filename);
        if (Files.exists(filePath)) {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        }
        return "Konten belum tersedia.";
    }

    private String convertMarkdownToHtml(String markdown) {
        // Parse and render the markdown content directly
        // (H1 heading already removed in extractSectionContent)
        Node document = parser.parse(markdown);
        return renderer.render(document);
    }
    
    /**
     * Extract a specific H2 section from the markdown content.
     * Special handling for sections that should aggregate multiple H2 sections.
     * If sectionTitle matches the file's H1 title exactly, returns content before first H2.
     * Otherwise, returns the content between the specified H2 heading and the next H2 heading (or end of file).
     * If no H2 match is found, tries to find content from H1 to a specific H2 (for aggregate sections).
     */
    private String extractSectionContent(String markdown, String sectionTitle) {
        if (sectionTitle == null || sectionTitle.isEmpty()) {
            // If no specific section title provided, return full content
            return markdown;
        }
        
        // Get the H1 title
        Pattern h1Pattern = Pattern.compile("^#\\s+([^\\n]+)", Pattern.MULTILINE);
        Matcher h1Matcher = h1Pattern.matcher(markdown);
        String h1Title = "";
        if (h1Matcher.find()) {
            h1Title = h1Matcher.group(1).trim();
        }
        
        // Split by H2 headings to get all sections
        String[] sections = markdown.split("(?m)^## ");
        
        // First, try to find exact H2 match
        for (int i = 1; i < sections.length; i++) {
            String section = sections[i];
            String firstLine = section.split("\n", 2)[0].trim();
            
            // Match the section title using flexible matching
            if (titlesMatch(sectionTitle, firstLine)) {
                // Return content after the heading, including subsections (###)
                String[] parts = section.split("\n", 2);
                return parts.length > 1 ? parts[1].trim() : "";
            }
        }
        
        // Check if section title exactly matches H1 title - if so, return FULL content
        if (sectionTitle.equalsIgnoreCase(h1Title.trim())) {
            // Return full markdown content (entire document)
            return markdown;
        }
        
        // For sections like "Konsep Dasar Akuntansi" that should aggregate content
        // from the start until a specific H2, try to find the stopping point
        // by looking for an H2 that matches part of the next expected section
        if (sections.length > 1) {
            // Return content from after H1 until the first H2
            // This handles cases where a section should include intro content plus first few H2s
            String[] parts = markdown.split("(?m)^## ", 2);
            if (parts.length > 1) {
                // Get intro content (after H1, before first H2)
                String intro = parts[0].replaceFirst("^#\\s+[^\\n]+\\n*", "").trim();
                
                // Add the first few H2 sections until we hit a section that should be separate
                StringBuilder aggregated = new StringBuilder(intro);
                
                for (int i = 1; i < sections.length; i++) {
                    String section = sections[i];
                    String h2Title = section.split("\n", 2)[0].trim();
                    
                    // Stop aggregating if we find a section that should be standalone
                    // (like "Siklus Akuntansi", "Transaksi Harian", etc.)
                    if (h2Title.contains("Siklus") || h2Title.contains("Transaksi") || 
                        h2Title.contains("Jurnal") || h2Title.contains("Penyesuaian") || 
                        h2Title.contains("Laporan") || h2Title.contains("Tutup Buku")) {
                        break;
                    }
                    
                    // Include this H2 section in the aggregate
                    aggregated.append("\n\n---\n\n## ").append(section);
                }
                
                return aggregated.toString();
            }
        }
        
        // Section not found - return empty
        return "";
    }
    
    /**
     * Check if two titles match using flexible matching rules:
     * 1. Exact match (case-insensitive)
     * 2. Contains match (either contains the other)
     * 3. Keyword overlap match (ALL significant words from shorter title must be in longer title)
     */
    private boolean titlesMatch(String title1, String title2) {
        if (title1 == null || title2 == null) {
            return false;
        }
        
        String t1 = title1.toLowerCase().trim();
        String t2 = title2.toLowerCase().trim();
        
        // Exact match
        if (t1.equals(t2)) {
            return true;
        }
        
        // Contains match (whole word boundary)
        if (t1.contains(t2) || t2.contains(t1)) {
            return true;
        }
        
        // Extract significant words (length >= 4) from both titles
        String[] words1 = t1.split("\\s+");
        String[] words2 = t2.split("\\s+");
        
        Set<String> significantWords1 = new HashSet<>();
        Set<String> significantWords2 = new HashSet<>();
        
        for (String word : words1) {
            if (word.length() >= 4) {
                significantWords1.add(word);
            }
        }
        
        for (String word : words2) {
            if (word.length() >= 4) {
                significantWords2.add(word);
            }
        }
        
        // Find the shorter and longer sets
        Set<String> shorter = significantWords1.size() <= significantWords2.size() ? significantWords1 : significantWords2;
        Set<String> longer = significantWords1.size() > significantWords2.size() ? significantWords1 : significantWords2;
        
        // ALL significant words from shorter title must be present in longer title
        // This ensures "Siklus Akuntansi" doesn't match "Persamaan Dasar Akuntansi"
        // but "Import Seed Data" still matches "Import Industry Seed Data"
        if (shorter.isEmpty()) {
            return false;
        }
        
        return longer.containsAll(shorter);
    }

    private String buildScreenshotsHtml(List<String> screenshotIds) {
        if (screenshotIds.isEmpty()) {
            return "";
        }

        StringBuilder html = new StringBuilder();
        html.append("""
            <div class="mt-8 border-t border-gray-200 pt-6">
                <h3 class="text-lg font-semibold text-gray-800 mb-4">Tampilan Layar</h3>
                <div class="space-y-6">
            """);

        for (String id : screenshotIds) {
            var pageDef = ScreenshotCapture.getPageDefinitions().stream()
                    .filter(p -> p.id().equals(id))
                    .findFirst();

            String name = pageDef.map(ScreenshotCapture.PageDefinition::name).orElse(id);
            String description = pageDef.map(ScreenshotCapture.PageDefinition::description).orElse("");

            Path screenshotFile = outputDir.resolve("screenshots").resolve(id + ".png");
            boolean exists = Files.exists(screenshotFile);

            if (exists) {
                html.append(String.format("""
                    <div class="screenshot-container">
                        <div class="flex items-center mb-2">
                            <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800 mr-2">
                                %s
                            </span>
                        </div>
                        <a href="screenshots/%s.png" target="_blank" class="block">
                            <img src="screenshots/%s.png" alt="%s" class="w-full rounded-lg border border-gray-200 shadow-sm hover:shadow-lg transition-shadow cursor-zoom-in">
                        </a>
                        <p class="mt-2 text-sm text-gray-600">%s</p>
                    </div>
                    """, name, id, id, name, description));
            } else {
                html.append(String.format("""
                    <div class="screenshot-container">
                        <div class="flex items-center mb-2">
                            <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-600 mr-2">
                                %s
                            </span>
                        </div>
                        <div class="bg-gray-100 rounded-lg p-8 text-center border border-gray-200">
                            <svg class="w-12 h-12 mx-auto text-gray-400 mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"/>
                            </svg>
                            <p class="text-sm text-gray-500">Screenshot belum tersedia</p>
                            <p class="text-xs text-gray-400 mt-1">%s</p>
                        </div>
                    </div>
                    """, name, description));
            }
        }

        html.append("""
                </div>
            </div>
            """);

        return html.toString();
    }

    private String getHtmlTemplate() {
        return """
            <!DOCTYPE html>
            <html lang="id">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Panduan Pengguna - Aplikasi Akunting</title>
                <script src="https://cdn.tailwindcss.com"></script>
                <script>
                    tailwind.config = {
                        theme: {
                            extend: {
                                colors: {
                                    primary: {
                                        50: '#eff6ff', 100: '#dbeafe', 200: '#bfdbfe', 300: '#93c5fd',
                                        400: '#60a5fa', 500: '#3b82f6', 600: '#2563eb', 700: '#1d4ed8',
                                        800: '#1e40af', 900: '#1e3a8a',
                                    }
                                }
                            }
                        }
                    }
                </script>
                <style>
                    .prose h2 { font-size: 1.5rem; font-weight: 700; margin-top: 2rem; margin-bottom: 1rem; color: #1e3a8a; }
                    .prose h3 { font-size: 1.25rem; font-weight: 600; margin-top: 1.5rem; margin-bottom: 0.75rem; color: #374151; }
                    .prose h4 { font-size: 1.125rem; font-weight: 600; margin-top: 1.5rem; margin-bottom: 0.5rem; color: #1f2937; }
                    .prose h5 { font-size: 1rem; font-weight: 600; margin-top: 1.25rem; margin-bottom: 0.5rem; color: #374151; border-bottom: 1px solid #e5e7eb; padding-bottom: 0.25rem; }
                    .prose hr { border: none; border-top: 2px solid #e5e7eb; margin: 2rem 0; }
                    .prose p { margin-bottom: 1rem; line-height: 1.75; }
                    .prose ul, .prose ol { margin-bottom: 1rem; padding-left: 1.5rem; }
                    .prose ul { list-style-type: disc; }
                    .prose ol { list-style-type: decimal; }
                    .prose li { margin-bottom: 0.5rem; }
                    .prose table { width: 100%; border-collapse: collapse; margin: 1rem 0; }
                    .prose th, .prose td { border: 1px solid #e5e7eb; padding: 0.75rem; text-align: left; }
                    .prose th { background-color: #f9fafb; font-weight: 600; }
                    .prose code { background-color: #f3f4f6; padding: 0.125rem 0.375rem; border-radius: 0.25rem; font-size: 0.875rem; }
                    .prose pre { background-color: #1f2937; color: #e5e7eb; padding: 1rem; border-radius: 0.5rem; overflow-x: auto; margin: 1rem 0; }
                    .prose pre code { background: none; padding: 0; }
                    .prose strong { font-weight: 600; }
                    .prose blockquote { border-left: 4px solid #3b82f6; padding-left: 1rem; margin: 1rem 0; color: #4b5563; font-style: italic; }
                    .screenshot-container { margin: 1.5rem 0; }
                    @media print {
                        .no-print { display: none !important; }
                        .prose { font-size: 12pt; }
                        section { page-break-inside: avoid; }
                    }
                </style>
            </head>
            <body class="bg-gray-50 min-h-screen">
                <header class="bg-primary-700 text-white shadow-lg sticky top-0 z-50 no-print">
                    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                        <div class="flex items-center justify-between h-16">
                            <div class="flex items-center">
                                <svg class="w-8 h-8 mr-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/>
                                </svg>
                                <h1 class="text-xl font-bold">Panduan Pengguna - Aplikasi Akunting</h1>
                            </div>
                            <div class="flex items-center space-x-4 text-sm">
                                <span class="hidden sm:inline">{{DATE}}</span>
                                <button onclick="window.print()" class="px-3 py-1 bg-primary-600 rounded hover:bg-primary-500 transition">Cetak</button>
                            </div>
                        </div>
                    </div>
                </header>

                <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
                    <div class="lg:grid lg:grid-cols-12 lg:gap-8">
                        <aside class="hidden lg:block lg:col-span-3 no-print">
                            <nav class="sticky top-24 bg-white rounded-lg shadow-sm border border-gray-200 p-4 max-h-[calc(100vh-8rem)] overflow-y-auto">
                                <h2 class="text-sm font-semibold text-gray-500 uppercase tracking-wider mb-4">Daftar Isi</h2>
                                <ul class="space-y-2">
                                    {{TOC}}
                                </ul>
                            </nav>
                        </aside>

                        <main class="lg:col-span-9">
                            {{SECTIONS}}

                            <footer class="text-center text-sm text-gray-500 py-8">
                                <p>&copy; 2025 Aplikasi Akunting - ArtiVisi</p>
                                <p class="mt-1">Dokumentasi dibuat secara otomatis</p>
                            </footer>
                        </main>
                    </div>
                </div>

                <script>
                    // Toggle group expand/collapse
                    function toggleGroup(groupId) {
                        const group = document.getElementById('group-' + groupId);
                        const chevron = document.getElementById('chevron-' + groupId);
                        const isOpen = group.style.maxHeight !== '0px';

                        if (isOpen) {
                            group.style.maxHeight = '0';
                            chevron.classList.remove('rotate-180');
                        } else {
                            group.style.maxHeight = group.scrollHeight + 'px';
                            chevron.classList.add('rotate-180');
                        }
                    }

                    // Expand all groups on page load for better UX
                    document.addEventListener('DOMContentLoaded', function() {
                        // Expand first group (Pengantar) by default
                        toggleGroup('pengantar');
                    });

                    // Smooth scroll for anchor links
                    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
                        anchor.addEventListener('click', function (e) {
                            e.preventDefault();
                            const targetId = this.getAttribute('href').substring(1);
                            const target = document.getElementById(targetId);
                            if (target) {
                                // Find and expand the parent group if collapsed
                                const groups = document.querySelectorAll('[id^="group-"]');
                                groups.forEach(group => {
                                    const links = group.querySelectorAll('a[href="#' + targetId + '"]');
                                    if (links.length > 0 && group.style.maxHeight === '0px') {
                                        const groupId = group.id.replace('group-', '');
                                        toggleGroup(groupId);
                                    }
                                });
                                target.scrollIntoView({ behavior: 'smooth', block: 'start' });
                            }
                        });
                    });
                </script>
            </body>
            </html>
            """;
    }

    public static void main(String[] args) throws IOException {
        Path markdownDir = Paths.get("docs", "user-manual");
        Path outputDir = Paths.get("target", "user-manual");
        Path screenshotsDir = Paths.get("target", "user-manual", "screenshots");  // All tests now save directly here

        UserManualGenerator generator = new UserManualGenerator(markdownDir, outputDir, screenshotsDir);
        generator.generate();
    }
}
