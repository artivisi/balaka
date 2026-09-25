package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.EmploymentStatus;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    public Employee findById(UUID id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Karyawan tidak ditemukan dengan id: " + id));
    }

    public Employee findByEmployeeId(String employeeId) {
        return employeeRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Karyawan tidak ditemukan dengan NIK: " + employeeId));
    }

    public Page<Employee> findAll(Pageable pageable) {
        return employeeRepository.findAll(pageable);
    }

    public Page<Employee> findByFilters(String search, EmploymentStatus status, Boolean active, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return employeeRepository.findByFiltersAndSearch(search, status, active, pageable);
        }
        return employeeRepository.findByFilters(status, active, pageable);
    }

    public List<Employee> findActiveEmployees() {
        return employeeRepository.findAllActive();
    }

    public List<Employee> findByEmploymentStatus(EmploymentStatus status) {
        return employeeRepository.findByEmploymentStatus(status);
    }

    @Transactional
    public Employee create(Employee employee) {
        validateNewEmployee(employee);
        employee.setActive(true);
        return employeeRepository.save(employee);
    }

    @Transactional
    public Employee update(UUID id, Employee updatedEmployee) {
        Employee existing = findById(id);
        validateUpdatedEmployee(existing, updatedEmployee);

        existing.setEmployeeId(updatedEmployee.getEmployeeId());
        existing.setName(updatedEmployee.getName());
        existing.setEmail(updatedEmployee.getEmail());
        existing.setPhone(updatedEmployee.getPhone());
        existing.setAddress(updatedEmployee.getAddress());
        existing.setNpwp(updatedEmployee.getNpwp());
        existing.setNikKtp(updatedEmployee.getNikKtp());
        existing.setPtkpStatus(updatedEmployee.getPtkpStatus());
        existing.setHireDate(updatedEmployee.getHireDate());
        existing.setResignDate(updatedEmployee.getResignDate());
        existing.setEmploymentType(updatedEmployee.getEmploymentType());
        existing.setEmploymentStatus(updatedEmployee.getEmploymentStatus());
        existing.setJobTitle(updatedEmployee.getJobTitle());
        existing.setDepartment(updatedEmployee.getDepartment());
        existing.setBankName(updatedEmployee.getBankName());
        existing.setBankAccountNumber(updatedEmployee.getBankAccountNumber());
        existing.setBankAccountName(updatedEmployee.getBankAccountName());
        existing.setBpjsKesehatanNumber(updatedEmployee.getBpjsKesehatanNumber());
        existing.setBpjsKetenagakerjaanNumber(updatedEmployee.getBpjsKetenagakerjaanNumber());
        existing.setNotes(updatedEmployee.getNotes());

        return employeeRepository.save(existing);
    }

    @Transactional
    public Employee updateWithUserLink(UUID id, Employee updatedEmployee) {
        Employee saved = update(id, updatedEmployee);
        saved.setUser(updatedEmployee.getUser());
        return employeeRepository.save(saved);
    }

    @Transactional
    public void deactivate(UUID id) {
        Employee employee = findById(id);
        employee.setActive(false);
        employeeRepository.save(employee);
    }

    @Transactional
    public void activate(UUID id) {
        Employee employee = findById(id);
        employee.setActive(true);
        employeeRepository.save(employee);
    }

    public long countActiveEmployees() {
        return employeeRepository.countActiveEmployees();
    }

    private void validateNewEmployee(Employee employee) {
        if (employeeRepository.existsByEmployeeId(employee.getEmployeeId())) {
            throw new IllegalArgumentException("NIK karyawan sudah digunakan: " + employee.getEmployeeId());
        }
        validateNpwp(employee.getNpwp(), null);
    }

    private void validateUpdatedEmployee(Employee existing, Employee updated) {
        if (!existing.getEmployeeId().equals(updated.getEmployeeId()) &&
                employeeRepository.existsByEmployeeId(updated.getEmployeeId())) {
            throw new IllegalArgumentException("NIK karyawan sudah digunakan: " + updated.getEmployeeId());
        }
        validateNpwp(updated.getNpwp(), existing.getId());
    }

    private void validateNpwp(String npwp, UUID excludeId) {
        if (npwp == null || npwp.isBlank()) {
            return;
        }

        // Validate NPWP format: XX.XXX.XXX.X-XXX.XXX or 15-16 digits
        String cleanedNpwp = npwp.replaceAll("[.\\-]", "");
        if (cleanedNpwp.length() < 15 || cleanedNpwp.length() > 16) {
            throw new IllegalArgumentException("Format NPWP tidak valid: harus 15-16 digit");
        }
        if (!cleanedNpwp.matches("\\d+")) {
            throw new IllegalArgumentException("NPWP hanya boleh berisi angka");
        }

        // Check for duplicate NPWP
        if (excludeId != null) {
            employeeRepository.findByNpwpExcludingId(npwp, excludeId).ifPresent(e -> {
                throw new IllegalArgumentException("NPWP sudah digunakan oleh karyawan lain: " + e.getName());
            });
        } else if (employeeRepository.existsByNpwp(npwp)) {
            throw new IllegalArgumentException("NPWP sudah digunakan oleh karyawan lain");
        }
    }
}
