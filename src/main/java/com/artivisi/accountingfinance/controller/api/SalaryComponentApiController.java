package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.entity.SalaryComponent;
import com.artivisi.accountingfinance.entity.SalaryComponentType;
import com.artivisi.accountingfinance.service.SalaryComponentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/salary-components")
@Tag(name = "Salary Components", description = "CRUD for salary component master data")
@PreAuthorize("hasAuthority('SCOPE_tax-export:read') and hasAuthority('SALARY_COMPONENT_VIEW')")
@RequiredArgsConstructor
@Slf4j
public class SalaryComponentApiController {

    private final SalaryComponentService salaryComponentService;

    @GetMapping
    @Operation(summary = "List active salary components")
    @ApiResponse(responseCode = "200", description = "List of active components")
    public ResponseEntity<List<SalaryComponentResponse>> list() {
        List<SalaryComponentResponse> responses = salaryComponentService.findAllActive()
                .stream()
                .map(SalaryComponentResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_payroll:write') and hasAuthority('SALARY_COMPONENT_MANAGE')")
    @Operation(summary = "Create a salary component")
    @ApiResponse(responseCode = "201", description = "Component created")
    public ResponseEntity<SalaryComponentResponse> create(
            @Valid @RequestBody SalaryComponentRequest request) {
        log.info("API: Create salary component - code={}", request.code());

        SalaryComponent entity = toEntity(request);
        SalaryComponent saved = salaryComponentService.create(entity);

        log.info("API: Salary component created - id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(SalaryComponentResponse.from(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_payroll:write') and hasAuthority('SALARY_COMPONENT_MANAGE')")
    @Operation(summary = "Update a salary component")
    @ApiResponse(responseCode = "200", description = "Component updated")
    @ApiResponse(responseCode = "404", description = "Component not found")
    public ResponseEntity<SalaryComponentResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody SalaryComponentRequest request) {
        log.info("API: Update salary component - id={}", id);

        SalaryComponent updated = toEntity(request);
        SalaryComponent saved = salaryComponentService.update(id, updated);

        log.info("API: Salary component updated - id={}", saved.getId());
        return ResponseEntity.ok(SalaryComponentResponse.from(saved));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_payroll:write') and hasAuthority('SALARY_COMPONENT_MANAGE')")
    @Operation(summary = "Deactivate a salary component (soft delete)")
    @ApiResponse(responseCode = "204", description = "Component deactivated")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        log.info("API: Deactivate salary component - id={}", id);

        salaryComponentService.deactivate(id);

        log.info("API: Salary component deactivated - id={}", id);
        return ResponseEntity.noContent().build();
    }

    private SalaryComponent toEntity(SalaryComponentRequest request) {
        SalaryComponent entity = new SalaryComponent();
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setComponentType(request.componentType());
        entity.setIsPercentage(request.isPercentage() != null && request.isPercentage());
        entity.setDefaultRate(request.defaultRate());
        entity.setDefaultAmount(request.defaultAmount());
        entity.setIsTaxable(request.isTaxable() == null || request.isTaxable());
        entity.setActive(true);
        return entity;
    }

    public record SalaryComponentRequest(
            @NotBlank(message = "Kode wajib diisi")
            String code,

            @NotBlank(message = "Nama wajib diisi")
            String name,

            String description,

            @NotNull(message = "Tipe komponen wajib diisi")
            SalaryComponentType componentType,

            Boolean isPercentage,
            BigDecimal defaultRate,
            BigDecimal defaultAmount,
            Boolean isTaxable
    ) {}

    public record SalaryComponentResponse(
            UUID id,
            String code,
            String name,
            String description,
            SalaryComponentType componentType,
            boolean isPercentage,
            BigDecimal defaultRate,
            BigDecimal defaultAmount,
            boolean isTaxable,
            boolean isSystem,
            boolean active
    ) {
        public static SalaryComponentResponse from(SalaryComponent entity) {
            return new SalaryComponentResponse(
                    entity.getId(),
                    entity.getCode(),
                    entity.getName(),
                    entity.getDescription(),
                    entity.getComponentType(),
                    entity.isPercentageBased(),
                    entity.getDefaultRate(),
                    entity.getDefaultAmount(),
                    Boolean.TRUE.equals(entity.getIsTaxable()),
                    entity.isSystemComponent(),
                    entity.isActive()
            );
        }
    }
}
