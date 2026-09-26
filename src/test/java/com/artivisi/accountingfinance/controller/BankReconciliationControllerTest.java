package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.security.CspNonceFilter;
import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.BankStatement;
import com.artivisi.accountingfinance.entity.BankStatementItem;
import com.artivisi.accountingfinance.entity.BankStatementParserConfig;
import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.enums.BankStatementParserType;
import com.artivisi.accountingfinance.repository.BankStatementItemRepository;
import com.artivisi.accountingfinance.service.BankReconciliationService;
import com.artivisi.accountingfinance.service.BankStatementImportService;
import com.artivisi.accountingfinance.service.BankStatementParserConfigService;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import com.artivisi.accountingfinance.service.CompanyBankAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Integration tests for BankReconciliationController.
 * Covers parser configs CRUD, statement views, reconciliation operations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "admin", authorities = {
        "BANK_RECONCILIATION_VIEW", "BANK_RECONCILIATION_IMPORT",
        "BANK_RECONCILIATION_MATCH", "BANK_RECONCILIATION_COMPLETE",
        "BANK_RECONCILIATION_CONFIG", "SETTINGS_EDIT"})
@DisplayName("BankReconciliationController Integration Tests")
class BankReconciliationControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private BankStatementParserConfigService parserConfigService;

    @Autowired
    private CompanyBankAccountService bankAccountService;

    @Autowired
    private ChartOfAccountService chartOfAccountService;

    @Autowired
    private BankReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                // Servlet container registers CspNonceFilter ahead of Spring Security (WebMvcConfig);
                // MockMvc only adds filters it is given.
                .addFilters(webApplicationContext.getBean(CspNonceFilter.class))
                .apply(springSecurity())
                .build();
    }

    @Nested
    @DisplayName("Landing Page")
    class LandingPageTests {

        @Test
        @DisplayName("Should render bank reconciliation index page")
        void shouldRenderBankReconciliationIndexPage() throws Exception {
            mockMvc.perform(get("/bank-reconciliation"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("bank-reconciliation/index"))
                    .andExpect(model().attributeExists("recentStatements", "recentReconciliations"));
        }
    }

    @Nested
    @DisplayName("Parser Config CRUD")
    class ParserConfigTests {

        @Test
        @DisplayName("Should list parser configs")
        void shouldListParserConfigs() throws Exception {
            mockMvc.perform(get("/bank-reconciliation/parser-configs"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("bank-reconciliation/parser-configs/list"))
                    .andExpect(model().attributeExists("configs"));
        }

        @Test
        @DisplayName("Should show new parser config form")
        void shouldShowNewParserConfigForm() throws Exception {
            mockMvc.perform(get("/bank-reconciliation/parser-configs/new"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("bank-reconciliation/parser-configs/form"))
                    .andExpect(model().attributeExists("config", "bankTypes"))
                    .andExpect(model().attribute("isEdit", false));
        }

        @Test
        @DisplayName("Should create parser config")
        void shouldCreateParserConfig() throws Exception {
            mockMvc.perform(post("/bank-reconciliation/parser-configs/new").with(csrf())
                            .param("bankType", "BCA")
                            .param("configName", "Test BCA Config")
                            .param("dateColumn", "0")
                            .param("descriptionColumn", "1")
                            .param("debitColumn", "2")
                            .param("creditColumn", "3")
                            .param("dateFormat", "dd/MM/yyyy")
                            .param("delimiter", ",")
                            .param("skipHeaderRows", "1")
                            .param("encoding", "UTF-8")
                            .param("decimalSeparator", ".")
                            .param("thousandSeparator", ","))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("Should reject parser config with missing required fields")
        void shouldRejectParserConfigWithMissingFields() throws Exception {
            mockMvc.perform(post("/bank-reconciliation/parser-configs/new").with(csrf())
                            .param("configName", "")
                            .param("dateColumn", "0"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("bank-reconciliation/parser-configs/form"));
        }

        @Test
        @DisplayName("Should show edit parser config form")
        void shouldShowEditParserConfigForm() throws Exception {
            // First create a config
            BankStatementParserConfig config = new BankStatementParserConfig();
            config.setBankType(BankStatementParserType.BCA);
            config.setConfigName("Edit Test Config");
            config.setDateColumn(0);
            config.setDescriptionColumn(1);
            config.setDebitColumn(2);
            config.setCreditColumn(3);
            config.setDateFormat("dd/MM/yyyy");
            config.setDelimiter(",");
            config.setSkipHeaderRows(1);
            config.setEncoding("UTF-8");
            config.setDecimalSeparator(".");
            config.setThousandSeparator(",");
            BankStatementParserConfig saved = parserConfigService.create(config);

            mockMvc.perform(get("/bank-reconciliation/parser-configs/" + saved.getId() + "/edit"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("bank-reconciliation/parser-configs/form"))
                    .andExpect(model().attribute("isEdit", true));
        }

        @Test
        @DisplayName("Should update parser config")
        void shouldUpdateParserConfig() throws Exception {
            // Create a config first
            BankStatementParserConfig config = new BankStatementParserConfig();
            config.setBankType(BankStatementParserType.MANDIRI);
            config.setConfigName("Update Test Config");
            config.setDateColumn(0);
            config.setDescriptionColumn(1);
            config.setDebitColumn(2);
            config.setCreditColumn(3);
            config.setDateFormat("dd/MM/yyyy");
            config.setDelimiter(",");
            config.setSkipHeaderRows(1);
            config.setEncoding("UTF-8");
            config.setDecimalSeparator(".");
            config.setThousandSeparator(",");
            BankStatementParserConfig saved = parserConfigService.create(config);

            mockMvc.perform(post("/bank-reconciliation/parser-configs/" + saved.getId()).with(csrf())
                            .param("bankType", "MANDIRI")
                            .param("configName", "Updated Config Name")
                            .param("dateColumn", "0")
                            .param("descriptionColumn", "1")
                            .param("debitColumn", "3")
                            .param("creditColumn", "4")
                            .param("dateFormat", "yyyy-MM-dd")
                            .param("delimiter", ";")
                            .param("skipHeaderRows", "2")
                            .param("encoding", "UTF-8")
                            .param("decimalSeparator", ",")
                            .param("thousandSeparator", "."))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("Should reject update with validation errors")
        void shouldRejectUpdateWithValidationErrors() throws Exception {
            BankStatementParserConfig config = new BankStatementParserConfig();
            config.setBankType(BankStatementParserType.BCA);
            config.setConfigName("Validation Test");
            config.setDateColumn(0);
            config.setDescriptionColumn(1);
            config.setDebitColumn(2);
            config.setCreditColumn(3);
            config.setDateFormat("dd/MM/yyyy");
            config.setDelimiter(",");
            config.setSkipHeaderRows(1);
            config.setEncoding("UTF-8");
            config.setDecimalSeparator(".");
            config.setThousandSeparator(",");
            BankStatementParserConfig saved = parserConfigService.create(config);

            mockMvc.perform(post("/bank-reconciliation/parser-configs/" + saved.getId()).with(csrf())
                            .param("configName", "")) // blank = validation error
                    .andExpect(status().isOk())
                    .andExpect(view().name("bank-reconciliation/parser-configs/form"));
        }

        @Test
        @DisplayName("Should deactivate parser config")
        void shouldDeactivateParserConfig() throws Exception {
            BankStatementParserConfig config = new BankStatementParserConfig();
            config.setBankType(BankStatementParserType.BCA);
            config.setConfigName("Deactivate Test");
            config.setDateColumn(0);
            config.setDescriptionColumn(1);
            config.setDateFormat("dd/MM/yyyy");
            config.setDelimiter(",");
            config.setSkipHeaderRows(1);
            config.setEncoding("UTF-8");
            config.setDecimalSeparator(".");
            config.setThousandSeparator(",");
            BankStatementParserConfig saved = parserConfigService.create(config);

            mockMvc.perform(post("/bank-reconciliation/parser-configs/" + saved.getId() + "/deactivate").with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("Should activate parser config")
        void shouldActivateParserConfig() throws Exception {
            BankStatementParserConfig config = new BankStatementParserConfig();
            config.setBankType(BankStatementParserType.BCA);
            config.setConfigName("Activate Test");
            config.setDateColumn(0);
            config.setDescriptionColumn(1);
            config.setDateFormat("dd/MM/yyyy");
            config.setDelimiter(",");
            config.setSkipHeaderRows(1);
            config.setEncoding("UTF-8");
            config.setDecimalSeparator(".");
            config.setThousandSeparator(",");
            BankStatementParserConfig saved = parserConfigService.create(config);
            parserConfigService.deactivate(saved.getId());

            mockMvc.perform(post("/bank-reconciliation/parser-configs/" + saved.getId() + "/activate").with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("Should delete parser config")
        void shouldDeleteParserConfig() throws Exception {
            BankStatementParserConfig config = new BankStatementParserConfig();
            config.setBankType(BankStatementParserType.BCA);
            config.setConfigName("Delete Test");
            config.setDateColumn(0);
            config.setDescriptionColumn(1);
            config.setDateFormat("dd/MM/yyyy");
            config.setDelimiter(",");
            config.setSkipHeaderRows(1);
            config.setEncoding("UTF-8");
            config.setDecimalSeparator(".");
            config.setThousandSeparator(",");
            BankStatementParserConfig saved = parserConfigService.create(config);

            mockMvc.perform(post("/bank-reconciliation/parser-configs/" + saved.getId() + "/delete").with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }
    }

    @Nested
    @DisplayName("Statement Pages")
    class StatementTests {

        @Test
        @DisplayName("Should render import form")
        void shouldRenderImportForm() throws Exception {
            mockMvc.perform(get("/bank-reconciliation/import"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("bank-reconciliation/import"))
                    .andExpect(model().attributeExists("bankAccounts", "parserConfigs"));
        }

        @Test
        @DisplayName("Should list statements")
        void shouldListStatements() throws Exception {
            mockMvc.perform(get("/bank-reconciliation/statements"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("bank-reconciliation/statements/list"))
                    .andExpect(model().attributeExists("statements"));
        }
    }

    @Nested
    @DisplayName("Reconciliation Pages")
    class ReconciliationTests {

        @Test
        @DisplayName("Should list reconciliations")
        void shouldListReconciliations() throws Exception {
            mockMvc.perform(get("/bank-reconciliation/reconciliations"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("bank-reconciliation/reconciliations/list"))
                    .andExpect(model().attributeExists("reconciliations"));
        }

        @Test
        @DisplayName("Should show new reconciliation form")
        void shouldShowNewReconciliationForm() throws Exception {
            mockMvc.perform(get("/bank-reconciliation/reconciliations/new"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("bank-reconciliation/reconciliations/form"))
                    .andExpect(model().attributeExists("bankAccounts", "statements"));
        }
    }
}
