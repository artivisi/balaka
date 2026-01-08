package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.security.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for handling GDPR/UU PDP data subject requests.
 * Implements:
 * - Right to Access (DSAR)
 * - Right to Erasure (True deletion)
 * - Right to Data Portability (Export)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataSubjectService {

    private static final String ERR_EMPLOYEE_NOT_FOUND = "Employee not found: ";

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final SecurityAuditService securityAuditService;

    /**
     * Export all personal data for a data subject (employee).
     * Implements GDPR Art. 15 (Right to Access) and Art. 20 (Data Portability).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> exportPersonalData(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException(ERR_EMPLOYEE_NOT_FOUND + employeeId));

        Map<String, Object> personalData = new HashMap<>();

        // Basic information
        personalData.put("employee_id", employee.getEmployeeId());
        personalData.put("name", employee.getName());
        personalData.put("email", employee.getEmail());
        personalData.put("phone", employee.getPhone());
        personalData.put("address", employee.getAddress());
        personalData.put("hire_date", employee.getHireDate());
        personalData.put("resign_date", employee.getResignDate());

        // Employment details
        personalData.put("job_title", employee.getJobTitle());
        personalData.put("department", employee.getDepartment());
        personalData.put("employment_type", employee.getEmploymentType());
        personalData.put("employment_status", employee.getEmploymentStatus());

        // Sensitive data (masked for export)
        personalData.put("nik_ktp", maskData(employee.getNikKtp()));
        personalData.put("npwp", maskData(employee.getNpwp()));
        personalData.put("bank_name", employee.getBankName());
        personalData.put("bank_account_number", maskData(employee.getBankAccountNumber()));
        personalData.put("bpjs_kesehatan_number", maskData(employee.getBpjsKesehatanNumber()));
        personalData.put("bpjs_ketenagakerjaan_number", maskData(employee.getBpjsKetenagakerjaanNumber()));

        // Timestamps
        personalData.put("created_at", employee.getCreatedAt());
        personalData.put("updated_at", employee.getUpdatedAt());
        personalData.put("export_timestamp", LocalDateTime.now());

        securityAuditService.log(AuditEventType.DATA_EXPORT,
                "Personal data exported for employee: " + employee.getName() + " (DSAR request)");

        return personalData;
    }

    /**
     * Anonymize personal data for a data subject (right to erasure).
     * Implements GDPR Art. 17 (Right to be Forgotten).
     *
     * Note: True deletion may not be possible for financial records due to legal
     * retention requirements. This anonymizes PII while preserving transaction history.
     */
    @Transactional
    public void anonymizeEmployee(UUID employeeId, String reason) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException(ERR_EMPLOYEE_NOT_FOUND + employeeId));

        String originalName = employee.getName();

        // Anonymize PII
        employee.setName("ANONYMIZED-" + employee.getId().toString().substring(0, 8));
        employee.setEmail(null);
        employee.setPhone(null);
        employee.setAddress(null);
        employee.setNikKtp(null);
        employee.setNpwp(null);
        employee.setBankName(null);
        employee.setBankAccountNumber(null);
        employee.setBankAccountName(null);
        employee.setBpjsKesehatanNumber(null);
        employee.setBpjsKetenagakerjaanNumber(null);
        employee.setNotes("Data anonymized per data subject request on " + LocalDateTime.now() + ". Reason: " + reason);

        // Deactivate
        employee.setActive(false);

        employeeRepository.save(employee);

        securityAuditService.log(AuditEventType.USER_DELETE,
                "Employee data anonymized (right to erasure): " + originalName + " -> ANONYMIZED. Reason: " + reason);

        log.info("Employee data anonymized: {} ({})",
                LogSanitizer.sanitize(originalName), employeeId);
    }

    /**
     * Delete user account completely.
     * For users without associated financial records.
     */
    @Transactional
    public void deleteUser(UUID userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        String username = user.getUsername();

        // Check if user has associated employee record by looking up employee by user
        Employee associatedEmployee = employeeRepository.findByUser(user).orElse(null);
        if (associatedEmployee != null) {
            throw new IllegalArgumentException(
                    "Cannot delete user with associated employee record. Use anonymizeEmployee instead.");
        }

        userRepository.delete(user);

        securityAuditService.log(AuditEventType.USER_DELETE,
                "User account deleted (right to erasure): " + username + ". Reason: " + reason);

        log.info("User account deleted: {}", username);
    }

    /**
     * Get data retention status for an employee.
     */
    @Transactional(readOnly = true)
    public DataRetentionStatus getRetentionStatus(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException(ERR_EMPLOYEE_NOT_FOUND + employeeId));

        // Check if employee has financial records that require retention
        // For now, assume all employees with payroll records need retention
        boolean hasFinancialRecords = true; // TODO: Check actual payroll records

        // Indonesian tax law requires 10 years retention for tax documents
        // UU KUP Art. 28 - pembukuan/pencatatan harus disimpan 10 tahun
        int retentionYears = 10;

        LocalDate resignDate = employee.getResignDate();
        LocalDateTime retentionEndDate = resignDate != null
                ? resignDate.atStartOfDay().plusYears(retentionYears)
                : null;

        boolean canBeDeleted = !hasFinancialRecords ||
                (retentionEndDate != null && LocalDateTime.now().isAfter(retentionEndDate));

        return new DataRetentionStatus(
                employeeId,
                hasFinancialRecords,
                retentionYears,
                retentionEndDate,
                canBeDeleted,
                canBeDeleted ? "Data can be deleted" : "Data must be retained for " + retentionYears + " years after resignation"
        );
    }

    private String maskData(String value) {
        if (value == null || value.length() < 4) {
            return value;
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    public record DataRetentionStatus(
            UUID employeeId,
            boolean hasFinancialRecords,
            int retentionYears,
            LocalDateTime retentionEndDate,
            boolean canBeDeleted,
            String message
    ) {}
}
