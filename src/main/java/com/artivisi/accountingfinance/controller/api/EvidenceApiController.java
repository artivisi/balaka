package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.service.BankStatementArchiveService;
import com.artivisi.accountingfinance.service.BankStatementArchiveService.MissingStatement;
import com.artivisi.accountingfinance.service.EvidenceQueryService;
import com.artivisi.accountingfinance.service.EvidenceQueryService.ContractSummary;
import com.artivisi.accountingfinance.service.EvidenceQueryService.Requirement;
import com.artivisi.accountingfinance.service.EvidenceQueryService.TransactionEvidence;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read-only evidence layer (issue #48). Rules, contracts and archives are maintained in the web UI. */
@RestController
@RequestMapping("/api/evidence")
@Tag(name = "Evidence", description = "Required documents per transaction, evidence gaps, contract register, bank statement archive")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SCOPE_evidence:read')")
public class EvidenceApiController {

    private final EvidenceQueryService evidenceQueryService;
    private final BankStatementArchiveService archiveService;

    @GetMapping("/requirements")
    @Operation(summary = "Configured evidence requirements",
            description = "Each rule targets one account (covering its sub-accounts) or one template family. "
                    + "minAmount null applies to every amount.")
    public ResponseEntity<List<Requirement>> requirements() {
        return ResponseEntity.ok(evidenceQueryService.requirements());
    }

    @GetMapping("/transactions/{id}")
    @Operation(summary = "Required, present and missing evidence of one transaction",
            description = "Evaluated for any status; the gaps endpoint covers posted, non-closing transactions only.")
    @ApiResponse(responseCode = "404", description = "Transaction not found")
    public ResponseEntity<TransactionEvidence> transaction(@PathVariable UUID id) {
        return ResponseEntity.ok(evidenceQueryService.evidenceFor(id));
    }

    @GetMapping("/gaps")
    @Operation(summary = "Posted transactions in a period with required evidence missing")
    @ApiResponse(responseCode = "400", description = "from or to missing, or to before from")
    public ResponseEntity<List<TransactionEvidence>> gaps(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(evidenceQueryService.gaps(from, to));
    }

    @GetMapping("/index")
    @Operation(summary = "Every posted, non-closing transaction in a period with its evidence",
            description = "The evidence index of the audit pack.")
    public ResponseEntity<List<TransactionEvidence>> index(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(evidenceQueryService.index(from, to));
    }

    @GetMapping("/contracts")
    @Operation(summary = "Contract register", description = "Optionally filtered by clientId or vendorId.")
    public ResponseEntity<List<ContractSummary>> contracts(@RequestParam(required = false) UUID clientId,
                                                           @RequestParam(required = false) UUID vendorId) {
        return ResponseEntity.ok(evidenceQueryService.contracts(clientId, vendorId));
    }

    @GetMapping("/bank-statements/missing")
    @Operation(summary = "Months without an archived bank statement",
            description = "A month is due once graceDays have passed after its end, as of asOf.")
    public ResponseEntity<List<MissingStatement>> missingStatements(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam int graceDays) {
        return ResponseEntity.ok(archiveService.missingStatements(asOf, graceDays));
    }
}
