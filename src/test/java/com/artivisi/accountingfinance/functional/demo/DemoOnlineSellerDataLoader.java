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
 * Demo data loader for Online Seller industry (Toko Gadget Sejahtera).
 *
 * Features exercised:
 * - Marketplace sales (Tokopedia, Shopee, TikTok — DETAILED templates)
 * - Marketplace withdrawals
 * - Inventory purchases
 * - Payroll (4 employees, lower salary)
 * - PPh Final UMKM (0.5% of monthly revenue)
 * - Fixed assets (warehouse equipment) + depreciation
 * - Various marketplace expenses (ads, ongkir, packing)
 *
 * Usage:
 *   ./mvnw test -Dtest=DemoOnlineSellerDataLoader
 */
@Slf4j
@DisplayName("Demo: Online Seller Data Loader")
@DirtiesContext @Tag("demo") @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DemoOnlineSellerDataLoader extends DemoDataLoaderBase {

    @Override protected String industryName() { return "Online Seller"; }
    @Override protected String seedDataPath() { return "industry-seed/online-seller/seed-data"; }
    @Override protected String demoDataPath() { return "src/test/resources/demo-data/online-seller"; }
    @Override protected int jkkRiskClass() { return 1; }
    @Override protected long baseSalary() { return 5500000; }
    @Override protected boolean ppnEnabled() { return false; } // UMKM, non-PKP

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
        executeDemoTransactions("demo-data/online-seller/demo-transactions.csv");
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
