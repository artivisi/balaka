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
            // LANDING PAGE
            new SectionGroup("beranda", "Beranda", "M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6", List.of(
                new Section("beranda", "Dokumentasi Balaka", "index.md", List.of())
            )),

            // 1. SETUP AWAL & ADMINISTRASI
            new SectionGroup("setup-awal", "Setup Awal & Administrasi", "M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z M15 12a3 3 0 11-6 0 3 3 0 016 0z", List.of(
                new Section("setup-awal", "Setup Awal", "01-setup-awal.md", List.of("login", "dashboard", "accounts-list", "accounts-form")),
                new Section("import-seed", "Import Seed Data", "01-setup-awal.md", List.of()),
                new Section("user-management", "User Management", "01-setup-awal.md", List.of("users-list", "users-form", "settings/devices")),
                new Section("telegram-setup", "Telegram Integration", "01-setup-awal.md", List.of())
            )),

            // 2. TUTORIAL DASAR AKUNTANSI - Crown Jewel
            new SectionGroup("tutorial-akuntansi", "Tutorial Dasar Akuntansi", "M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253", List.of(
                new Section("konsep-dasar", "Konsep Dasar Akuntansi", "02-tutorial-akuntansi.md", List.of()),
                new Section("siklus-akuntansi", "Siklus Akuntansi", "02-tutorial-akuntansi.md", List.of()),
                new Section("transaksi-harian", "Transaksi Harian", "02-tutorial-akuntansi.md", List.of("service/transaction-list")),
                new Section("jurnal-buku-besar", "Jurnal & Buku Besar", "02-tutorial-akuntansi.md", List.of("service/journals-list")),
                new Section("penyesuaian", "Penyesuaian", "02-tutorial-akuntansi.md", List.of("amortization-list", "amortization-form")),
                new Section("jurnal-manual", "Jurnal Manual", "02-tutorial-akuntansi.md", List.of("journal-entry/form-empty", "journal-entry/form-filled", "journal-entry/result-posted")),
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
                new Section("bantuan-ai", "Operasi Aplikasi dengan Bantuan AI", "13-bantuan-ai.md", List.of("ai-transaction/00-device-authorization", "ai-transaction/04-transactions-list", "settings/devices")),
                new Section("publikasi-analisis", "Laporan Keuangan dan Analisis", "13-bantuan-ai.md", List.of("analysis-reports/list", "analysis-reports/detail-top", "analysis-reports/detail-bottom"))
            )),

            // 15. PERINGATAN (SMART ALERTS)
            new SectionGroup("peringatan", "Peringatan", "M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9", List.of(
                new Section("konsep-peringatan", "Konsep Peringatan", "15-peringatan.md", List.of()),
                new Section("konfigurasi-peringatan", "Konfigurasi Peringatan", "15-peringatan.md", List.of("alerts/config")),
                new Section("peringatan-aktif", "Peringatan Aktif", "15-peringatan.md", List.of("alerts/active")),
                new Section("widget-dashboard-alerts", "Widget Dashboard", "15-peringatan.md", List.of("alerts/dashboard-widget")),
                new Section("riwayat-peringatan", "Riwayat Peringatan", "15-peringatan.md", List.of("alerts/history"))
            )),

            // 16. FAKTUR & TAGIHAN
            new SectionGroup("faktur-tagihan", "Faktur & Tagihan", "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z", List.of(
                new Section("faktur-invoice", "Faktur (Invoice)", "16-faktur-tagihan.md", List.of("10-invoice-created", "10-invoice-sent")),
                new Section("tagihan-vendor", "Tagihan Vendor (Bill)", "16-faktur-tagihan.md", List.of("10-bill-approved")),
                new Section("pelacakan-pembayaran", "Pelacakan Pembayaran", "16-faktur-tagihan.md", List.of("10-invoice-partial-payment", "10-invoice-paid", "10-bill-payment")),
                new Section("aging-piutang-hutang", "Laporan Umur Piutang & Hutang", "16-faktur-tagihan.md", List.of("10-aging-receivables-unpaid", "10-aging-receivables-partial", "10-aging-receivables-cleared", "10-aging-payables-unpaid")),
                new Section("laporan-klien-vendor", "Laporan Klien & Vendor", "16-faktur-tagihan.md", List.of("10-client-statement", "10-vendor-statement")),
                new Section("contoh-alur-lengkap", "Contoh Alur Lengkap", "16-faktur-tagihan.md", List.of())
            )),

            // 17. TRANSAKSI BERULANG
            new SectionGroup("transaksi-berulang", "Transaksi Berulang", "M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15", List.of(
                new Section("konsep-transaksi-berulang", "Konsep Transaksi Berulang", "17-transaksi-berulang.md", List.of()),
                new Section("membuat-transaksi-berulang", "Membuat Transaksi Berulang", "17-transaksi-berulang.md", List.of()),
                new Section("detail-pengelolaan", "Detail dan Pengelolaan", "17-transaksi-berulang.md", List.of("recurring/detail")),
                new Section("eksekusi-otomatis", "Eksekusi Otomatis (Scheduler)", "17-transaksi-berulang.md", List.of()),
                new Section("hak-akses-recurring", "Hak Akses", "17-transaksi-berulang.md", List.of())
            )),

            // 18. LAMPIRAN (last)
            new SectionGroup("lampiran", "Lampiran", "M5 8h14M5 8a2 2 0 110-4h14a2 2 0 110 4M5 8v10a2 2 0 002 2h10a2 2 0 002-2V8m-9 4h4", List.of(
                new Section("glosarium", "Glosarium", "12-lampiran-glosarium.md", List.of()),
                new Section("referensi-template", "Referensi Template", "12-lampiran-template.md", List.of("service/templates-list", "service/templates-metadata-detail", "service/templates-metadata-form")),
                new Section("referensi-amortisasi", "Referensi Amortisasi & Depresiasi", "12-lampiran-amortisasi.md", List.of()),
                new Section("referensi-akun", "Referensi Akun", "12-lampiran-akun.md", List.of("accounts-list"))
            )),

            // ==================== TUTORIALS ====================

            // Tutorial: Panduan Umum
            new SectionGroup("tutorial-umum", "Tutorial: Panduan Umum", "M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253", List.of(
                new Section("tut-setup", "Persiapan Awal", "common/01-setup.md", List.of()),
                new Section("tut-coa", "Bagan Akun (COA)", "common/02-chart-of-accounts.md", List.of()),
                new Section("tut-debit-kredit", "Debit dan Kredit", "common/03-debit-credit.md", List.of()),
                new Section("tut-template", "Template Jurnal", "common/04-journal-templates.md", List.of()),
                new Section("tut-laporan", "Laporan Keuangan", "common/05-financial-reports.md", List.of()),
                new Section("tut-payroll", "Payroll", "common/06-payroll.md", List.of()),
                new Section("tut-ppn", "PPN", "common/07-ppn.md", List.of()),
                new Section("tut-pph", "PPh", "common/08-pph.md", List.of()),
                new Section("tut-bpjs", "BPJS", "common/09-bpjs.md", List.of()),
                new Section("tut-aset", "Aset Tetap & Penyusutan", "common/10-fixed-assets.md", List.of()),
                new Section("tut-tutup-bulan", "Tutup Buku Bulanan", "common/11-monthly-closing.md", List.of()),
                new Section("tut-tutup-tahun", "Tutup Buku Akhir Tahun", "common/12-year-end-closing.md", List.of()),
                new Section("tut-coretax", "Export Coretax", "common/13-coretax-export.md", List.of())
            )),

            // Tutorial: IT Service
            new SectionGroup("tutorial-it", "Tutorial: IT Service", "M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z", List.of(
                new Section("tut-it-overview", "Overview", "it-service/00-overview.md", List.of()),
                new Section("tut-it-opening", "Setoran Modal", "it-service/01-opening-balance.md", List.of()),
                new Section("tut-it-income", "Mencatat Pendapatan", "it-service/02-recording-income.md", List.of()),
                new Section("tut-it-expense", "Mencatat Pengeluaran", "it-service/03-paying-expenses.md", List.of()),
                new Section("tut-it-payroll", "Payroll Bulanan", "it-service/04-payroll.md", List.of()),
                new Section("tut-it-asset", "Aset Tetap", "it-service/05-fixed-assets.md", List.of()),
                new Section("tut-it-closing", "Tutup Bulan", "it-service/06-monthly-closing.md", List.of()),
                new Section("tut-it-yearend", "Tutup Tahun & SPT", "it-service/07-year-end.md", List.of()),
                new Section("tut-it-mistakes", "Kesalahan Umum", "it-service/08-common-mistakes.md", List.of())
            )),

            // Tutorial: Online Seller
            new SectionGroup("tutorial-seller", "Tutorial: Online Seller", "M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z", List.of(
                new Section("tut-seller-overview", "Overview", "online-seller/00-overview.md", List.of()),
                new Section("tut-seller-sales", "Penjualan Marketplace", "online-seller/01-marketplace-sales.md", List.of()),
                new Section("tut-seller-withdraw", "Withdraw Saldo", "online-seller/02-withdrawals.md", List.of()),
                new Section("tut-seller-inventory", "Inventori", "online-seller/03-inventory.md", List.of()),
                new Section("tut-seller-expense", "Beban Operasional", "online-seller/04-expenses.md", List.of()),
                new Section("tut-seller-pph", "PPh Final UMKM", "online-seller/05-pph-final-umkm.md", List.of()),
                new Section("tut-seller-payroll", "Payroll", "online-seller/06-payroll.md", List.of()),
                new Section("tut-seller-closing", "Tutup Bulan", "online-seller/07-monthly-closing.md", List.of())
            )),

            // Tutorial: Coffee Shop
            new SectionGroup("tutorial-coffee", "Tutorial: Coffee Shop", "M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4", List.of(
                new Section("tut-coffee-overview", "Overview", "coffee-shop/00-overview.md", List.of()),
                new Section("tut-coffee-materials", "Pembelian Bahan Baku", "coffee-shop/01-raw-materials.md", List.of()),
                new Section("tut-coffee-sales", "Penjualan Harian", "coffee-shop/02-daily-sales.md", List.of()),
                new Section("tut-coffee-delivery", "Penjualan Online", "coffee-shop/03-online-delivery.md", List.of()),
                new Section("tut-coffee-bom", "Produksi & BOM", "coffee-shop/04-production-bom.md", List.of()),
                new Section("tut-coffee-expense", "Beban Operasional", "coffee-shop/05-expenses.md", List.of()),
                new Section("tut-coffee-payroll", "Payroll", "coffee-shop/06-payroll.md", List.of()),
                new Section("tut-coffee-tax", "Pajak UMKM", "coffee-shop/07-tax-umkm.md", List.of())
            )),

            // Tutorial: Campus
            new SectionGroup("tutorial-campus", "Tutorial: Campus", "M12 14l9-5-9-5-9 5 9 5z M12 14l6.16-3.422a12.083 12.083 0 01.665 6.479A11.952 11.952 0 0012 20.055a11.952 11.952 0 00-6.824-2.998 12.078 12.078 0 01.665-6.479L12 14z", List.of(
                new Section("tut-campus-overview", "Overview", "campus/00-overview.md", List.of()),
                new Section("tut-campus-billing", "Tagihan SPP", "campus/01-tuition-billing.md", List.of()),
                new Section("tut-campus-payment", "Penerimaan Pembayaran", "campus/02-receiving-payments.md", List.of()),
                new Section("tut-campus-grants", "Hibah & Donasi", "campus/03-grants-donations.md", List.of()),
                new Section("tut-campus-scholarship", "Beasiswa", "campus/04-scholarships.md", List.of()),
                new Section("tut-campus-payroll", "Payroll Dosen & Staff", "campus/05-faculty-payroll.md", List.of()),
                new Section("tut-campus-ops", "Operasional Kampus", "campus/06-campus-operations.md", List.of()),
                new Section("tut-campus-report", "Laporan & Pelaporan", "campus/07-reporting.md", List.of())
            )),

            // ==================== FEATURE REFERENCE ====================

            new SectionGroup("fitur-referensi", "Referensi Fitur", "M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2", List.of(
                new Section("ref-bank-recon", "Rekonsiliasi Bank", "01-bank-reconciliation.md", List.of()),
                new Section("ref-invoicing", "Faktur (Invoice)", "02-invoicing.md", List.of()),
                new Section("ref-bills", "Tagihan Vendor", "03-vendor-bills.md", List.of()),
                new Section("ref-recurring", "Transaksi Berulang", "04-recurring-transactions.md", List.of()),
                new Section("ref-alerts", "Peringatan (Smart Alerts)", "05-smart-alerts.md", List.of()),
                new Section("ref-tags", "Tag Transaksi", "06-tags.md", List.of()),
                new Section("ref-ai", "Integrasi AI & Telegram", "07-ai-integration.md", List.of()),
                new Section("ref-data", "Manajemen Data", "08-data-management.md", List.of()),
                new Section("ref-device-auth", "Otorisasi Perangkat API", "09-api-device-auth.md", List.of())
            )),

            // ==================== ADMIN GUIDE ====================

            new SectionGroup("admin-guide", "Panduan Administrator", "M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z M15 12a3 3 0 11-6 0 3 3 0 016 0z", List.of(
                new Section("adm-deployment", "Deployment", "01-deployment.md", List.of()),
                new Section("adm-config", "Konfigurasi", "02-configuration.md", List.of()),
                new Section("adm-database", "Database", "03-database.md", List.of()),
                new Section("adm-monitoring", "Monitoring", "04-monitoring.md", List.of()),
                new Section("adm-updates", "Update & Upgrade", "05-updates.md", List.of()),
                new Section("adm-security", "Keamanan", "06-security.md", List.of()),
                new Section("adm-multi-instance", "Multi-Instance", "07-multi-instance.md", List.of()),
                new Section("adm-demo-setup", "Setup Demo", "08-demo-setup.md", List.of()),
                new Section("adm-troubleshooting", "Troubleshooting", "09-troubleshooting.md", List.of())
            )),

            // ==================== IMPLEMENTOR GUIDE ====================

            new SectionGroup("implementor-guide", "Panduan Implementor", "M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z", List.of(
                new Section("impl-partner", "Model Kemitraan", "01-partner-model.md", List.of()),
                new Section("impl-onboarding", "Onboarding Klien", "02-client-onboarding.md", List.of()),
                new Section("impl-coa", "Kustomisasi COA", "03-coa-customization.md", List.of()),
                new Section("impl-import", "Import Data", "04-data-import.md", List.of()),
                new Section("impl-template", "Konfigurasi Template", "05-template-config.md", List.of()),
                new Section("impl-tax", "Setup Pajak", "06-tax-setup.md", List.of()),
                new Section("impl-training", "Pelatihan Klien", "07-training-clients.md", List.of()),
                new Section("impl-support", "Support Playbook", "08-support-playbook.md", List.of())
            )),

            // ==================== DEVELOPER GUIDE ====================

            new SectionGroup("dev-api", "Developer: API Guide", "M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4", List.of(
                new Section("dev-auth", "Authentication", "01-authentication.md", List.of()),
                new Section("dev-quickstart", "Quickstart", "02-quickstart.md", List.of()),
                new Section("dev-transactions", "Transactions API", "03-transactions.md", List.of()),
                new Section("dev-reports", "Reports API", "04-reports.md", List.of()),
                new Section("dev-payroll", "Payroll API", "05-payroll.md", List.of()),
                new Section("dev-tax", "Tax Export API", "06-tax-export.md", List.of()),
                new Section("dev-pagination", "Pagination", "07-pagination.md", List.of())
            )),

            new SectionGroup("dev-extending", "Developer: Extending Balaka", "M11 4a2 2 0 114 0v1a1 1 0 001 1h3a1 1 0 011 1v3a1 1 0 01-1 1h-1a2 2 0 100 4h1a1 1 0 011 1v3a1 1 0 01-1 1h-3a1 1 0 01-1-1v-1a2 2 0 10-4 0v1a1 1 0 01-1 1H7a1 1 0 01-1-1v-3a1 1 0 00-1-1H4a2 2 0 110-4h1a1 1 0 001-1V7a1 1 0 011-1h3a1 1 0 001-1V4z", List.of(
                new Section("dev-architecture", "Architecture", "01-architecture.md", List.of()),
                new Section("dev-structure", "Project Structure", "02-project-structure.md", List.of()),
                new Section("dev-adding", "Adding Features", "03-adding-features.md", List.of()),
                new Section("dev-seed-packs", "Seed Packs", "04-seed-packs.md", List.of()),
                new Section("dev-testing", "Testing", "05-testing.md", List.of()),
                new Section("dev-contributing", "Contributing", "06-contributing.md", List.of())
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
     * Generates multi-page user manual:
     * - index.html: landing page with links to each guide
     * - {group-id}.html: one page per SectionGroup with sidebar + content
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

        List<SectionGroup> groups = getSectionGroups();

        // Generate landing page (index.html)
        String landingHtml = generateLandingPage(groups);
        Files.writeString(outputDir.resolve("index.html"), landingHtml, StandardCharsets.UTF_8);
        System.out.println("Generated: index.html");

        // Generate one page per SectionGroup
        for (SectionGroup group : groups) {
            // Skip the landing page group itself
            if ("beranda".equals(group.id())) continue;

            String pageHtml = generateGroupPage(group, groups);
            Path pagePath = outputDir.resolve(group.id() + ".html");
            Files.writeString(pagePath, pageHtml, StandardCharsets.UTF_8);
            System.out.println("Generated: " + group.id() + ".html");
        }

        // Also generate the legacy single-page version for backward compatibility
        String singlePageHtml = generateHtml();
        Files.writeString(outputDir.resolve("all.html"), singlePageHtml, StandardCharsets.UTF_8);
        System.out.println("Generated: all.html (legacy single-page)");
    }

    /**
     * Generate landing page with cards linking to each guide.
     */
    private String generateLandingPage(List<SectionGroup> groups) throws IOException {
        // Read and render the index.md content
        String indexMarkdown = readMarkdownFile("index.md");
        String indexContent = convertMarkdownToHtml(indexMarkdown);

        // Build navigation cards
        StringBuilder cardsHtml = new StringBuilder();
        for (SectionGroup group : groups) {
            if ("beranda".equals(group.id())) continue;
            int sectionCount = group.sections().size();
            cardsHtml.append(String.format("""
                <a href="%s.html" class="block bg-white rounded-lg shadow-sm border border-gray-200 p-6 hover:shadow-md hover:border-primary-300 transition-all">
                    <div class="flex items-center mb-3">
                        <svg class="w-6 h-6 text-primary-600 mr-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="%s"/>
                        </svg>
                        <h3 class="text-lg font-semibold text-gray-900">%s</h3>
                    </div>
                    <p class="text-sm text-gray-500">%d bagian</p>
                </a>
                """, group.id(), group.icon(), group.title(), sectionCount));
        }

        return wrapInHtmlPage("Dokumentasi Balaka", String.format("""
            <div class="max-w-5xl mx-auto px-6 py-12">
                <div class="prose max-w-none mb-12">
                    %s
                </div>
                <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    %s
                </div>
            </div>
            """, indexContent, cardsHtml), null);
    }

    /**
     * Generate a single guide page with sidebar navigation and all sections.
     */
    private String generateGroupPage(SectionGroup group, List<SectionGroup> allGroups) throws IOException {
        StringBuilder tocHtml = new StringBuilder();
        StringBuilder sectionsHtml = new StringBuilder();

        int sectionIndex = 0;
        for (Section section : group.sections()) {
            sectionIndex++;

            // TOC entry
            tocHtml.append(String.format("""
                <li>
                    <a href="#%s" class="flex items-center text-sm text-gray-600 hover:text-primary-600 hover:bg-primary-50 px-3 py-1.5 rounded-lg transition-colors">
                        <span class="w-5 h-5 flex items-center justify-center text-xs text-gray-400 mr-2">%d</span>
                        <span class="truncate">%s</span>
                    </a>
                </li>
                """, section.id(), sectionIndex, section.title()));

            // Section content
            String fullMarkdown = readMarkdownFile(section.markdownFile());
            Set<String> siblingTitles = new HashSet<>();
            for (Section s : group.sections()) {
                if (s.markdownFile().equals(section.markdownFile()) && !s.title().equals(section.title())) {
                    siblingTitles.add(s.title());
                }
            }
            String sectionContent = extractSectionContent(fullMarkdown, section.title(), siblingTitles);
            String contentHtml = convertMarkdownToHtml(sectionContent);
            String screenshotsHtml = buildScreenshotsHtml(section.screenshots());

            sectionsHtml.append(String.format("""
                <section id="%s" class="bg-white rounded-lg shadow-sm border border-gray-200 mb-8 overflow-hidden">
                    <div class="bg-gradient-to-r from-primary-600 to-primary-700 px-6 py-4">
                        <h2 class="text-xl font-bold text-white">%s</h2>
                    </div>
                    <div class="p-6">
                        <div class="prose max-w-none">
                            %s
                        </div>
                        %s
                    </div>
                </section>
                """, section.id(), section.title(), contentHtml, screenshotsHtml));
        }

        // Build sidebar with links to other guides
        StringBuilder navHtml = new StringBuilder();
        navHtml.append("""
            <li class="mb-2">
                <a href="index.html" class="flex items-center text-sm font-medium text-gray-500 hover:text-primary-600 px-3 py-2 rounded-lg transition-colors">
                    ← Kembali ke Beranda
                </a>
            </li>
            <li class="mb-3">
                <div class="text-xs font-semibold text-primary-600 uppercase tracking-wider px-3 py-1">""");
        navHtml.append(group.title());
        navHtml.append("""
                </div>
                <ul class="mt-1 space-y-1">""");
        navHtml.append(tocHtml);
        navHtml.append("""
                </ul>
            </li>
            <li class="border-t border-gray-200 pt-3 mt-3">
                <div class="text-xs font-semibold text-gray-400 uppercase tracking-wider px-3 py-1">Panduan Lain</div>
                <ul class="mt-1 space-y-1">""");
        for (SectionGroup other : allGroups) {
            if ("beranda".equals(other.id()) || other.id().equals(group.id())) continue;
            navHtml.append(String.format("""
                    <li>
                        <a href="%s.html" class="flex items-center text-sm text-gray-500 hover:text-primary-600 px-3 py-1.5 rounded-lg transition-colors">
                            <span class="truncate">%s</span>
                        </a>
                    </li>
                """, other.id(), other.title()));
        }
        navHtml.append("</ul></li>");

        String bodyContent = String.format("""
            <div class="grid grid-cols-1 lg:grid-cols-12 gap-8 max-w-screen-xl mx-auto px-4 py-8">
                <aside class="lg:col-span-3">
                    <nav class="sticky top-8">
                        <ul class="space-y-1">
                            %s
                        </ul>
                    </nav>
                </aside>
                <main class="lg:col-span-9">
                    %s
                </main>
            </div>
            """, navHtml, sectionsHtml);

        return wrapInHtmlPage(group.title() + " — Balaka", bodyContent, group.title());
    }

    /**
     * Wrap body content in a complete HTML page with Tailwind CSS.
     */
    private String wrapInHtmlPage(String title, String bodyContent, String headerTitle) {
        String headerHtml = headerTitle != null ? String.format("""
            <header class="bg-gradient-to-r from-primary-700 to-primary-800 text-white shadow-lg">
                <div class="max-w-screen-xl mx-auto px-4 py-4 flex items-center justify-between">
                    <a href="index.html" class="flex items-center space-x-3">
                        <span class="text-xl font-bold">Balaka</span>
                    </a>
                    <span class="text-sm text-primary-200">%s</span>
                </div>
            </header>
            """, headerTitle) : """
            <header class="bg-gradient-to-r from-primary-700 to-primary-800 text-white shadow-lg">
                <div class="max-w-screen-xl mx-auto px-4 py-4">
                    <span class="text-xl font-bold">Balaka</span>
                </div>
            </header>
            """;

        return String.format("""
            <!DOCTYPE html>
            <html lang="id">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
                <script src="https://cdn.tailwindcss.com"></script>
                <script>
                    tailwind.config = {
                        theme: {
                            extend: {
                                colors: {
                                    primary: {
                                        50: '#eef2ff', 100: '#e0e7ff', 200: '#c7d2fe', 300: '#a5b4fc',
                                        400: '#818cf8', 500: '#6366f1', 600: '#4f46e5', 700: '#4338ca',
                                        800: '#2E2D8E', 900: '#1e1b4b'
                                    }
                                }
                            }
                        }
                    }
                </script>
                <style>
                    .prose h2 { font-size: 1.5rem; font-weight: 700; margin-top: 2rem; margin-bottom: 1rem; color: #1f2937; }
                    .prose h3 { font-size: 1.25rem; font-weight: 600; margin-top: 1.5rem; margin-bottom: 0.75rem; color: #374151; }
                    .prose p { margin-bottom: 1rem; line-height: 1.7; color: #4b5563; }
                    .prose ul, .prose ol { margin-bottom: 1rem; padding-left: 1.5rem; }
                    .prose li { margin-bottom: 0.25rem; color: #4b5563; }
                    .prose table { width: 100%%; border-collapse: collapse; margin-bottom: 1rem; font-size: 0.875rem; }
                    .prose th { background: #f3f4f6; padding: 0.75rem; text-align: left; border: 1px solid #e5e7eb; font-weight: 600; }
                    .prose td { padding: 0.75rem; border: 1px solid #e5e7eb; }
                    .prose code { background: #f3f4f6; padding: 0.125rem 0.375rem; border-radius: 0.25rem; font-size: 0.875rem; }
                    .prose pre { background: #1f2937; color: #e5e7eb; padding: 1rem; border-radius: 0.5rem; overflow-x: auto; margin-bottom: 1rem; }
                    .prose pre code { background: transparent; padding: 0; color: inherit; }
                    .prose a { color: #4f46e5; text-decoration: underline; }
                    .prose strong { font-weight: 600; color: #1f2937; }
                    .prose blockquote { border-left: 4px solid #e5e7eb; padding-left: 1rem; color: #6b7280; font-style: italic; }
                    .prose img { max-width: 100%%; border-radius: 0.5rem; box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin: 1rem 0; }
                </style>
            </head>
            <body class="bg-gray-50 min-h-screen">
                %s
                %s
            </body>
            </html>
            """, title, headerHtml, bodyContent);
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
                // Collect sibling section titles (other sections using the same markdown file)
                Set<String> siblingTitles = new HashSet<>();
                for (SectionGroup g : getSectionGroups()) {
                    for (Section s : g.sections()) {
                        if (s.markdownFile().equals(section.markdownFile()) && !s.title().equals(section.title())) {
                            siblingTitles.add(s.title());
                        }
                    }
                }
                String sectionContent = extractSectionContent(fullMarkdown, section.title(), siblingTitles);
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
        // Try tutorials directory (for tutorial sections)
        Path tutorialPath = Paths.get("docs", "tutorials").resolve(filename);
        if (Files.exists(tutorialPath)) {
            return Files.readString(tutorialPath, StandardCharsets.UTF_8);
        }
        // Try docs root (for index.md and other top-level docs)
        Path docsPath = Paths.get("docs").resolve(filename);
        if (Files.exists(docsPath)) {
            return Files.readString(docsPath, StandardCharsets.UTF_8);
        }
        // Try admin-guide, implementor-guide, feature-reference, developer-guide
        for (String subdir : List.of("admin-guide", "implementor-guide", "feature-reference", "developer-guide", "developer-guide/api", "developer-guide/extending")) {
            Path guidePath = Paths.get("docs", subdir).resolve(filename);
            if (Files.exists(guidePath)) {
                return Files.readString(guidePath, StandardCharsets.UTF_8);
            }
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
     *
     * Extraction rules (applied in order):
     * 1. Try exact H2 match → return that H2's content (up to next H2)
     * 2. If sectionTitle matches the file's H1 title → return ALL content EXCEPT
     *    H2 sections that have their own section definitions (siblingTitles).
     *    This prevents duplicate rendering when multiple sections reference the same file.
     * 3. Fallback: aggregate intro content plus H2s that aren't sibling sections.
     *
     * IMPORTANT: When adding sections to UserManualGenerator.getSectionGroups(),
     * if multiple sections reference the same markdown file, each section title
     * must correspond to a specific H2 heading in that file. The first section
     * (which typically matches the H1 or acts as the "catch-all") will automatically
     * EXCLUDE H2 content that belongs to sibling sections. This prevents the
     * recurring "duplicate section rendering" bug.
     *
     * @param siblingTitles titles of other sections that reference the same markdown file
     */
    private String extractSectionContent(String markdown, String sectionTitle, Set<String> siblingTitles) {
        if (sectionTitle == null || sectionTitle.isEmpty()) {
            return markdown;
        }

        // Split by H2 headings to get all sections
        String[] sections = markdown.split("(?m)^## ");

        // First, try to find exact H2 match
        for (int i = 1; i < sections.length; i++) {
            String section = sections[i];
            String firstLine = section.split("\n", 2)[0].trim();

            if (titlesMatch(sectionTitle, firstLine)) {
                String[] parts = section.split("\n", 2);
                return parts.length > 1 ? parts[1].trim() : "";
            }
        }

        // H1 match or fallback: return content EXCLUDING sibling H2 sections.
        // This handles both:
        //   - Section title matching H1 (e.g., "Bantuan AI untuk Pencatatan Transaksi")
        //   - Aggregate sections (e.g., "Konsep Dasar Akuntansi")
        if (sections.length > 1) {
            // Get intro content (after H1, before first H2)
            String intro = sections[0].replaceFirst("^#\\s+[^\\n]+\\n*", "").trim();
            StringBuilder aggregated = new StringBuilder(intro);

            for (int i = 1; i < sections.length; i++) {
                String section = sections[i];
                String h2Title = section.split("\n", 2)[0].trim();

                // Skip H2 sections that have their own section definitions
                boolean isSibling = siblingTitles.stream()
                        .anyMatch(st -> titlesMatch(st, h2Title));
                if (isSibling) {
                    continue;
                }

                aggregated.append("\n\n## ").append(section);
            }

            return aggregated.toString();
        }

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
                <title>Panduan Pengguna - Balaka</title>
                <script src="https://cdn.tailwindcss.com"></script>
                <script>
                    tailwind.config = {
                        theme: {
                            extend: {
                                colors: {
                                    primary: {
                                        50: '#eeeef8', 100: '#d5d5ed', 200: '#ababdb', 300: '#8180c9',
                                        400: '#5756b7', 500: '#3d3ca5', 600: '#2E2D8E', 700: '#262578',
                                        800: '#1e1d62', 900: '#16164c',
                                    }
                                }
                            }
                        }
                    }
                </script>
                <style>
                    .prose h2 { font-size: 1.5rem; font-weight: 700; margin-top: 2rem; margin-bottom: 1rem; color: #2E2D8E; }
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
                    .prose blockquote { border-left: 4px solid #2E2D8E; padding-left: 1rem; margin: 1rem 0; color: #4b5563; font-style: italic; }
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
                                <svg class="h-8 mr-3" viewBox="0 0 580 140">
                                    <g transform="translate(1 10) scale(0.356)">
                                        <path fill="white" d="M25.5 30.2c-.3.7-.4 26.9-.3 58.3l.3 57h99c109.8 0 102.7.4 117-6.9 11.5-5.9 20.1-14.7 24.3-24.8 3.8-9.1 5.2-15.7 5.2-24.4 0-21.9-12.3-41.6-32-51.3-4.7-2.3-11.6-5.1-15.5-6.3-7-2.2-7.5-2.2-102.3-2.6-77.3-.2-95.3-.1-95.7 1m181.6 29.2c15.8 3.4 23.9 10.5 27.5 24.2 3.4 13.4-5.5 27.6-20 31.8-4.8 1.4-14.4 1.6-80.3 1.4l-74.8-.3-.3-28c-.1-15.4 0-28.5.3-29.2.7-2 138.4-1.8 147.6.1M25.4 162.3c-.2.7-.3 6.5-.2 12.8l.3 11.4 95.5.5c94.5.5 95.6.5 101.1 2.6 9.2 3.6 12.1 5.4 18 11.5 15.3 15.6 18.9 39.7 8.8 57.6-5.2 9.1-15.4 16.3-27.6 19.5-3.7 1-23 1.3-82.8 1.3h-78v-54l83-.5 82.9-.5 1.3-11c.6-6 .9-11.2.7-11.5-.3-.3-46.1-.5-101.7-.5H25.5v107l97.9.3c102.1.2 102.7.2 116.6-3.9 12.9-3.8 25.5-11.3 33.7-20.1 12.2-13.2 19.2-33.4 18-52.7-1.1-17.2-6.5-31.4-16.8-43.9-8.8-10.7-22.1-19.5-34.9-23.1-2.5-.8-5.8-1.8-7.5-2.5-2.1-.8-32.7-1.2-104.8-1.4-84.7-.2-101.8-.1-102.3 1.1"/>
                                    </g>
                                    <text x="115" y="120" font-family="'Plus Jakarta Sans', sans-serif" font-size="140" font-weight="600" fill="white" letter-spacing="1">Balaka</text>
                                </svg>
                                <span class="text-xl font-bold">Panduan Pengguna</span>
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
                                <p>&copy; 2025 Balaka</p>
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

                    // Navigate to section: scroll, expand sidebar group, update URL hash
                    function navigateToSection(targetId, smooth) {
                        const target = document.getElementById(targetId);
                        if (!target) return;

                        // Expand the sidebar group containing this section
                        const groups = document.querySelectorAll('[id^="group-"]');
                        groups.forEach(group => {
                            const links = group.querySelectorAll('a[href="#' + targetId + '"]');
                            if (links.length > 0 && group.style.maxHeight === '0px') {
                                const groupId = group.id.replace('group-', '');
                                toggleGroup(groupId);
                            }
                        });

                        target.scrollIntoView({ behavior: smooth ? 'smooth' : 'auto', block: 'start' });
                        history.replaceState(null, '', '#' + targetId);
                    }

                    // Smooth scroll for anchor links
                    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
                        anchor.addEventListener('click', function (e) {
                            e.preventDefault();
                            const targetId = this.getAttribute('href').substring(1);
                            navigateToSection(targetId, true);
                        });
                    });

                    // On page load, navigate to hash target if present
                    if (window.location.hash) {
                        const targetId = window.location.hash.substring(1);
                        // Delay to ensure layout is ready
                        setTimeout(() => navigateToSection(targetId, false), 100);
                    }
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
