package com.artivisi.accountingfinance.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HttpSecurityUtil Tests")
class HttpSecurityUtilTest {

    @Test
    @DisplayName("Should generate attachment Content-Disposition header")
    void shouldGenerateAttachmentContentDisposition() {
        assertThat(HttpSecurityUtil.attachmentContentDisposition("report.pdf"))
                .isEqualTo("attachment; filename=\"report.pdf\"; filename*=UTF-8''report.pdf");
        assertThat(HttpSecurityUtil.attachmentContentDisposition("Laporan Pajak – Maret.pdf"))
                .isEqualTo("attachment; filename=\"Laporan Pajak _ Maret.pdf\"; "
                        + "filename*=UTF-8''Laporan%20Pajak%20%E2%80%93%20Maret.pdf");
        assertThat(HttpSecurityUtil.attachmentContentDisposition("a\"; filename=evil.bat"))
                .isEqualTo("attachment; filename=\"a_; filename=evil.bat\"; filename*=UTF-8''a_%3B%20filename%3Devil.bat");
    }

    @Test
    @DisplayName("Should generate inline Content-Disposition header")
    void shouldGenerateInlineContentDisposition() {
        assertThat(HttpSecurityUtil.inlineContentDisposition("image.png"))
                .isEqualTo("inline; filename=\"image.png\"; filename*=UTF-8''image.png");
        assertThat(HttpSecurityUtil.inlineContentDisposition("../../etc/passwd"))
                .isEqualTo("inline; filename=\".._.._etc_passwd\"; filename*=UTF-8''.._.._etc_passwd");
    }

    @Test
    @DisplayName("Should sanitize null filename to default")
    void shouldSanitizeNullFilename() {
        String result = HttpSecurityUtil.sanitizeFilename(null);
        assertThat(result).isEqualTo("download");
    }

    @Test
    @DisplayName("Should sanitize empty filename to default")
    void shouldSanitizeEmptyFilename() {
        String result = HttpSecurityUtil.sanitizeFilename("");
        assertThat(result).isEqualTo("download");
    }

    @Test
    @DisplayName("Should sanitize dots-only filename to default")
    void shouldSanitizeDotsOnlyFilename() {
        String result = HttpSecurityUtil.sanitizeFilename("...");
        assertThat(result).isEqualTo("download");
    }

    @Test
    @DisplayName("Should remove path separators from filename")
    void shouldRemovePathSeparators() {
        String result = HttpSecurityUtil.sanitizeFilename("path/to\\file.txt");
        assertThat(result).isEqualTo("path_to_file.txt");
    }

    @Test
    @DisplayName("Should remove null bytes from filename")
    void shouldRemoveNullBytes() {
        String result = HttpSecurityUtil.sanitizeFilename("file\0name.txt");
        assertThat(result).doesNotContain("\0");
        assertThat(result).isEqualTo("filename.txt");
    }

    @Test
    @DisplayName("Should replace dangerous characters")
    void shouldReplaceDangerousCharacters() {
        String result = HttpSecurityUtil.sanitizeFilename("file<>:\"|?*.txt");
        assertThat(result).isEqualTo("file_______.txt");
    }

    @Test
    @DisplayName("Should truncate long filenames preserving extension")
    void shouldTruncateLongFilenames() {
        String longName = "a".repeat(250) + ".pdf";
        String result = HttpSecurityUtil.sanitizeFilename(longName);
        assertThat(result).isEqualTo("a".repeat(196) + ".pdf");
    }

    @Test
    @DisplayName("Should truncate long filenames without extension")
    void shouldTruncateLongFilenamesWithoutExtension() {
        String longName = "a".repeat(250);
        String result = HttpSecurityUtil.sanitizeFilename(longName);
        assertThat(result).isEqualTo("a".repeat(200));
    }

    @Test
    @DisplayName("Should handle normal filenames unchanged")
    void shouldHandleNormalFilenames() {
        String result = HttpSecurityUtil.sanitizeFilename("laporan-keuangan_2025.xlsx");
        assertThat(result).isEqualTo("laporan-keuangan_2025.xlsx");
    }

    @Test
    @DisplayName("Should handle Indonesian filenames with spaces")
    void shouldHandleIndonesianFilenames() {
        String result = HttpSecurityUtil.sanitizeFilename("Laporan Pajak Maret 2025.pdf");
        assertThat(result).isEqualTo("Laporan Pajak Maret 2025.pdf");
    }
}
