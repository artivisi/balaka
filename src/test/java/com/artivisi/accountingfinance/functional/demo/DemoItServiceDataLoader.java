package com.artivisi.accountingfinance.functional.demo;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.LocalDate;

/**
 * Demo data loader for IT Service industry (PT Solusi Digital Nusantara).
 *
 * Features exercised:
 * - Transaction templates (SIMPLE + DETAILED: PPN, PPh 23, BUMN FP03)
 * - Payroll (monthly create + calculate + approve + post)
 * - Fixed assets (purchase + monthly depreciation)
 * - Invoices (create, pay)
 * - Fiscal period close (monthly)
 * - Tax payments (PPh 21, PPh 23, PPh 4(2), PPN, PPh 25)
 * - Year-end closing journal
 * - Trial balance verification
 *
 * Usage:
 *   ./mvnw test -Dtest=DemoItServiceDataLoader
 *   ./mvnw test -Dtest=DemoItServiceDataLoader -Dplaywright.headless=false -Dplaywright.slowmo=100
 */
@Slf4j
@DisplayName("Demo: IT Service Data Loader")
@DirtiesContext @Tag("demo") @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DemoItServiceDataLoader extends DemoDataLoaderBase {

    @Override protected String industryName() { return "IT Service"; }
    @Override protected String seedDataPath() { return "industry-seed/it-service/seed-data"; }
    @Override protected String demoDataPath() { return "src/test/resources/demo-data/it-service"; }
    @Override protected int jkkRiskClass() { return 1; }
    @Override protected long baseSalary() { return 15000000; }

    @Test @Order(1)
    @DisplayName("1. Import seed data")
    void importSeed() throws Exception {
        importSeedData();
    }

    @Test @Order(2)
    @DisplayName("2. Import master data")
    void importMaster() throws Exception {
        importMasterData();
    }

    @Test @Order(3)
    @DisplayName("3. Create demo users")
    void createUsers() {
        createDemoUsers();
    }

    @Test @Order(4)
    @DisplayName("4. Execute demo transactions via UI")
    void executeTransactions() {
        executeDemoTransactions("demo-data/it-service/demo-transactions.csv");
    }

    @Test @Order(5)
    @DisplayName("5. Validate trial balance")
    void validateBalance() {
        validateTrialBalance(LocalDate.of(2025, 12, 31));
    }

    @Test @Order(6)
    @DisplayName("6. Validate dashboard")
    void validateDashboardLoads() {
        validateDashboard();
    }
}
