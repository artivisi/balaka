package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.EmploymentStatus;
import com.artivisi.accountingfinance.entity.EmploymentType;
import com.artivisi.accountingfinance.entity.PtkpStatus;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for EmployeeService.
 * Tests actual database queries and business logic. Runs with an encryption key, as production does:
 * npwp and nik_ktp are stored encrypted, which is what the duplicate checks have to work against.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "app.encryption.key=Kea3OzaaPR89QOebS1b427C8xxRoztOft8rlSEL3W7k=")
@Transactional
@DisplayName("EmployeeService Integration Tests")
class EmployeeServiceTest {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Nested
    @DisplayName("Find Operations")
    class FindOperationsTests {

        @Test
        @DisplayName("findById should return employee with correct data")
        void findByIdShouldReturnEmployee() {
            Employee employee = createTestEmployee();

            Employee found = employeeService.findById(employee.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(employee.getId());
            assertThat(found.getName()).isEqualTo(employee.getName());
        }

        @Test
        @DisplayName("findById should throw EntityNotFoundException for invalid ID")
        void findByIdShouldThrowForInvalidId() {
            UUID invalidId = UUID.randomUUID();

            assertThatThrownBy(() -> employeeService.findById(invalidId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Karyawan tidak ditemukan");
        }

        @Test
        @DisplayName("findByEmployeeId should return correct employee")
        void findByEmployeeIdShouldReturnEmployee() {
            Employee employee = createTestEmployee();

            Employee found = employeeService.findByEmployeeId(employee.getEmployeeId());

            assertThat(found).isNotNull();
            assertThat(found.getEmployeeId()).isEqualTo(employee.getEmployeeId());
        }

        @Test
        @DisplayName("findByEmployeeId should throw for invalid NIK")
        void findByEmployeeIdShouldThrowForInvalidNik() {
            assertThatThrownBy(() -> employeeService.findByEmployeeId("INVALID-NIK"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Karyawan tidak ditemukan dengan NIK");
        }

        @Test
        @DisplayName("findAll should return paginated results")
        void findAllShouldReturnPaginatedResults() {
            createTestEmployee();
            createTestEmployee();

            Page<Employee> page = employeeService.findAll(PageRequest.of(0, 10));

            assertThat(page.getContent()).isNotEmpty();
            assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("findByFilters should filter by employment status")
        void findByFiltersShouldFilterByStatus() {
            createTestEmployeeWithStatus(EmploymentStatus.ACTIVE);
            createTestEmployeeWithStatus(EmploymentStatus.RESIGNED);

            Page<Employee> page = employeeService.findByFilters(
                null, EmploymentStatus.ACTIVE, null, PageRequest.of(0, 10));

            assertThat(page.getContent()).isNotEmpty().allMatch(e -> e.getEmploymentStatus() == EmploymentStatus.ACTIVE);
        }

        @Test
        @DisplayName("findByFilters should filter by active status")
        void findByFiltersShouldFilterByActiveStatus() {
            createTestEmployee(); // Create active employee for test
            Employee inactive = createTestEmployee();
            employeeService.deactivate(inactive.getId());

            Page<Employee> activePage = employeeService.findByFilters(
                null, null, true, PageRequest.of(0, 10));

            assertThat(activePage.getContent()).isNotEmpty().allMatch(Employee::isActive);
        }

        @Test
        @DisplayName("findByFilters should search by name")
        void findByFiltersShouldSearchByName() {
            createTestEmployeeWithName("John Unique Smith");

            Page<Employee> page = employeeService.findByFilters(
                "Unique", null, null, PageRequest.of(0, 10));

            assertThat(page.getContent()).isNotEmpty();
            assertThat(page.getContent()).anyMatch(e -> e.getName().contains("Unique"));
        }

        @Test
        @DisplayName("findByFilters should return all when search is blank")
        void findByFiltersShouldReturnAllWhenSearchIsBlank() {
            createTestEmployee();

            Page<Employee> page = employeeService.findByFilters(
                "", null, null, PageRequest.of(0, 10));

            assertThat(page.getContent()).isNotEmpty();
        }

        @Test
        @DisplayName("findByFilters should search with status and active combined")
        void findByFiltersShouldSearchWithStatusAndActiveCombined() {
            createTestEmployeeWithName("CombinedFilter Employee");

            Page<Employee> page = employeeService.findByFilters(
                "CombinedFilter", EmploymentStatus.ACTIVE, true, PageRequest.of(0, 10));

            assertThat(page.getContent()).isNotEmpty();
            assertThat(page.getContent()).anyMatch(e -> e.getName().contains("CombinedFilter"));
        }

        @Test
        @DisplayName("findByFilters should filter without search using status only")
        void findByFiltersShouldFilterWithoutSearchUsingStatusOnly() {
            createTestEmployeeWithStatus(EmploymentStatus.ACTIVE);

            Page<Employee> page = employeeService.findByFilters(
                null, EmploymentStatus.ACTIVE, null, PageRequest.of(0, 10));

            assertThat(page.getContent()).isNotEmpty();
            assertThat(page.getContent()).allMatch(e -> e.getEmploymentStatus() == EmploymentStatus.ACTIVE);
        }

        @Test
        @DisplayName("findByFilters should filter without search using active only")
        void findByFiltersShouldFilterWithoutSearchUsingActiveOnly() {
            createTestEmployee();

            Page<Employee> page = employeeService.findByFilters(
                null, null, true, PageRequest.of(0, 10));

            assertThat(page.getContent()).isNotEmpty();
            assertThat(page.getContent()).allMatch(Employee::isActive);
        }

        @Test
        @DisplayName("findActiveEmployees should return only active employees")
        void findActiveEmployeesShouldReturnOnlyActive() {
            Employee active = createTestEmployee();
            Employee inactive = createTestEmployee();
            employeeService.deactivate(inactive.getId());

            List<Employee> activeEmployees = employeeService.findActiveEmployees();

            assertThat(activeEmployees)
                    .isNotEmpty()
                    .allMatch(Employee::isActive)
                    .anyMatch(e -> e.getId().equals(active.getId()))
                    .noneMatch(e -> e.getId().equals(inactive.getId()));
        }

        @Test
        @DisplayName("findByEmploymentStatus should return employees with matching status")
        void findByEmploymentStatusShouldReturnMatchingEmployees() {
            createTestEmployeeWithStatus(EmploymentStatus.ACTIVE);
            createTestEmployeeWithStatus(EmploymentStatus.RESIGNED);
            createTestEmployeeWithStatus(EmploymentStatus.TERMINATED);

            List<Employee> activeEmployees = employeeService.findByEmploymentStatus(EmploymentStatus.ACTIVE);

            assertThat(activeEmployees).isNotEmpty().allMatch(e -> e.getEmploymentStatus() == EmploymentStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("Create Employee")
    class CreateEmployeeTests {

        @Test
        @DisplayName("create should save employee with active status")
        void createShouldSaveWithActiveStatus() {
            Employee employee = buildTestEmployee();

            Employee saved = employeeService.create(employee);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.isActive()).isTrue();
        }

        @Test
        @DisplayName("create should persist all fields correctly")
        void createShouldPersistAllFields() {
            Employee employee = buildTestEmployee();
            employee.setEmail("test@example.com");
            employee.setPhone("081234567890");
            employee.setAddress("Jl. Test No. 123");
            employee.setNpwp("12.345.678.9-012.345");
            employee.setNikKtp("3201234567890001");
            employee.setPtkpStatus(PtkpStatus.K_1);
            employee.setJobTitle("Software Engineer");
            employee.setDepartment("IT");
            employee.setBankName("Bank Mandiri");
            employee.setBankAccountNumber("1234567890");
            employee.setBankAccountName("Test Employee");

            Employee saved = employeeService.create(employee);

            Employee retrieved = employeeRepository.findById(saved.getId()).orElseThrow();
            assertThat(retrieved.getEmail()).isEqualTo("test@example.com");
            assertThat(retrieved.getPhone()).isEqualTo("081234567890");
            assertThat(retrieved.getPtkpStatus()).isEqualTo(PtkpStatus.K_1);
            assertThat(retrieved.getJobTitle()).isEqualTo("Software Engineer");
            assertThat(retrieved.getBankName()).isEqualTo("Bank Mandiri");
        }

        @Test
        @DisplayName("create should throw for duplicate employee ID")
        void createShouldThrowForDuplicateEmployeeId() {
            Employee first = createTestEmployee();

            Employee second = buildTestEmployee();
            second.setEmployeeId(first.getEmployeeId());

            assertThatThrownBy(() -> employeeService.create(second))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NIK karyawan sudah digunakan");
        }

        @Test
        @DisplayName("create should throw for duplicate NPWP")
        void createShouldThrowForDuplicateNpwp() {
            Employee first = buildTestEmployee();
            first.setNpwp("12.345.678.9-012.345");
            employeeService.create(first);

            Employee second = buildTestEmployee();
            second.setNpwp("12.345.678.9-012.345");

            assertThatThrownBy(() -> employeeService.create(second))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NPWP sudah digunakan");
        }

        @Test
        @DisplayName("create should throw for a duplicate NPWP stored encrypted and written in another format")
        void createShouldThrowForDuplicateNpwpInAnotherFormat() {
            Employee first = buildTestEmployee();
            first.setNpwp("33.333.333.3-333.333");
            Employee saved = employeeService.create(first);
            employeeRepository.flush();

            String stored = jdbcTemplate.queryForObject(
                    "SELECT npwp FROM employees WHERE id = ?", String.class, saved.getId());
            assertThat(stored).startsWith("ENC:").doesNotContain("333333");

            Employee second = buildTestEmployee();
            second.setNpwp("333333333333333");

            assertThatThrownBy(() -> employeeService.create(second))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NPWP sudah digunakan")
                .hasMessageContaining(first.getName());
        }

        @Test
        @DisplayName("create should throw for invalid NPWP format")
        void createShouldThrowForInvalidNpwpFormat() {
            Employee employee = buildTestEmployee();
            employee.setNpwp("123"); // Too short

            assertThatThrownBy(() -> employeeService.create(employee))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Format NPWP tidak valid");
        }

        @Test
        @DisplayName("create should throw for NPWP with non-numeric characters")
        void createShouldThrowForNpwpWithNonNumeric() {
            Employee employee = buildTestEmployee();
            employee.setNpwp("12.345.ABC.9-012.345");

            assertThatThrownBy(() -> employeeService.create(employee))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NPWP hanya boleh berisi angka");
        }

        @Test
        @DisplayName("create should allow null NPWP")
        void createShouldAllowNullNpwp() {
            Employee employee = buildTestEmployee();
            employee.setNpwp(null);

            Employee saved = employeeService.create(employee);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getNpwp()).isNull();
        }

        @Test
        @DisplayName("create should allow blank NPWP")
        void createShouldAllowBlankNpwp() {
            Employee employee = buildTestEmployee();
            employee.setNpwp("");

            Employee saved = employeeService.create(employee);

            assertThat(saved.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Update Employee")
    class UpdateEmployeeTests {

        @Test
        @DisplayName("update should modify employee fields")
        void updateShouldModifyFields() {
            Employee employee = createTestEmployee();

            Employee updateData = buildUpdateData(employee);
            updateData.setName("Updated Name");
            updateData.setEmail("updated@example.com");
            updateData.setJobTitle("Senior Developer");

            Employee updated = employeeService.update(employee.getId(), updateData);

            assertThat(updated.getName()).isEqualTo("Updated Name");
            assertThat(updated.getEmail()).isEqualTo("updated@example.com");
            assertThat(updated.getJobTitle()).isEqualTo("Senior Developer");
        }

        @Test
        @DisplayName("update should allow changing employee ID if not duplicate")
        void updateShouldAllowChangingEmployeeId() {
            Employee employee = createTestEmployee();
            String newEmployeeId = "NEW-" + System.currentTimeMillis();

            Employee updateData = buildUpdateData(employee);
            updateData.setEmployeeId(newEmployeeId);

            Employee updated = employeeService.update(employee.getId(), updateData);

            assertThat(updated.getEmployeeId()).isEqualTo(newEmployeeId);
        }

        @Test
        @DisplayName("update should throw for duplicate employee ID")
        void updateShouldThrowForDuplicateEmployeeId() {
            Employee first = createTestEmployee();
            Employee second = createTestEmployee();

            Employee updateData = buildUpdateData(second);
            updateData.setEmployeeId(first.getEmployeeId());

            assertThatThrownBy(() -> employeeService.update(second.getId(), updateData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NIK karyawan sudah digunakan");
        }

        @Test
        @DisplayName("update should throw for duplicate NPWP")
        void updateShouldThrowForDuplicateNpwp() {
            Employee first = buildTestEmployee();
            first.setNpwp("11.111.111.1-111.111");
            employeeService.create(first);

            Employee second = createTestEmployee();

            Employee updateData = buildUpdateData(second);
            updateData.setNpwp("11.111.111.1-111.111");

            assertThatThrownBy(() -> employeeService.update(second.getId(), updateData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NPWP sudah digunakan");
        }

        @Test
        @DisplayName("update should allow keeping same NPWP")
        void updateShouldAllowKeepingSameNpwp() {
            Employee employee = buildTestEmployee();
            employee.setNpwp("22.222.222.2-222.222");
            employee = employeeService.create(employee);

            Employee updateData = buildUpdateData(employee);
            updateData.setNpwp("22.222.222.2-222.222");
            updateData.setName("Updated Name");

            Employee updated = employeeService.update(employee.getId(), updateData);

            assertThat(updated.getName()).isEqualTo("Updated Name");
            assertThat(updated.getNpwp()).isEqualTo("22.222.222.2-222.222");
        }

        @Test
        @DisplayName("update should throw for invalid NPWP format")
        void updateShouldThrowForInvalidNpwpFormat() {
            Employee employee = createTestEmployee();

            Employee updateData = buildUpdateData(employee);
            updateData.setNpwp("123"); // Too short

            assertThatThrownBy(() -> employeeService.update(employee.getId(), updateData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Format NPWP tidak valid");
        }

        @Test
        @DisplayName("update should throw for NPWP with non-numeric characters")
        void updateShouldThrowForNpwpWithNonNumericOnUpdate() {
            Employee employee = createTestEmployee();

            Employee updateData = buildUpdateData(employee);
            updateData.setNpwp("12.345.ABC.9-012.345");

            assertThatThrownBy(() -> employeeService.update(employee.getId(), updateData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NPWP hanya boleh berisi angka");
        }

        @Test
        @DisplayName("update should allow null NPWP")
        void updateShouldAllowNullNpwp() {
            Employee employee = createTestEmployee();

            Employee updateData = buildUpdateData(employee);
            updateData.setNpwp(null);
            updateData.setName("Updated Without NPWP");

            Employee updated = employeeService.update(employee.getId(), updateData);

            assertThat(updated.getName()).isEqualTo("Updated Without NPWP");
        }

        @Test
        @DisplayName("update should allow blank NPWP")
        void updateShouldAllowBlankNpwp() {
            Employee employee = createTestEmployee();

            Employee updateData = buildUpdateData(employee);
            updateData.setNpwp("");
            updateData.setName("Updated With Blank NPWP");

            Employee updated = employeeService.update(employee.getId(), updateData);

            assertThat(updated.getName()).isEqualTo("Updated With Blank NPWP");
        }

        @Test
        @DisplayName("update should update PTKP status")
        void updateShouldUpdatePtkpStatus() {
            Employee employee = createTestEmployee();

            Employee updateData = buildUpdateData(employee);
            updateData.setPtkpStatus(PtkpStatus.K_2);

            Employee updated = employeeService.update(employee.getId(), updateData);

            assertThat(updated.getPtkpStatus()).isEqualTo(PtkpStatus.K_2);
        }

        @Test
        @DisplayName("update should update employment status")
        void updateShouldUpdateEmploymentStatus() {
            Employee employee = createTestEmployeeWithStatus(EmploymentStatus.ACTIVE);

            Employee updateData = buildUpdateData(employee);
            updateData.setEmploymentStatus(EmploymentStatus.RESIGNED);

            Employee updated = employeeService.update(employee.getId(), updateData);

            assertThat(updated.getEmploymentStatus()).isEqualTo(EmploymentStatus.RESIGNED);
        }

        @Test
        @DisplayName("update should update resign date")
        void updateShouldUpdateResignDate() {
            Employee employee = createTestEmployee();
            LocalDate resignDate = LocalDate.now();

            Employee updateData = buildUpdateData(employee);
            updateData.setResignDate(resignDate);

            Employee updated = employeeService.update(employee.getId(), updateData);

            assertThat(updated.getResignDate()).isEqualTo(resignDate);
        }
    }

    @Nested
    @DisplayName("Activate/Deactivate Employee")
    class ActivateDeactivateTests {

        @Test
        @DisplayName("deactivate should set active to false")
        void deactivateShouldSetActiveFalse() {
            Employee employee = createTestEmployee();
            assertThat(employee.isActive()).isTrue();

            employeeService.deactivate(employee.getId());

            Employee deactivated = employeeRepository.findById(employee.getId()).orElseThrow();
            assertThat(deactivated.isActive()).isFalse();
        }

        @Test
        @DisplayName("activate should set active to true")
        void activateShouldSetActiveTrue() {
            Employee employee = createTestEmployee();
            employeeService.deactivate(employee.getId());

            employeeService.activate(employee.getId());

            Employee activated = employeeRepository.findById(employee.getId()).orElseThrow();
            assertThat(activated.isActive()).isTrue();
        }

        @Test
        @DisplayName("deactivate should throw for non-existent employee")
        void deactivateShouldThrowForNonExistent() {
            UUID invalidId = UUID.randomUUID();

            assertThatThrownBy(() -> employeeService.deactivate(invalidId))
                .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("activate should throw for non-existent employee")
        void activateShouldThrowForNonExistent() {
            UUID invalidId = UUID.randomUUID();

            assertThatThrownBy(() -> employeeService.activate(invalidId))
                .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Count Operations")
    class CountOperationsTests {

        @Test
        @DisplayName("countActiveEmployees should return correct count")
        void countActiveEmployeesShouldReturnCorrectCount() {
            createTestEmployee(); // Create 2 active employees
            createTestEmployee();
            Employee inactive = createTestEmployee();
            employeeService.deactivate(inactive.getId());

            long count = employeeService.countActiveEmployees();

            // At least 2 from our test (may be more from other test data)
            assertThat(count).isGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("BPJS Information")
    class BpjsInformationTests {

        @Test
        @DisplayName("should save and retrieve BPJS numbers")
        void shouldSaveAndRetrieveBpjsNumbers() {
            Employee employee = buildTestEmployee();
            employee.setBpjsKesehatanNumber("0001234567890");
            employee.setBpjsKetenagakerjaanNumber("JKT1234567890");

            Employee saved = employeeService.create(employee);

            Employee retrieved = employeeService.findById(saved.getId());
            assertThat(retrieved.getBpjsKesehatanNumber()).isEqualTo("0001234567890");
            assertThat(retrieved.getBpjsKetenagakerjaanNumber()).isEqualTo("JKT1234567890");
        }
    }

    @Nested
    @DisplayName("Bank Information")
    class BankInformationTests {

        @Test
        @DisplayName("should save and retrieve bank details")
        void shouldSaveAndRetrieveBankDetails() {
            Employee employee = buildTestEmployee();
            employee.setBankName("Bank Central Asia");
            employee.setBankAccountNumber("1234567890");
            employee.setBankAccountName("Test Account Name");

            Employee saved = employeeService.create(employee);

            Employee retrieved = employeeService.findById(saved.getId());
            assertThat(retrieved.getBankName()).isEqualTo("Bank Central Asia");
            assertThat(retrieved.getBankAccountNumber()).isEqualTo("1234567890");
            assertThat(retrieved.getBankAccountName()).isEqualTo("Test Account Name");
        }
    }

    // Helper methods

    private Employee createTestEmployee() {
        Employee employee = buildTestEmployee();
        return employeeService.create(employee);
    }

    private Employee createTestEmployeeWithName(String name) {
        Employee employee = buildTestEmployee();
        employee.setName(name);
        return employeeService.create(employee);
    }

    private Employee createTestEmployeeWithStatus(EmploymentStatus status) {
        Employee employee = buildTestEmployee();
        employee.setEmploymentStatus(status);
        return employeeService.create(employee);
    }

    private Employee buildTestEmployee() {
        Employee employee = new Employee();
        // Max 20 chars for employeeId
        String uniqueId = String.valueOf(System.nanoTime() % 100000000);
        employee.setEmployeeId("EMP" + uniqueId);
        employee.setName("Test Employee " + System.currentTimeMillis());
        employee.setHireDate(LocalDate.now().minusYears(1));
        employee.setEmploymentType(EmploymentType.PERMANENT);
        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        employee.setPtkpStatus(PtkpStatus.TK_0);
        return employee;
    }

    private Employee buildUpdateData(Employee existing) {
        Employee updateData = new Employee();
        updateData.setEmployeeId(existing.getEmployeeId());
        updateData.setName(existing.getName());
        updateData.setEmail(existing.getEmail());
        updateData.setPhone(existing.getPhone());
        updateData.setAddress(existing.getAddress());
        updateData.setNpwp(existing.getNpwp());
        updateData.setNikKtp(existing.getNikKtp());
        updateData.setPtkpStatus(existing.getPtkpStatus());
        updateData.setHireDate(existing.getHireDate());
        updateData.setResignDate(existing.getResignDate());
        updateData.setEmploymentType(existing.getEmploymentType());
        updateData.setEmploymentStatus(existing.getEmploymentStatus());
        updateData.setJobTitle(existing.getJobTitle());
        updateData.setDepartment(existing.getDepartment());
        updateData.setBankName(existing.getBankName());
        updateData.setBankAccountNumber(existing.getBankAccountNumber());
        updateData.setBankAccountName(existing.getBankAccountName());
        updateData.setBpjsKesehatanNumber(existing.getBpjsKesehatanNumber());
        updateData.setBpjsKetenagakerjaanNumber(existing.getBpjsKetenagakerjaanNumber());
        updateData.setNotes(existing.getNotes());
        return updateData;
    }
}
