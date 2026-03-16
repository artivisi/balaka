package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.EmployeeSalaryComponent;
import com.artivisi.accountingfinance.entity.EmploymentStatus;
import com.artivisi.accountingfinance.entity.EmploymentType;
import com.artivisi.accountingfinance.entity.PtkpStatus;
import com.artivisi.accountingfinance.entity.SalaryComponent;
import com.artivisi.accountingfinance.repository.EmployeeSalaryComponentRepository;
import com.artivisi.accountingfinance.service.EmployeeService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/employees")
@Tag(name = "Employees", description = "Employee master data and salary component assignments")
@PreAuthorize("hasAuthority('SCOPE_tax-export:read')")
@RequiredArgsConstructor
@Slf4j
public class EmployeeApiController {

    private final EmployeeService employeeService;
    private final SalaryComponentService salaryComponentService;
    private final EmployeeSalaryComponentRepository employeeSalaryComponentRepository;

    @GetMapping
    @Operation(summary = "List employees with optional filters")
    @ApiResponse(responseCode = "200", description = "List of employees")
    public ResponseEntity<List<EmployeeResponse>> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) EmploymentStatus status) {
        List<Employee> employees;
        if (active != null && active && status != null) {
            employees = employeeService.findByEmploymentStatus(status)
                    .stream().filter(e -> Boolean.TRUE.equals(e.getActive())).toList();
        } else if (status != null) {
            employees = employeeService.findByEmploymentStatus(status);
        } else if (active != null && active) {
            employees = employeeService.findActiveEmployees();
        } else {
            employees = employeeService.findActiveEmployees();
        }

        List<EmployeeResponse> responses = employees.stream()
                .map(e -> toResponse(e, loadSalaryComponents(e)))
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping
    @Operation(summary = "Create an employee")
    @ApiResponse(responseCode = "201", description = "Employee created")
    public ResponseEntity<EmployeeResponse> create(
            @Valid @RequestBody EmployeeRequest request) {
        log.info("API: Create employee - name={}", request.name());

        Employee entity = toEntity(request);
        Employee saved = employeeService.create(entity);

        log.info("API: Employee created - id={}, employeeId={}", saved.getId(), saved.getEmployeeId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(saved, List.of()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get employee detail with salary components")
    @ApiResponse(responseCode = "200", description = "Employee detail")
    @ApiResponse(responseCode = "404", description = "Employee not found")
    public ResponseEntity<EmployeeResponse> get(@PathVariable UUID id) {
        Employee employee = employeeService.findById(id);
        return ResponseEntity.ok(toResponse(employee, loadSalaryComponents(employee)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update employee master data")
    @ApiResponse(responseCode = "200", description = "Employee updated")
    @ApiResponse(responseCode = "404", description = "Employee not found")
    public ResponseEntity<EmployeeResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody EmployeeRequest request) {
        log.info("API: Update employee - id={}", id);

        Employee updated = toEntity(request);
        Employee saved = employeeService.update(id, updated);

        log.info("API: Employee updated - id={}", saved.getId());
        return ResponseEntity.ok(toResponse(saved, loadSalaryComponents(saved)));
    }

    @PostMapping("/{id}/salary-components")
    @Operation(summary = "Assign a salary component to an employee",
            description = "Same component can be assigned multiple times with non-overlapping date ranges. "
                    + "If endDate is null, the assignment is open-ended. "
                    + "Returns 400 if the date range overlaps with an existing assignment of the same component.")
    @ApiResponse(responseCode = "201", description = "Component assigned")
    @ApiResponse(responseCode = "400", description = "Overlapping date range with existing assignment")
    @ApiResponse(responseCode = "404", description = "Employee or component not found")
    public ResponseEntity<SalaryAssignmentResponse> assignComponent(
            @PathVariable UUID id,
            @Valid @RequestBody SalaryAssignmentRequest request) {
        log.info("API: Assign salary component - employeeId={}, componentId={}", id, request.salaryComponentId());

        Employee employee = employeeService.findById(id);
        SalaryComponent component = salaryComponentService.findById(request.salaryComponentId());

        EmployeeSalaryComponent assignment = salaryComponentService.assignComponentToEmployee(
                employee, component, request.effectiveDate(), request.endDate(), null, request.amount(), null);

        log.info("API: Salary component assigned - id={}", assignment.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SalaryAssignmentResponse.from(assignment));
    }

    @PutMapping("/{id}/salary-components/{componentId}")
    @Operation(summary = "Update a salary component assignment")
    @ApiResponse(responseCode = "200", description = "Assignment updated")
    public ResponseEntity<SalaryAssignmentResponse> updateComponent(
            @PathVariable UUID id,
            @PathVariable UUID componentId,
            @Valid @RequestBody SalaryAssignmentRequest request) {
        log.info("API: Update salary assignment - employeeId={}, assignmentId={}", id, componentId);

        EmployeeSalaryComponent assignment = employeeSalaryComponentRepository.findById(componentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment tidak ditemukan"));

        assignment.setAmount(request.amount());
        assignment.setEffectiveDate(request.effectiveDate());
        assignment.setEndDate(request.endDate());

        EmployeeSalaryComponent saved = employeeSalaryComponentRepository.save(assignment);

        log.info("API: Salary assignment updated - id={}", saved.getId());
        return ResponseEntity.ok(SalaryAssignmentResponse.from(saved));
    }

    private List<EmployeeSalaryComponent> loadSalaryComponents(Employee employee) {
        return employeeSalaryComponentRepository.findByEmployeeId(employee.getId());
    }

    private Employee toEntity(EmployeeRequest request) {
        Employee entity = new Employee();
        entity.setEmployeeId(request.employeeId());
        entity.setName(request.name());
        entity.setEmail(request.email());
        entity.setPhone(request.phone());
        entity.setAddress(request.address());
        entity.setNpwp(request.npwp());
        entity.setNikKtp(request.nikKtp());
        entity.setPtkpStatus(request.ptkpStatus() != null ? request.ptkpStatus() : PtkpStatus.TK_0);
        entity.setHireDate(request.hireDate());
        entity.setResignDate(request.resignDate());
        entity.setEmploymentType(request.employmentType() != null ? request.employmentType() : EmploymentType.PERMANENT);
        entity.setEmploymentStatus(request.employmentStatus() != null ? request.employmentStatus() : EmploymentStatus.ACTIVE);
        entity.setJobTitle(request.jobTitle());
        entity.setDepartment(request.department());
        entity.setBpjsKesehatanNumber(request.bpjsKesehatanNumber());
        entity.setBpjsKetenagakerjaanNumber(request.bpjsKetenagakerjaanNumber());
        entity.setActive(true);
        return entity;
    }

    private EmployeeResponse toResponse(Employee e, List<EmployeeSalaryComponent> components) {
        return new EmployeeResponse(
                e.getId(),
                e.getEmployeeId(),
                e.getName(),
                e.getEmail(),
                e.getPhone(),
                e.getNpwp(),
                e.getNikKtp(),
                e.getPtkpStatus(),
                e.getHireDate(),
                e.getResignDate(),
                e.getEmploymentType(),
                e.getEmploymentStatus(),
                e.getJobTitle(),
                e.getDepartment(),
                e.getBpjsKesehatanNumber(),
                e.getBpjsKetenagakerjaanNumber(),
                components.stream().map(SalaryAssignmentResponse::from).toList()
        );
    }

    public record EmployeeRequest(
            @NotBlank(message = "NIK karyawan wajib diisi")
            String employeeId,

            @NotBlank(message = "Nama wajib diisi")
            String name,

            String email,
            String phone,
            String address,
            String npwp,
            String nikKtp,
            PtkpStatus ptkpStatus,

            @NotNull(message = "Tanggal masuk wajib diisi")
            LocalDate hireDate,

            LocalDate resignDate,
            EmploymentType employmentType,
            EmploymentStatus employmentStatus,
            String jobTitle,
            String department,
            String bpjsKesehatanNumber,
            String bpjsKetenagakerjaanNumber
    ) {}

    public record EmployeeResponse(
            UUID id,
            String employeeId,
            String name,
            String email,
            String phone,
            String npwp,
            String nikKtp,
            PtkpStatus ptkpStatus,
            LocalDate hireDate,
            LocalDate resignDate,
            EmploymentType employmentType,
            EmploymentStatus employmentStatus,
            String jobTitle,
            String department,
            String bpjsKesehatanNumber,
            String bpjsKetenagakerjaanNumber,
            List<SalaryAssignmentResponse> salaryComponents
    ) {}

    public record SalaryAssignmentRequest(
            @NotNull(message = "ID komponen gaji wajib diisi")
            UUID salaryComponentId,

            @NotNull(message = "Jumlah wajib diisi")
            BigDecimal amount,

            @NotNull(message = "Tanggal efektif wajib diisi")
            LocalDate effectiveDate,

            LocalDate endDate
    ) {}

    public record SalaryAssignmentResponse(
            UUID id,
            UUID salaryComponentId,
            String componentCode,
            String componentName,
            BigDecimal amount,
            LocalDate effectiveDate,
            LocalDate endDate
    ) {
        public static SalaryAssignmentResponse from(EmployeeSalaryComponent esc) {
            return new SalaryAssignmentResponse(
                    esc.getId(),
                    esc.getSalaryComponent().getId(),
                    esc.getSalaryComponent().getCode(),
                    esc.getSalaryComponent().getName(),
                    esc.getAmount(),
                    esc.getEffectiveDate(),
                    esc.getEndDate()
            );
        }
    }
}
