package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.security.LogSanitizer;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Chart of Accounts", description = "CRUD for chart of accounts master data")
@PreAuthorize("hasAuthority('SCOPE_accounts:read')")
@RequiredArgsConstructor
@Slf4j
public class ChartOfAccountApiController {

    private final ChartOfAccountService chartOfAccountService;

    @GetMapping
    @Operation(summary = "List accounts with optional type filter")
    @ApiResponse(responseCode = "200", description = "List of accounts")
    public ResponseEntity<Page<AccountResponse>> list(
            @RequestParam(required = false) AccountType type,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        log.info("API: List accounts - type={}, search={}", type, LogSanitizer.sanitize(search));

        Page<ChartOfAccount> page;
        if (search != null && !search.isBlank()) {
            page = chartOfAccountService.search(search, true, pageable);
        } else if (type != null) {
            page = chartOfAccountService.findByAccountType(type, pageable);
        } else {
            page = chartOfAccountService.findAll(pageable);
        }

        return ResponseEntity.ok(page.map(AccountResponse::from));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get account by ID")
    @ApiResponse(responseCode = "200", description = "Account details")
    @ApiResponse(responseCode = "404", description = "Account not found")
    public ResponseEntity<AccountResponse> getById(@PathVariable UUID id) {
        log.info("API: Get account - id={}", LogSanitizer.sanitize(id.toString()));
        ChartOfAccount account = chartOfAccountService.findById(id);
        return ResponseEntity.ok(AccountResponse.from(account));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_accounts:write') and hasAuthority('ACCOUNT_CREATE')")
    @Operation(summary = "Create a new account")
    @ApiResponse(responseCode = "201", description = "Account created")
    public ResponseEntity<AccountResponse> create(
            @Valid @RequestBody AccountRequest request) {
        log.info("API: Create account - code={}", LogSanitizer.sanitize(request.accountCode()));

        ChartOfAccount entity = toEntity(request);
        entity.setActive(true);
        ChartOfAccount saved = chartOfAccountService.create(entity);

        log.info("API: Account created - id={}, code={}", saved.getId(), LogSanitizer.sanitize(saved.getAccountCode()));
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_accounts:write') and hasAuthority('ACCOUNT_EDIT')")
    @Operation(summary = "Update an account")
    @ApiResponse(responseCode = "200", description = "Account updated")
    @ApiResponse(responseCode = "404", description = "Account not found")
    public ResponseEntity<AccountResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody AccountRequest request) {
        log.info("API: Update account - id={}", LogSanitizer.sanitize(id.toString()));

        ChartOfAccount entity = toEntity(request);
        ChartOfAccount saved = chartOfAccountService.update(id, entity);

        log.info("API: Account updated - id={}, code={}", saved.getId(), LogSanitizer.sanitize(saved.getAccountCode()));
        return ResponseEntity.ok(AccountResponse.from(saved));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_accounts:write') and hasAuthority('ACCOUNT_DELETE')")
    @Operation(summary = "Delete an account (soft delete)")
    @ApiResponse(responseCode = "204", description = "Account deleted")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @ApiResponse(responseCode = "409", description = "Account has children or journal entries")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        log.info("API: Delete account - id={}", LogSanitizer.sanitize(id.toString()));

        chartOfAccountService.delete(id);

        log.info("API: Account deleted - id={}", LogSanitizer.sanitize(id.toString()));
        return ResponseEntity.noContent().build();
    }

    private ChartOfAccount toEntity(AccountRequest request) {
        ChartOfAccount entity = new ChartOfAccount();
        entity.setAccountCode(request.accountCode());
        entity.setAccountName(request.accountName());
        entity.setAccountType(request.accountType());
        entity.setNormalBalance(request.normalBalance());
        entity.setIsHeader(request.isHeader() != null && request.isHeader());
        entity.setPermanent(request.permanent() != null && request.permanent());
        entity.setDescription(request.description());

        if (request.parentId() != null) {
            ChartOfAccount parent = chartOfAccountService.findById(request.parentId());
            entity.setParent(parent);
        }

        return entity;
    }

    public record AccountRequest(
            @NotBlank(message = "Kode akun wajib diisi")
            @Size(max = 20, message = "Kode akun maksimal 20 karakter")
            String accountCode,

            @NotBlank(message = "Nama akun wajib diisi")
            @Size(max = 255, message = "Nama akun maksimal 255 karakter")
            String accountName,

            @NotNull(message = "Tipe akun wajib diisi")
            AccountType accountType,

            NormalBalance normalBalance,

            UUID parentId,

            Boolean isHeader,

            Boolean permanent,

            String description
    ) {}

    public record AccountResponse(
            UUID id,
            String accountCode,
            String accountName,
            AccountType accountType,
            NormalBalance normalBalance,
            UUID parentId,
            String parentCode,
            Integer level,
            boolean isHeader,
            boolean permanent,
            boolean active,
            String description
    ) {
        public static AccountResponse from(ChartOfAccount entity) {
            return new AccountResponse(
                    entity.getId(),
                    entity.getAccountCode(),
                    entity.getAccountName(),
                    entity.getAccountType(),
                    entity.getNormalBalance(),
                    entity.getParent() != null ? entity.getParent().getId() : null,
                    entity.getParent() != null ? entity.getParent().getAccountCode() : null,
                    entity.getLevel(),
                    Boolean.TRUE.equals(entity.getIsHeader()),
                    Boolean.TRUE.equals(entity.getPermanent()),
                    Boolean.TRUE.equals(entity.getActive()),
                    entity.getDescription()
            );
        }
    }
}
