package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.enums.AccountType;
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
public interface ChartOfAccountRepository extends JpaRepository<ChartOfAccount, UUID> {

    Optional<ChartOfAccount> findByAccountCode(String accountCode);

    boolean existsByAccountCode(String accountCode);

    List<ChartOfAccount> findByActiveOrderByAccountCodeAsc(Boolean active);

    List<ChartOfAccount> findByParentIsNullOrderByAccountCodeAsc();

    List<ChartOfAccount> findByParentIdOrderByAccountCodeAsc(UUID parentId);

    List<ChartOfAccount> findByAccountTypeAndActiveOrderByAccountCodeAsc(AccountType accountType, Boolean active);

    List<ChartOfAccount> findByIsHeaderAndActiveOrderByAccountCodeAsc(Boolean isHeader, Boolean active);

    @Query("SELECT c FROM ChartOfAccount c WHERE c.active = true AND c.isHeader = false ORDER BY c.accountCode")
    List<ChartOfAccount> findAllTransactableAccounts();

    @Query("SELECT c FROM ChartOfAccount c WHERE c.active = :active AND " +
           "(LOWER(c.accountCode) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.accountName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<ChartOfAccount> searchAccounts(@Param("search") String search, @Param("active") Boolean active, Pageable pageable);

    @Query("SELECT c FROM ChartOfAccount c WHERE c.active = true AND c.isHeader = false AND " +
           "c.accountType IN :types ORDER BY c.accountCode")
    List<ChartOfAccount> findByAccountTypeIn(@Param("types") List<AccountType> types);
}
