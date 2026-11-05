package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.service.CloseCheckService;
import com.artivisi.accountingfinance.service.CloseCheckService.CloseChecklist;
import com.artivisi.accountingfinance.service.CloseCheckService.CutOffFinding;
import com.artivisi.accountingfinance.service.CloseCheckService.InvoiceOutsideReceivables;
import com.artivisi.accountingfinance.service.CloseCheckService.ProceedsWarning;
import com.artivisi.accountingfinance.service.CloseCheckService.StaleClearing;
import com.artivisi.accountingfinance.service.CloseCheckService.TaxCreditCheck;
import com.artivisi.accountingfinance.service.CloseCheckService.UnmovedLiability;
import com.artivisi.accountingfinance.service.StatementService;
import com.artivisi.accountingfinance.service.StatementService.CurrentAccountStatement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only period close checks (issue #50). */
@RestController
@RequestMapping("/api/close-checks")
@Tag(name = "Close Checks", description = "Period close checklist: tax credits, clearing accounts, unmoved liabilities, "
        + "invoices outside receivables, cut-off, asset proceeds; shareholder current account statement")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SCOPE_close-checks:read')")
public class CloseCheckApiController {

    private final CloseCheckService closeCheckService;
    private final StatementService statementService;

    @GetMapping
    @Operation(summary = "Close checklist for a period",
            description = "Runs every check for [from, to]. Status PASS, WARNING, NOT_APPLICABLE or ERROR per check; "
                    + "ERROR carries the missing configuration in message.")
    @ApiResponse(responseCode = "400", description = "from or to missing, or to before from")
    public ResponseEntity<CloseChecklist> checklist(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(closeCheckService.checklist(from, to));
    }

    @GetMapping("/tax-credits")
    @Operation(summary = "TAX_CREDIT balances not offset after the PPh Badan return of returnYear",
            description = "filedDate null: no reported return of returnYear on or before asOf.")
    public ResponseEntity<TaxCreditCheck> taxCredits(
            @RequestParam int returnYear,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(closeCheckService.taxCredits(returnYear, asOf));
    }

    @GetMapping("/clearing")
    @Operation(summary = "CLEARING accounts with a balance and no reducing movement in the period")
    public ResponseEntity<List<StaleClearing>> clearing(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(closeCheckService.staleClearingBalances(from, to));
    }

    @GetMapping("/unmoved-liabilities")
    @Operation(summary = "Liability accounts with a balance and no posted line for the configured number of months",
            description = "months is the LIABILITY_UNMOVED alert threshold.")
    public ResponseEntity<Map<String, Object>> unmovedLiabilities(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        int months = closeCheckService.configuredUnmovedMonths();
        List<UnmovedLiability> accounts = closeCheckService.unmovedLiabilities(asOf, months);
        return ResponseEntity.ok(Map.of("asOf", asOf, "months", months, "accounts", accounts));
    }

    @GetMapping("/invoices-outside-receivables")
    @Operation(summary = "Issued invoices in the period not fully debited to the configured receivable account")
    public ResponseEntity<List<InvoiceOutsideReceivables>> invoicesOutsideReceivables(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(closeCheckService.invoicesOutsideReceivables(from, to));
    }

    @GetMapping("/cut-off")
    @Operation(summary = "Posted journals dated after the period for invoices, bills and faktur dated in it")
    public ResponseEntity<List<CutOffFinding>> cutOff(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(closeCheckService.cutOffExceptions(from, to));
    }

    @GetMapping("/asset-proceeds")
    @Operation(summary = "Asset disposals whose proceeds went to a shareholder current account or a non-company account")
    public ResponseEntity<List<ProceedsWarning>> assetProceeds(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(closeCheckService.assetProceedsWarnings(from, to));
    }

    @GetMapping("/shareholder-statement")
    @Operation(summary = "Statement of a shareholder/director current account",
            description = "Balance is credit minus debit: positive, the company owes the shareholder or director.")
    @ApiResponse(responseCode = "400", description = "Account is not a SHAREHOLDER_CURRENT account, or dates invalid")
    public ResponseEntity<CurrentAccountStatement> shareholderStatement(
            @RequestParam UUID accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(statementService.generateShareholderStatement(accountId, from, to));
    }
}
