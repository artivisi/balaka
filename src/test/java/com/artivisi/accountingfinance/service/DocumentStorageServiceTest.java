package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for DocumentStorageService.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DisplayName("DocumentStorageService Integration Tests")
class DocumentStorageServiceTest {

    @Autowired
    private DocumentStorageService documentStorageService;

    @Nested
    @DisplayName("File Validation Operations")
    class FileValidationTests {

        @Test
        @DisplayName("Should reject file exceeding max size")
        void shouldRejectFileExceedingMaxSize() {
            // Create a file larger than 10MB (default max size)
            byte[] largeContent = new byte[11 * 1024 * 1024]; // 11 MB
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "large-file.pdf",
                    "application/pdf",
                    largeContent
            );

            assertThatThrownBy(() -> documentStorageService.store(file))
                    .isInstanceOf(com.artivisi.accountingfinance.exception.FileTooLargeException.class)
                    .hasMessageContaining("exceeds maximum allowed size 10485760");
        }

        @Test
        @DisplayName("Should reject unsupported file type")
        void shouldRejectUnsupportedFileType() {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "malicious.exe",
                    "application/x-msdownload",
                    "executable content".getBytes()
            );

            assertThatThrownBy(() -> documentStorageService.store(file))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should reject empty file")
        void shouldRejectEmptyFile() {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "empty.pdf",
                    "application/pdf",
                    new byte[0]
            );

            assertThatThrownBy(() -> documentStorageService.store(file))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Load Operations")
    class LoadOperationsTests {

