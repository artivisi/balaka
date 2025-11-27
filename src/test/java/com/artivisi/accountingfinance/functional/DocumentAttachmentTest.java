package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.page.LoginPage;
import com.artivisi.accountingfinance.functional.page.TransactionDetailPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@DisplayName("Document Attachment (Section 2.1)")
class DocumentAttachmentTest extends PlaywrightTestBase {

    private LoginPage loginPage;
    private TransactionDetailPage transactionDetailPage;

    // Test data IDs from V904 migration
    private static final String DRAFT_TRANSACTION_ID = "a0000000-0000-0000-0000-000000000001";
    private static final String POSTED_TRANSACTION_ID = "a0000000-0000-0000-0000-000000000002";

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        loginPage = new LoginPage(page, baseUrl());
        transactionDetailPage = new TransactionDetailPage(page, baseUrl());

        loginPage.navigate().loginAsAdmin();
    }

    /**
     * Copy test receipt image from resources to temp directory with given filename.
     * Uses actual invoice PNG from application for realistic OCR testing.
     */
    private Path getTestImage(String filename) throws IOException {
        Path targetPath = tempDir.resolve(filename);
        try (InputStream is = getClass().getResourceAsStream("/test-receipts/test-receipt.png")) {
            if (is == null) {
                throw new IOException("Test receipt image not found in resources");
            }
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return targetPath;
    }

    /**
     * Copy test invoice PDF from resources to temp directory with given filename.
     * Uses actual invoice PDF from application for realistic document testing.
     */
    private Path getTestPdf(String filename) throws IOException {
        Path targetPath = tempDir.resolve(filename);
        try (InputStream is = getClass().getResourceAsStream("/test-receipts/test-invoice.pdf")) {
            if (is == null) {
                throw new IOException("Test invoice PDF not found in resources");
            }
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return targetPath;
    }

    @Nested
    @DisplayName("2.1.1 Document Section Display")
    class DocumentSectionDisplayTests {

        @Test
        @DisplayName("Should display document attachment section on transaction detail")
        void shouldDisplayDocumentAttachmentSection() {
            transactionDetailPage.navigate(DRAFT_TRANSACTION_ID);

            transactionDetailPage.assertDocumentSectionVisible();
            transactionDetailPage.assertDocumentListContainerVisible();
        }

        @Test
        @DisplayName("Should display empty state when no documents")
        void shouldDisplayEmptyStateWhenNoDocuments() {
            transactionDetailPage.navigate(DRAFT_TRANSACTION_ID);

            transactionDetailPage.assertNoDocumentsMessage();
        }
    }

    @Nested
    @DisplayName("2.1.2 Document Upload")
    class DocumentUploadTests {

        @Test
        @DisplayName("Should upload image file to transaction")
        void shouldUploadImageFile() throws IOException {
            Path imagePath = getTestImage("test-receipt.png");

            transactionDetailPage.navigate(DRAFT_TRANSACTION_ID);
            transactionDetailPage.uploadDocument(imagePath);

            transactionDetailPage.assertDocumentVisible("test-receipt.png");
        }

        @Test
        @DisplayName("Should upload PDF file to transaction")
        void shouldUploadPdfFile() throws IOException {
            Path pdfPath = getTestPdf("test-invoice.pdf");

            transactionDetailPage.navigate(DRAFT_TRANSACTION_ID);
            transactionDetailPage.uploadDocument(pdfPath);

            transactionDetailPage.assertDocumentVisible("test-invoice.pdf");
        }

        @Test
        @DisplayName("Should upload document to posted transaction")
        void shouldUploadDocumentToPostedTransaction() throws IOException {
            Path imagePath = getTestImage("posted-receipt.png");

            transactionDetailPage.navigate(POSTED_TRANSACTION_ID);
            transactionDetailPage.uploadDocument(imagePath);

            transactionDetailPage.assertDocumentVisible("posted-receipt.png");
        }
    }

    @Nested
    @DisplayName("2.1.3 Multiple Documents")
    class MultipleDocumentsTests {

        @Test
        @DisplayName("Should upload multiple documents to transaction")
        void shouldUploadMultipleDocuments() throws IOException {
            Path image1 = getTestImage("receipt-1.png");
            Path image2 = getTestImage("receipt-2.png");

            transactionDetailPage.navigate(DRAFT_TRANSACTION_ID);
            transactionDetailPage.uploadDocument(image1);
            transactionDetailPage.uploadDocument(image2);

            transactionDetailPage.assertDocumentVisible("receipt-1.png");
            transactionDetailPage.assertDocumentVisible("receipt-2.png");
        }
    }

    @Nested
    @DisplayName("2.1.4 Document Deletion")
    class DocumentDeletionTests {

        @Test
        @DisplayName("Should delete document from transaction")
        void shouldDeleteDocument() throws IOException {
            // Use unique filename with timestamp to avoid conflicts
            String filename = "to-delete-" + System.currentTimeMillis() + ".png";
            Path imagePath = getTestImage(filename);

            transactionDetailPage.navigate(DRAFT_TRANSACTION_ID);
            int initialCount = getDocumentCount();

            transactionDetailPage.uploadDocument(imagePath);
            transactionDetailPage.assertDocumentVisible(filename);

            // Delete the first document (which should be the one we just uploaded)
            transactionDetailPage.clickDeleteDocumentButton();

            // Verify document count decreased
            int finalCount = getDocumentCount();
            org.assertj.core.api.Assertions.assertThat(finalCount).isLessThanOrEqualTo(initialCount);
        }

        private int getDocumentCount() {
            return page.locator("#document-list-container .group").count();
        }
    }
}
