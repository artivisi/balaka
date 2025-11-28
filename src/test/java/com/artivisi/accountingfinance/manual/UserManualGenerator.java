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
        return List.of(
            new SectionGroup("pengantar", "Pengantar", "M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253", List.of(
                new Section("pendahuluan", "Pendahuluan", "00-pendahuluan.md", List.of()),
                new Section("konsep-dasar", "Konsep Dasar Akuntansi", "01-konsep-dasar.md", List.of())
            )),
            new SectionGroup("operasi-harian", "Operasi Harian", "M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01", List.of(
                new Section("mencatat-pendapatan", "Mencatat Pendapatan", "10-mencatat-pendapatan.md", List.of("transactions-form", "transactions-detail")),
                new Section("mencatat-pengeluaran", "Mencatat Pengeluaran", "11-mencatat-pengeluaran.md", List.of("transactions-list", "transactions-form")),
                new Section("transfer-antar-akun", "Transfer Antar Akun", "12-transfer-antar-akun.md", List.of("transactions-form")),
                new Section("telegram-receipt", "Telegram Receipt", "13-telegram-receipt.md", List.of())
            )),
            new SectionGroup("pelaporan", "Pelaporan", "M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z", List.of(
                new Section("laporan-harian", "Laporan Harian", "20-laporan-harian.md", List.of("dashboard", "transactions-list", "journals-list")),
                new Section("laporan-bulanan", "Laporan Bulanan", "21-laporan-bulanan.md", List.of("reports-trial-balance", "reports-balance-sheet", "reports-income-statement")),
                new Section("laporan-tahunan", "Laporan Tahunan", "22-laporan-tahunan.md", List.of("reports-income-statement", "reports-balance-sheet"))
            )),
            new SectionGroup("perpajakan", "Perpajakan", "M9 14l6-6m-5.5.5h.01m4.99 5h.01M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16l3.5-2 3.5 2 3.5-2 3.5 2z", List.of(
                new Section("transaksi-ppn", "Transaksi PPN", "30-transaksi-ppn.md", List.of("transactions-form", "reports-ppn-summary")),
                new Section("transaksi-pph", "Transaksi PPh", "31-transaksi-pph.md", List.of("transactions-form", "reports-pph23-withholding")),
                new Section("laporan-pajak", "Laporan Pajak", "32-laporan-pajak.md", List.of("reports-ppn-summary", "reports-pph23-withholding", "reports-tax-summary")),
                new Section("kalender-pajak", "Kalender Pajak", "33-kalender-pajak.md", List.of())
            )),
            new SectionGroup("manajemen-proyek", "Manajemen Proyek", "M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10", List.of(
                new Section("setup-proyek", "Setup Proyek", "40-setup-proyek.md", List.of("projects-form", "clients-list")),
                new Section("tracking-proyek", "Tracking Proyek", "41-tracking-proyek.md", List.of("projects-detail", "projects-list")),
                new Section("invoice-penagihan", "Invoice & Penagihan", "42-invoice-penagihan.md", List.of("invoices-list")),
                new Section("analisis-profitabilitas", "Analisis Profitabilitas", "43-analisis-profitabilitas.md", List.of("reports-project-profitability", "reports-client-profitability"))
            )),
            new SectionGroup("konfigurasi", "Konfigurasi", "M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z M15 12a3 3 0 11-6 0 3 3 0 016 0z", List.of(
                new Section("setup-awal", "Setup Awal", "50-setup-awal.md", List.of("accounts-list", "accounts-form")),
                new Section("kelola-template", "Kelola Template", "51-kelola-template.md", List.of("templates-list", "templates-detail", "templates-form")),
                new Section("kelola-klien", "Kelola Klien", "52-kelola-klien.md", List.of("clients-list", "clients-detail", "clients-form")),
                new Section("jadwal-amortisasi", "Jadwal Amortisasi", "53-jadwal-amortisasi.md", List.of("amortization-list", "amortization-form")),
                new Section("kelola-periode-fiskal", "Kelola Periode Fiskal", "54-kelola-periode-fiskal.md", List.of()),
                new Section("setup-telegram", "Setup Telegram Bot", "55-setup-telegram.md", List.of())
            )),
            new SectionGroup("penggajian", "Penggajian", "M17 9V7a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2m2 4h10a2 2 0 002-2v-6a2 2 0 00-2-2H9a2 2 0 00-2 2v6a2 2 0 002 2zm7-5a2 2 0 11-4 0 2 2 0 014 0z", List.of(
                new Section("kelola-karyawan", "Kelola Karyawan", "60-kelola-karyawan.md", List.of("employees-list", "employees-form")),
                new Section("komponen-gaji", "Komponen Gaji", "61-komponen-gaji.md", List.of()),
                new Section("kalkulator-bpjs", "Kalkulator BPJS", "62-kalkulator-bpjs.md", List.of()),
                new Section("kalkulator-pph21", "Kalkulator PPh 21", "63-kalkulator-pph21.md", List.of()),
                new Section("payroll-processing", "Proses Penggajian", "64-payroll-processing.md", List.of("payroll-list", "payroll-detail", "payroll-form"))
            )),
            new SectionGroup("administrasi", "Administrasi", "M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z", List.of(
                new Section("kelola-pengguna", "Kelola Pengguna", "70-kelola-pengguna.md", List.of("users-list", "users-form", "users-detail")),
                new Section("layanan-mandiri", "Layanan Mandiri", "71-layanan-mandiri.md", List.of("self-service-payslips", "self-service-bukti-potong", "self-service-profile")),
                new Section("kebijakan-data", "Kebijakan Data", "80-kebijakan-data.md", List.of()),
                new Section("ekspor-data", "Ekspor Data", "81-ekspor-data.md", List.of())
            )),
            new SectionGroup("lampiran", "Lampiran", "M5 8h14M5 8a2 2 0 110-4h14a2 2 0 110 4M5 8v10a2 2 0 002 2h10a2 2 0 002-2V8m-9 4h4", List.of(
                new Section("glosarium", "Glosarium", "90-glosarium.md", List.of()),
                new Section("referensi-akun", "Referensi Akun", "91-referensi-akun.md", List.of("accounts-list")),
                new Section("referensi-template", "Referensi Template", "92-referensi-template.md", List.of("templates-list"))
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

        // Copy screenshots if they exist
        if (Files.exists(screenshotsDir)) {
            try (var files = Files.list(screenshotsDir)) {
                files.filter(p -> p.toString().endsWith(".png"))
                     .forEach(p -> {
                         try {
                             Files.copy(p, outputScreenshotsDir.resolve(p.getFileName()),
                                     StandardCopyOption.REPLACE_EXISTING);
                         } catch (IOException e) {
                             System.err.println("Failed to copy screenshot: " + e.getMessage());
                         }
                     });
            }
        }

        // Generate HTML
        String html = generateHtml();
        Path indexPath = outputDir.resolve("index.html");
        Files.writeString(indexPath, html, StandardCharsets.UTF_8);

        System.out.println("User manual generated: " + indexPath);
    }

    private String generateHtml() throws IOException {
        StringBuilder sectionsHtml = new StringBuilder();
        StringBuilder tocHtml = new StringBuilder();

        int groupIndex = 0;
        int globalIndex = 0;
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
                globalIndex++;
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

                // Parse markdown content
                String markdownContent = readMarkdownFile(section.markdownFile());
                String contentHtml = convertMarkdownToHtml(markdownContent);

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
        // Remove the first H1 heading (title) as it's already in the section header
        String content = markdown.replaceFirst("^#\\s+[^\\n]+\\n*", "");
        Node document = parser.parse(content);
        return renderer.render(document);
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
        Path screenshotsDir = Paths.get("target", "user-manual", "screenshots");

        UserManualGenerator generator = new UserManualGenerator(markdownDir, outputDir, screenshotsDir);
        generator.generate();
    }
}