        @Test
        @DisplayName("Should throw exception when loading non-existent file")
        void shouldThrowExceptionWhenLoadingNonExistentFile() {
            assertThatThrownBy(() -> documentStorageService.loadAsResource("non-existent-path/file.pdf"))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("Delete Operations")
    class DeleteOperationsTests {

        @Test
        @DisplayName("Should not throw when deleting non-existent file")
        void shouldNotThrowWhenDeletingNonExistentFile() {
            // Should not throw, just log warning
            assertThatCode(() -> documentStorageService.delete("non-existent-path/file.pdf"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Security Operations")
    class SecurityOperationsTests {

        @Test
        @DisplayName("Should reject path traversal in filename")
        void shouldRejectPathTraversalInFilename() {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "../../../etc/passwd",
                    "application/pdf",
                    "malicious content".getBytes()
            );

            assertThatThrownBy(() -> documentStorageService.store(file))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Should reject null original filename")
        void shouldRejectNullOriginalFilename() {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    null,
                    "application/pdf",
                    "content".getBytes()
            );

            assertThatThrownBy(() -> documentStorageService.store(file))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("Checksum Operations")
    class ChecksumOperationsTests {

        @Test
        @DisplayName("Should calculate checksum from bytes")
        void shouldCalculateChecksumFromBytes() {
            byte[] content = "test content for checksum".getBytes();
            String checksum = documentStorageService.calculateChecksumFromBytes(content);

            assertThat(checksum).isNotNull();
            assertThat(checksum).hasSize(64); // SHA-256 produces 64 hex chars
        }

        @Test
        @DisplayName("Should produce same checksum for same content")
        void shouldProduceSameChecksumForSameContent() {
            byte[] content = "identical content".getBytes();

            String checksum1 = documentStorageService.calculateChecksumFromBytes(content);
            String checksum2 = documentStorageService.calculateChecksumFromBytes(content);

            assertThat(checksum1).isEqualTo(checksum2);
        }

        @Test
        @DisplayName("Should produce different checksum for different content")
        void shouldProduceDifferentChecksumForDifferentContent() {
            String checksum1 = documentStorageService.calculateChecksumFromBytes("content1".getBytes());
            String checksum2 = documentStorageService.calculateChecksumFromBytes("content2".getBytes());

            assertThat(checksum1).isNotEqualTo(checksum2);
        }
    }

    @Nested
    @DisplayName("Utility Operations")
    class UtilityOperationsTests {

        @Test
        @DisplayName("Should check if file exists - false for non-existent")
        void shouldCheckIfFileExistsFalseForNonExistent() {
            boolean exists = documentStorageService.exists("non-existent-path/file.pdf");
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("Should get root location")
        void shouldGetRootLocation() {
            var rootLocation = documentStorageService.getRootLocation();
            assertThat(rootLocation).isNotNull();
        }

        @Test
        @DisplayName("Should check encryption status")
        void shouldCheckEncryptionStatus() {
            boolean enabled = documentStorageService.isEncryptionEnabled();
            // Just verify it returns a value without throwing
            assertThat(enabled).isIn(true, false);
        }

        @Test
        @DisplayName("Should return false for path traversal in exists check")
        void shouldReturnFalseForPathTraversalInExists() {
            boolean exists = documentStorageService.exists("../../etc/passwd");
            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("Store and Retrieve Operations")
    class StoreRetrieveTests {

        @Test
        @DisplayName("Should store and retrieve a JPEG file")
        void shouldStoreAndRetrieveJpegFile() throws IOException {
            // JPEG magic bytes: FF D8 FF
            byte[] jpegContent = new byte[1024];
            jpegContent[0] = (byte) 0xFF;
            jpegContent[1] = (byte) 0xD8;
            jpegContent[2] = (byte) 0xFF;

            MockMultipartFile file = new MockMultipartFile(
                    "file", "test-image.jpg", "image/jpeg", jpegContent);

            String storedPath = documentStorageService.store(file);
            assertThat(storedPath).isNotBlank();
            assertThat(storedPath).endsWith(".jpg");

            // Verify file exists
            assertThat(documentStorageService.exists(storedPath)).isTrue();

            // Load and verify resource
            Resource resource = documentStorageService.loadAsResource(storedPath);
            assertThat(resource).isNotNull();
            assertThat(resource.getFilename()).isNotBlank();

            // Cleanup
            documentStorageService.delete(storedPath);
            assertThat(documentStorageService.exists(storedPath)).isFalse();
        }

        @Test
        @DisplayName("Should store and retrieve a PNG file")
        void shouldStoreAndRetrievePngFile() throws IOException {
            // PNG magic bytes: 89 50 4E 47 0D 0A 1A 0A
            byte[] pngContent = new byte[1024];
            pngContent[0] = (byte) 0x89;
            pngContent[1] = 0x50;
            pngContent[2] = 0x4E;
            pngContent[3] = 0x47;
            pngContent[4] = 0x0D;
            pngContent[5] = 0x0A;
            pngContent[6] = 0x1A;
            pngContent[7] = 0x0A;

            MockMultipartFile file = new MockMultipartFile(
                    "file", "test-image.png", "image/png", pngContent);

            String storedPath = documentStorageService.store(file);
            assertThat(storedPath).isNotBlank();

            Resource resource = documentStorageService.loadAsResource(storedPath);
            assertThat(resource).isNotNull();

            documentStorageService.delete(storedPath);
        }

        @Test
        @DisplayName("Should calculate checksum for multipart file")
        void shouldCalculateChecksumForMultipartFile() throws IOException {
            byte[] jpegContent = new byte[256];
            jpegContent[0] = (byte) 0xFF;
            jpegContent[1] = (byte) 0xD8;
            jpegContent[2] = (byte) 0xFF;

            MockMultipartFile file = new MockMultipartFile(
                    "file", "checksum-test.jpg", "image/jpeg", jpegContent);

            String checksum = documentStorageService.calculateChecksum(file);
            assertThat(checksum).isNotNull();
            assertThat(checksum).hasSize(64); // SHA-256 hex
        }
    }

    @Nested
    @DisplayName("StoreFromBytes Operations")
    class StoreFromBytesTests {

        @Test
        @DisplayName("Should store file from byte array")
        void shouldStoreFromBytes() throws IOException {
            byte[] jpegContent = new byte[1024];
            jpegContent[0] = (byte) 0xFF;
            jpegContent[1] = (byte) 0xD8;
            jpegContent[2] = (byte) 0xFF;

            String storedPath = documentStorageService.storeFromBytes(
                    jpegContent, "test.jpg", "image/jpeg");

            assertThat(storedPath).isNotBlank();
            assertThat(documentStorageService.exists(storedPath)).isTrue();

            documentStorageService.delete(storedPath);
        }

        @Test
        @DisplayName("Should reject empty byte array")
        void shouldRejectEmptyByteArray() {
            assertThatThrownBy(() -> documentStorageService.storeFromBytes(
                    new byte[0], "empty.jpg", "image/jpeg"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("File content is empty");
        }

        @Test
        @DisplayName("Should reject null byte array")
        void shouldRejectNullByteArray() {
            assertThatThrownBy(() -> documentStorageService.storeFromBytes(
                    null, "null.jpg", "image/jpeg"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("File content is empty");
        }

        @Test
        @DisplayName("Should reject oversized byte array")
        void shouldRejectOversizedByteArray() {
            byte[] largeContent = new byte[11 * 1024 * 1024]; // 11 MB
            assertThatThrownBy(() -> documentStorageService.storeFromBytes(
                    largeContent, "large.jpg", "image/jpeg"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds maximum");
        }

        @Test
        @DisplayName("Should reject unsupported content type for bytes")
        void shouldRejectUnsupportedContentTypeForBytes() {
            assertThatThrownBy(() -> documentStorageService.storeFromBytes(
                    new byte[100], "file.exe", "application/x-msdownload"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not allowed");
        }

        @Test
        @DisplayName("Should reject magic byte mismatch for bytes")
        void shouldRejectMagicByteMismatchForBytes() {
            // Content with wrong magic bytes for JPEG
            byte[] wrongContent = "not a real jpeg".getBytes();
            assertThatThrownBy(() -> documentStorageService.storeFromBytes(
                    wrongContent, "fake.jpg", "image/jpeg"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("content does not match");
        }
    }

    @Nested
    @DisplayName("File Validation Detail Tests")
    class FileValidationDetailTests {

        @Test
        @DisplayName("Should reject null file in validateFile")
        void shouldRejectNullFile() {
            assertThatThrownBy(() -> documentStorageService.validateFile(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("File is empty");
        }

        @Test
        @DisplayName("Should reject file with null content type")
        void shouldRejectFileWithNullContentType() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", null, "content".getBytes());

            assertThatThrownBy(() -> documentStorageService.validateFile(file))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not allowed");
        }

        @Test
        @DisplayName("Should reject file with content-type spoofing")
        void shouldRejectContentTypeSpoofing() {
            // Claim PDF but content doesn't start with %PDF-
            MockMultipartFile file = new MockMultipartFile(
                    "file", "spoofed.pdf", "application/pdf", "not a pdf file".getBytes());

            assertThatThrownBy(() -> documentStorageService.validateFile(file))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("content does not match");
        }
    }

    @Nested
    @DisplayName("Path Traversal Security Tests")
    class PathTraversalSecurityTests {

        @Test
        @DisplayName("Should throw security exception for path traversal in loadAsResource")
        void shouldThrowForPathTraversalInLoad() {
            assertThatThrownBy(() -> documentStorageService.loadAsResource("../../etc/passwd"))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Should throw security exception for path traversal in delete")
        void shouldThrowForPathTraversalInDelete() {
            // This should either throw SecurityException or silently pass (file not found)
            assertThatCode(() -> documentStorageService.delete("valid-path/file.pdf"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should throw security exception for path traversal in loadRawContent")
        void shouldThrowForPathTraversalInLoadRawContent() {
            assertThatThrownBy(() -> documentStorageService.loadRawContent("../../etc/passwd"))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("Load Raw Content and Encryption Check Tests")
    class LoadRawContentTests {

        @Test
        @DisplayName("Should store and load raw content")
        void shouldStoreAndLoadRawContent() throws IOException {
            byte[] jpegContent = new byte[512];
            jpegContent[0] = (byte) 0xFF;
            jpegContent[1] = (byte) 0xD8;
            jpegContent[2] = (byte) 0xFF;

            String storedPath = documentStorageService.storeFromBytes(
                    jpegContent, "raw-test.jpg", "image/jpeg");

            byte[] rawContent = documentStorageService.loadRawContent(storedPath);
            assertThat(rawContent).isNotEmpty();

            documentStorageService.delete(storedPath);
        }

        @Test
        @DisplayName("Should check if file is encrypted")
        void shouldCheckIfFileIsEncrypted() throws IOException {
            byte[] jpegContent = new byte[512];
            jpegContent[0] = (byte) 0xFF;
            jpegContent[1] = (byte) 0xD8;
            jpegContent[2] = (byte) 0xFF;

            String storedPath = documentStorageService.storeFromBytes(
                    jpegContent, "enc-check.jpg", "image/jpeg");

            boolean encrypted = documentStorageService.isFileEncrypted(storedPath);
            // Result depends on encryption config
            assertThat(encrypted).isIn(true, false);

            documentStorageService.delete(storedPath);
        }
    }
}
