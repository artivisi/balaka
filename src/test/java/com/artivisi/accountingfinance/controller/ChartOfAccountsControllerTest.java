package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.SecurityAuditLog;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.SecurityAuditLogRepository;
import com.artivisi.accountingfinance.security.CspNonceFilter;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FAS-93: the chart-of-accounts form must audit the cash/bank flag on the edit
 * path as well as the create path. The service mutates the same managed entity
 * the controller loaded, so the previous value has to be captured before the
 * update call.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "admin", authorities = {"ACCOUNT_VIEW", "ACCOUNT_CREATE", "ACCOUNT_EDIT"})
@DisplayName("ChartOfAccountsController cash/bank audit")
class ChartOfAccountsControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ChartOfAccountService chartOfAccountService;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @Autowired
    private SecurityAuditLogRepository auditLogRepository;

    @Autowired
    private EntityManager entityManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .addFilters(webApplicationContext.getBean(CspNonceFilter.class))
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("create form with cash/bank checked writes one SETTINGS_CHANGE event")
    void createFormWithCashFlagWritesAuditEvent() throws Exception {
        String code = uniqueCode();

        mockMvc.perform(post("/accounts/new").with(csrf())
                        .param("accountCode", code)
                        .param("accountName", "Kas Uji FAS-93")
                        .param("accountType", "ASSET")
                        .param("normalBalance", "DEBIT")
                        .param("permanent", "true")
                        .param("isCashBank", "true"))
                .andExpect(status().is3xxRedirection());

        flushAndClear();
        ChartOfAccount created = chartOfAccountRepository.findByAccountCode(code).orElseThrow();
        assertThat(created.getIsCashBank()).isTrue();

        List<SecurityAuditLog> events = cashFlagEvents(code);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getDetails())
                .contains(code)
                .contains("Kas Uji FAS-93")
                .contains("marked as cash/bank");
    }

    @Test
    @DisplayName("edit form setting the cash/bank flag writes exactly one SETTINGS_CHANGE event")
    void editFormSettingCashFlagWritesAuditEvent() throws Exception {
        ChartOfAccount account = createAssetLeaf(false);

        mockMvc.perform(editRequest(account)
                        .param("isCashBank", "true"))
                .andExpect(status().is3xxRedirection());

        flushAndClear();
        assertThat(chartOfAccountRepository.findById(account.getId()).orElseThrow().getIsCashBank()).isTrue();

        List<SecurityAuditLog> events = cashFlagEvents(account.getAccountCode());
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getDetails())
                .contains("cash/bank flag set")
                .contains(account.getAccountCode())
                .contains(account.getAccountName())
                .doesNotContain("cleared");
    }

    @Test
    @DisplayName("edit form clearing the cash/bank flag writes exactly one SETTINGS_CHANGE event")
    void editFormClearingCashFlagWritesAuditEvent() throws Exception {
        ChartOfAccount account = createAssetLeaf(true);

        mockMvc.perform(editRequest(account))
                .andExpect(status().is3xxRedirection());

        flushAndClear();
        assertThat(chartOfAccountRepository.findById(account.getId()).orElseThrow().getIsCashBank()).isFalse();

        List<SecurityAuditLog> events = cashFlagEvents(account.getAccountCode());
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getDetails())
                .contains("cash/bank flag cleared")
                .contains(account.getAccountCode())
                .contains(account.getAccountName())
                .doesNotContain("flag set");
    }

    @Test
    @DisplayName("edit form that leaves the flag off writes no audit event")
    void editFormWithUnchangedOffFlagWritesNoAuditEvent() throws Exception {
        ChartOfAccount account = createAssetLeaf(false);

        mockMvc.perform(editRequest(account))
                .andExpect(status().is3xxRedirection());

        flushAndClear();
        assertThat(chartOfAccountRepository.findById(account.getId()).orElseThrow().getIsCashBank()).isFalse();
        assertThat(cashFlagEvents(account.getAccountCode())).isEmpty();
    }

    @Test
    @DisplayName("edit form that leaves the flag on writes no audit event")
    void editFormWithUnchangedOnFlagWritesNoAuditEvent() throws Exception {
        ChartOfAccount account = createAssetLeaf(true);

        mockMvc.perform(editRequest(account)
                        .param("isCashBank", "true"))
                .andExpect(status().is3xxRedirection());

        flushAndClear();
        assertThat(chartOfAccountRepository.findById(account.getId()).orElseThrow().getIsCashBank()).isTrue();
        assertThat(cashFlagEvents(account.getAccountCode())).isEmpty();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder editRequest(ChartOfAccount account) {
        return post("/accounts/{id}/edit", account.getId()).with(csrf())
                .param("accountCode", account.getAccountCode())
                .param("accountName", account.getAccountName())
                .param("accountType", account.getAccountType().name())
                .param("normalBalance", account.getNormalBalance().name())
                .param("permanent", "true")
                .param("active", "true");
    }

    private ChartOfAccount createAssetLeaf(boolean cashBank) {
        ChartOfAccount account = new ChartOfAccount();
        account.setAccountCode(uniqueCode());
        account.setAccountName("Akun Uji FAS-93");
        account.setAccountType(AccountType.ASSET);
        account.setNormalBalance(NormalBalance.DEBIT);
        account.setIsHeader(false);
        account.setActive(true);
        account.setPermanent(true);
        account.setIsCashBank(cashBank);
        return chartOfAccountService.create(account);
    }

    private List<SecurityAuditLog> cashFlagEvents(String accountCode) {
        return auditLogRepository.findAll().stream()
                .filter(event -> event.getEventType() == AuditEventType.SETTINGS_CHANGE)
                .filter(event -> event.getDetails() != null && event.getDetails().contains(accountCode))
                .toList();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private String uniqueCode() {
        return "T93." + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
