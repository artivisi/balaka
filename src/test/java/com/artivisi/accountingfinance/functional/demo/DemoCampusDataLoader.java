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
import java.util.List;

/**
 * Demo data loader for Campus industry (STMIK Merdeka Digital).
 *
 * Features exercised:
 * - Tuition billing (Tagihan SPP, Uang Pangkal, Praktikum)
 * - Tuition payments (Pembayaran SPP, etc.)
 * - Grants (Terima Hibah)
 * - Scholarships (Beasiswa Prestasi, Tidak Mampu)
 * - Faculty/staff payroll (8 employees)
 * - Campus expenses (listrik, air, internet, ATK, keamanan, kebersihan)
 * - Fixed assets (lab equipment) + depreciation
 *
 * Usage:
 *   ./mvnw test -Dtest=DemoCampusDataLoader
 */
@Slf4j
@DisplayName("Demo: Campus Data Loader")
@DirtiesContext @Tag("demo") @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DemoCampusDataLoader extends DemoDataLoaderBase {

    @Override protected String industryName() { return "Campus"; }
    @Override protected String seedDataPath() { return "industry-seed/campus/seed-data"; }
    @Override protected String demoDataPath() { return "src/test/resources/demo-data/campus"; }
    @Override protected int jkkRiskClass() { return 1; }
    @Override protected long baseSalary() { return 8000000; }
    @Override protected boolean ppnEnabled() { return false; }

    @Override
    protected List<FiscalAdj> fiscalAdjustments() {
        return List.of(
                new FiscalAdj("Beban beasiswa (beda tetap)", "PERMANENT", "POSITIVE",
                        10000000, "", "Pasal 9 ayat 1 — beasiswa non-deductible portion")
        );
    }

    @Test @Order(1) void importSeed() throws Exception { importSeedData(); }
    @Test @Order(2) void importMaster() throws Exception { importMasterData(); }
    @Test @Order(3) void createUsers() { createDemoUsers(); }
    @Test @Order(4) void executeTransactions() {
        executeDemoTransactions("demo-data/campus/demo-transactions.csv");
    }
    @Test @Order(5) void validateBalance() {
        validateTrialBalance(LocalDate.of(2025, 12, 31));
    }
    @Test @Order(6) void validateDashboardLoads() { validateDashboard(); }
}
