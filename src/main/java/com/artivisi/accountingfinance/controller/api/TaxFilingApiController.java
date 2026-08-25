package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.dto.DocumentResponse;
import com.artivisi.accountingfinance.entity.TaxDocument;
import com.artivisi.accountingfinance.entity.TaxFiling;
import com.artivisi.accountingfinance.enums.TaxDocumentType;
import com.artivisi.accountingfinance.enums.TaxFilingStatus;
import com.artivisi.accountingfinance.enums.TaxFilingType;
import com.artivisi.accountingfinance.security.CurrentUser;
import com.artivisi.accountingfinance.security.LogSanitizer;
import com.artivisi.accountingfinance.service.TaxFilingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST API for the tax filing register (issue #35). Coretax documents that
 * attach to a masa pajak rather than to a journal entry — SPT, BPE, STP,
 * teguran, SP2DK, SKPLB — have no home on a transaction; they live here.
 */
@RestController
@RequestMapping("/api/tax-filings")
@Tag(name = "Tax Filings", description = "Register of period-linked tax records: SPT Masa/Tahunan "
        + "with their BPE, billing code, and setoran, plus the STP / teguran / SP2DK paperwork "
        + "attached to a tax period. Transaction-linked tax documents (faktur pajak, bukti potong) "
        + "belong on /api/transactions/{id}/documents instead.")
@RequiredArgsConstructor
@Slf4j
public class TaxFilingApiController {

    private static final String PERIOD_PATTERN = "^\\d{4}(-(0[1-9]|1[0-2]))?$";

    private final TaxFilingService taxFilingService;

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_tax-filings:read')")
    @Operation(summary = "List tax filings",
            description = "Optional filters: taxType and year. Newest period first.")
    @ApiResponse(responseCode = "200", description = "Tax filings")
    public ResponseEntity<List<TaxFilingResponse>> list(
            @RequestParam(required = false) TaxFilingType taxType,
            @RequestParam(required = false) Integer year) {
        List<TaxFilingResponse> responses = taxFilingService.findByFilters(taxType, year)
                .stream()
                .map(TaxFilingResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_tax-filings:read')")
    @Operation(summary = "Get a tax filing with its attached documents")
    @ApiResponse(responseCode = "200", description = "Tax filing detail")
    @ApiResponse(responseCode = "404", description = "Filing not found")
    public ResponseEntity<TaxFilingDetailResponse> detail(@PathVariable UUID id) {
        TaxFiling filing = taxFilingService.findById(id);
        List<TaxDocumentResponse> documents = taxFilingService.findDocuments(id)
                .stream()
                .map(TaxDocumentResponse::from)
                .toList();
        return ResponseEntity.ok(TaxFilingDetailResponse.from(filing, documents));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_tax-filings:write')")
    @Operation(summary = "Register a tax filing",
            description = "period is YYYY-MM for masa returns (PPN, PPH21, PPH23_UNIFIKASI) and "
                    + "YYYY for the annual PPH_BADAN return. A filing is identified by "
                    + "(taxType, period, pembetulanNo); registering the same key twice returns 409. "
                    + "paymentTransactionId is optional — a nihil masa has no setoran, and an FP-03 "
                    + "(BUMN pemungut) masa is report-only.")
    @ApiResponse(responseCode = "201", description = "Filing registered")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "404", description = "Payment transaction not found")
    @ApiResponse(responseCode = "409", description = "Filing already registered for this period")
    public ResponseEntity<TaxFilingDetailResponse> create(@Valid @RequestBody TaxFilingRequest request) {
        log.info("API: Register tax filing - taxType={}, period={}, pembetulanNo={}",
                request.taxType(), LogSanitizer.sanitize(request.period()), request.pembetulanNo());

        TaxFiling saved = taxFilingService.create(toEntity(request), request.paymentTransactionId());

        log.info("API: Tax filing registered - id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TaxFilingDetailResponse.from(saved, List.of()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_tax-filings:write')")
    @Operation(summary = "Update a tax filing",
            description = "Full replacement of the filing fields. Omitting paymentTransactionId "
                    + "unlinks the setoran.")
    @ApiResponse(responseCode = "200", description = "Filing updated")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "404", description = "Filing or payment transaction not found")
    @ApiResponse(responseCode = "409", description = "Another filing already holds this period key")
    public ResponseEntity<TaxFilingDetailResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody TaxFilingRequest request) {
        log.info("API: Update tax filing - id={}", id);

        TaxFiling saved = taxFilingService.update(id, toEntity(request), request.paymentTransactionId());
        List<TaxDocumentResponse> documents = taxFilingService.findDocuments(id)
                .stream()
                .map(TaxDocumentResponse::from)
                .toList();

        log.info("API: Tax filing updated - id={}", saved.getId());
        return ResponseEntity.ok(TaxFilingDetailResponse.from(saved, documents));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_tax-filings:write')")
    @Operation(summary = "Delete a tax filing",
            description = "Only allowed while no documents are attached. Attached SPT/BPE/STP scans "
                    + "carry a 10-year retention obligation, so they must be removed one by one first.")
    @ApiResponse(responseCode = "204", description = "Filing deleted")
    @ApiResponse(responseCode = "404", description = "Filing not found")
    @ApiResponse(responseCode = "409", description = "Filing still has attached documents")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        log.info("API: Delete tax filing - id={}", id);
        taxFilingService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== DOCUMENTS ====================

    @PostMapping(path = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SCOPE_tax-filings:write')")
    @Operation(summary = "Attach a document to a tax filing",
            description = "Multipart upload. The file goes to the same encrypted store as transaction "
                    + "attachments (checksum + 10-year retention); the metadata fields record the "
                    + "document chain — referenceNumber points at the document this one answers, "
                    + "e.g. a teguran referencing the STP it chases.")
    @ApiResponse(responseCode = "201", description = "Document attached")
    @ApiResponse(responseCode = "400", description = "Missing file or disallowed file type")
    @ApiResponse(responseCode = "404", description = "Filing not found")
    public ResponseEntity<TaxDocumentResponse> uploadDocument(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("docType") TaxDocumentType docType,
            @RequestParam(required = false) String docNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate docDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate,
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) String referenceNumber,
            @RequestParam(required = false) String notes) throws IOException {
        String username = CurrentUser.name();
        log.info("API: Attach tax document - filingId={}, docType={}, docNumber={}, filename={}, user={}",
                id, docType, LogSanitizer.sanitize(docNumber),
                LogSanitizer.sanitize(file.getOriginalFilename()), LogSanitizer.sanitize(username));

        TaxDocument metadata = new TaxDocument();
        metadata.setDocType(docType);
        metadata.setDocNumber(docNumber);
        metadata.setDocDate(docDate);
        metadata.setDueDate(dueDate);
        metadata.setAmount(amount);
        metadata.setReferenceNumber(referenceNumber);
        metadata.setNotes(notes);

        TaxDocument saved = taxFilingService.addDocument(id, file, metadata, username);

        log.info("API: Tax document attached - id={}, filingId={}", saved.getId(), id);
        return ResponseEntity.status(HttpStatus.CREATED).body(TaxDocumentResponse.from(saved));
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAuthority('SCOPE_tax-filings:read')")
    @Operation(summary = "List documents attached to a tax filing")
    @ApiResponse(responseCode = "200", description = "Attached documents")
    @ApiResponse(responseCode = "404", description = "Filing not found")
    public ResponseEntity<List<TaxDocumentResponse>> listDocuments(@PathVariable UUID id) {
        List<TaxDocumentResponse> responses = taxFilingService.findDocuments(id)
                .stream()
                .map(TaxDocumentResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}/documents/{taxDocumentId}/download")
    @PreAuthorize("hasAuthority('SCOPE_tax-filings:read')")
    @Operation(summary = "Download an attached document")
    @ApiResponse(responseCode = "200", description = "File stream")
    @ApiResponse(responseCode = "404", description = "Filing or document not found")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable UUID id, @PathVariable UUID taxDocumentId) {
        TaxDocument taxDocument = taxFilingService.findDocument(id, taxDocumentId);
        Resource resource = taxFilingService.loadDocument(id, taxDocumentId);

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(taxDocument.getDocument().getOriginalFilename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(taxDocument.getDocument().getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(resource);
    }

    @DeleteMapping("/{id}/documents/{taxDocumentId}")
    @PreAuthorize("hasAuthority('SCOPE_tax-filings:write')")
    @Operation(summary = "Remove an attached document",
            description = "Deletes the stored file and the tax metadata. The generic "
                    + "DELETE /api/documents/{docId} refuses files owned by this register.")
    @ApiResponse(responseCode = "204", description = "Document removed")
    @ApiResponse(responseCode = "404", description = "Filing or document not found")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable UUID id, @PathVariable UUID taxDocumentId) throws IOException {
        log.info("API: Delete tax document - filingId={}, id={}", id, taxDocumentId);
        taxFilingService.deleteDocument(id, taxDocumentId);
        return ResponseEntity.noContent().build();
    }

    // ==================== HELPERS ====================

    private TaxFiling toEntity(TaxFilingRequest request) {
        TaxFiling filing = new TaxFiling();
        filing.setTaxType(request.taxType());
        filing.setPeriodYear(parseYear(request.period()));
        filing.setPeriodMonth(parseMonth(request.period()));
        Integer pembetulanNo = request.pembetulanNo();
        filing.setPembetulanNo(pembetulanNo == null ? Integer.valueOf(0) : pembetulanNo);
        filing.setStatus(request.status());
        filing.setFiledDate(request.filedDate());
        filing.setBpeNumber(request.bpeNumber());
        filing.setBpeDate(request.bpeDate());
        filing.setKurangBayar(request.kurangBayar());
        filing.setLebihBayar(request.lebihBayar());
        filing.setNihil(Boolean.TRUE.equals(request.nihil()));
        filing.setBillingCode(request.billingCode());
        filing.setNtpn(request.ntpn());
        filing.setPaidDate(request.paidDate());
        filing.setNotes(request.notes());
        return filing;
    }

    private int parseYear(String period) {
        return Integer.parseInt(period.substring(0, 4));
    }

    private Integer parseMonth(String period) {
        return period.length() == 4 ? null : Integer.valueOf(period.substring(5, 7));
    }

    // ==================== DTOs ====================

    public record TaxFilingRequest(
            @NotNull(message = "Jenis pajak wajib diisi")
            TaxFilingType taxType,

            @NotNull(message = "Periode wajib diisi")
            @Pattern(regexp = PERIOD_PATTERN,
                    message = "Periode harus YYYY-MM untuk SPT Masa atau YYYY untuk SPT Tahunan")
            String period,

            @PositiveOrZero(message = "Nomor pembetulan tidak boleh negatif")
            Integer pembetulanNo,

            @NotNull(message = "Status pelaporan wajib diisi")
            TaxFilingStatus status,

            LocalDate filedDate,

            @Size(max = 100, message = "Nomor BPE maksimal 100 karakter")
            String bpeNumber,

            LocalDate bpeDate,

            @PositiveOrZero(message = "Kurang bayar tidak boleh negatif")
            BigDecimal kurangBayar,

            @PositiveOrZero(message = "Lebih bayar tidak boleh negatif")
            BigDecimal lebihBayar,

            Boolean nihil,

            @Size(max = 50, message = "Kode billing maksimal 50 karakter")
            String billingCode,

            @Size(max = 50, message = "NTPN maksimal 50 karakter")
            String ntpn,

            LocalDate paidDate,

            UUID paymentTransactionId,

            String notes
    ) {}

    public record TaxFilingResponse(
            UUID id,
            TaxFilingType taxType,
            String period,
            Integer pembetulanNo,
            TaxFilingStatus status,
            LocalDate filedDate,
            String bpeNumber,
            LocalDate bpeDate,
            BigDecimal kurangBayar,
            BigDecimal lebihBayar,
            boolean nihil,
            String billingCode,
            String ntpn,
            LocalDate paidDate,
            UUID paymentTransactionId,
            String paymentTransactionNumber,
            String notes
    ) {
        public static TaxFilingResponse from(TaxFiling filing) {
            return new TaxFilingResponse(
                    filing.getId(),
                    filing.getTaxType(),
                    filing.getPeriod(),
                    filing.getPembetulanNo(),
                    filing.getStatus(),
                    filing.getFiledDate(),
                    filing.getBpeNumber(),
                    filing.getBpeDate(),
                    filing.getKurangBayar(),
                    filing.getLebihBayar(),
                    filing.isNihil(),
                    filing.getBillingCode(),
                    filing.getNtpn(),
                    filing.getPaidDate(),
                    filing.getPaymentTransaction() == null ? null : filing.getPaymentTransaction().getId(),
                    filing.getPaymentTransaction() == null
                            ? null : filing.getPaymentTransaction().getTransactionNumber(),
                    filing.getNotes());
        }
    }

    public record TaxFilingDetailResponse(
            TaxFilingResponse filing,
            List<TaxDocumentResponse> documents
    ) {
        public static TaxFilingDetailResponse from(TaxFiling filing, List<TaxDocumentResponse> documents) {
            return new TaxFilingDetailResponse(TaxFilingResponse.from(filing), documents);
        }
    }

    public record TaxDocumentResponse(
            UUID id,
            TaxDocumentType docType,
            String docNumber,
            LocalDate docDate,
            LocalDate dueDate,
            BigDecimal amount,
            String referenceNumber,
            String notes,
            DocumentResponse file
    ) {
        public static TaxDocumentResponse from(TaxDocument taxDocument) {
            return new TaxDocumentResponse(
                    taxDocument.getId(),
                    taxDocument.getDocType(),
                    taxDocument.getDocNumber(),
                    taxDocument.getDocDate(),
                    taxDocument.getDueDate(),
                    taxDocument.getAmount(),
                    taxDocument.getReferenceNumber(),
                    taxDocument.getNotes(),
                    DocumentResponse.from(taxDocument.getDocument()));
        }
    }
}
