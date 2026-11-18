package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.EmploymentStatus;
import com.artivisi.accountingfinance.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByEmployeeId(String employeeId);

    boolean existsByEmployeeId(String employeeId);

    List<Employee> findByActiveTrue();

    List<Employee> findByEmploymentStatus(EmploymentStatus status);

    List<Employee> findByActiveTrueAndEmploymentStatus(EmploymentStatus status);

    @Query("SELECT e FROM Employee e WHERE e.active = true ORDER BY e.name")
    List<Employee> findAllActive();

    @Query("SELECT e FROM Employee e WHERE " +
           "(LOWER(e.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(e.employeeId) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR e.employmentStatus = :status) " +
           "AND (:active IS NULL OR e.active = :active) " +
           "ORDER BY e.employeeId")
    Page<Employee> findByFiltersAndSearch(
            @Param("search") String search,
            @Param("status") EmploymentStatus status,
            @Param("active") Boolean active,
            Pageable pageable);

    @Query("SELECT e FROM Employee e WHERE " +
           "(:status IS NULL OR e.employmentStatus = :status) " +
           "AND (:active IS NULL OR e.active = :active) " +
           "ORDER BY e.employeeId")
    Page<Employee> findByFilters(
            @Param("status") EmploymentStatus status,
            @Param("active") Boolean active,
            Pageable pageable);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.active = true AND e.employmentStatus = 'ACTIVE'")
    long countActiveEmployees();


    Optional<Employee> findByUser(User user);

    Optional<Employee> findByUserId(UUID userId);
}
