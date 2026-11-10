package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.DocumentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.ExtendedModelMap;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The transaction evidence endpoint accepts several files in one request: every
 * file is stored and audited on its own, a rejected file does not discard the
 * ones that succeeded, and the response fragment is the evidence panel.
 *
 * <p>The controller method is invoked directly: the multi-file loop and its
 * partial-failure accounting are the behaviour under test, while the
 * {@code @PreAuthorize} and CSRF layers are covered by the security tests.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("Document upload for a transaction")
class DocumentControllerTest {

    private static final String VIEW_DOCUMENT_LIST = "fragments/document-list :: documentList";
    private static final String UPLOADED_BY = "admin";

    @Autowired
    private DocumentController documentController;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JournalTemplateRepository journalTemplateRepository;

    @Autowired
    private DocumentService documentService;

    private final UsernamePasswordAuthenticationToken authentication =
            UsernamePasswordAuthenticationToken.authenticated(UPLOADED_BY, "n/a",
                    List.of(new SimpleGrantedAuthority("TRANSACTION_CREATE")));

    private UUID transactionId;

    @BeforeEach
    void setUp() {
        // The controller is a proxied bean: @PreAuthorize reads the SecurityContext,
        // not the Authentication argument.
        SecurityContextHolder.getContext().setAuthentication(authentication);

        JournalTemplate template = journalTemplateRepository.findAll().stream().findFirst().orElseThrow();
        Transaction transaction = new Transaction();
        transaction.setTransactionNumber("TRX-UPLOAD-" + UUID.randomUUID().toString().substring(0, 8));
        transaction.setTransactionDate(LocalDate.of(2024, 6, 30));
        transaction.setJournalTemplate(template);
        transaction.setAmount(new BigDecimal("150000.00"));
        transaction.setDescription("Uji unggah banyak berkas");
        transaction.setStatus(TransactionStatus.DRAFT);
        transactionId = transactionRepository.save(transaction).getId();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("two files in one request are both stored and reported")
    void uploadsEveryFileInOneRequest() throws Exception {
        ExtendedModelMap model = upload(pdf("bukti-1.pdf"), pdf("bukti-2.pdf"));

        assertThat(model.get("success")).isEqualTo(true);
        assertThat(model.get("message")).isEqualTo("2 dokumen berhasil diunggah");
        assertThat(documentService.findByTransactionId(transactionId))
                .extracting(document -> document.getOriginalFilename())
                .containsExactlyInAnyOrder("bukti-1.pdf", "bukti-2.pdf");
    }

    @Test
    @DisplayName("a rejected file keeps the stored ones and names the failure")
    void keepsSuccessfulFilesWhenOneIsRejected() throws Exception {
        MockMultipartFile rejected = new MockMultipartFile("file", "skrip.sh",
                "application/x-sh", "#!/bin/sh".getBytes(StandardCharsets.UTF_8));

        ExtendedModelMap model = upload(pdf("bukti-valid.pdf"), rejected);

        assertThat(model.get("success")).isEqualTo(true);
        assertThat((String) model.get("message"))
                .contains("1 dokumen berhasil diunggah")
                .contains("skrip.sh");
        assertThat(documentService.findByTransactionId(transactionId))
                .extracting(document -> document.getOriginalFilename())
                .containsExactly("bukti-valid.pdf");
    }

    @Test
    @DisplayName("a request without files reports that nothing was selected")
    void reportsEmptySelection() throws Exception {
        ExtendedModelMap model = upload();

        assertThat(model.get("success")).isEqualTo(false);
        assertThat(model.get("message")).isEqualTo("Tidak ada file yang dipilih");
        assertThat(documentService.findByTransactionId(transactionId)).isEmpty();
    }

    private ExtendedModelMap upload(MockMultipartFile... files) throws Exception {
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = documentController.uploadForTransaction(
                transactionId, files, null, authentication, response, model);

        assertThat(view).isEqualTo(VIEW_DOCUMENT_LIST);
        assertThat(response.getHeader("HX-Trigger")).isEqualTo("evidence-changed");
        return model;
    }

    private MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf",
                ("%PDF-1.4 " + name).getBytes(StandardCharsets.UTF_8));
    }
}
