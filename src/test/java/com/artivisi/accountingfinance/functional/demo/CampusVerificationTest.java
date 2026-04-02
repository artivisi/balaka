package com.artivisi.accountingfinance.functional.demo;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

@Slf4j
@DisplayName("Verify: Campus")
@DirtiesContext @Tag("demo") @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CampusVerificationTest extends DemoVerificationTest {
    @Override protected String industryName() { return "Campus"; }
    @Override protected String seedDataPath() { return "industry-seed/campus/seed-data"; }
    @Override protected String demoDataPath() { return "src/test/resources/demo-data/campus"; }
    @Override protected long baseSalary() { return 8000000; }
    @Override protected boolean ppnEnabled() { return false; }
    @Override protected List<FiscalAdj> fiscalAdjustments() {
        return List.of(
            new FiscalAdj("Beban beasiswa (beda tetap)", "PERMANENT", "POSITIVE",
                    10000000, "", "Pasal 9 ayat 1 — beasiswa non-deductible portion")
        );
    }
}
