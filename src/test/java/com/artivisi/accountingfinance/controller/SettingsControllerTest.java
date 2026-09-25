package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.service.CompanyConfigService;
import com.artivisi.accountingfinance.service.CompanyBankAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Integration tests for SettingsController.
 * Covers company settings, bank accounts, device tokens, about, audit logs,
 * and company logo upload.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "admin", authorities = {
        "SETTINGS_VIEW", "SETTINGS_EDIT", "AUDIT_LOG_VIEW",
        "TELEGRAM_MANAGE"})
@DisplayName("SettingsController Integration Tests")
class SettingsControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private CompanyConfigService companyConfigService;

    @Autowired
    private CompanyBankAccountService bankAccountService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Nested
    @DisplayName("Company Settings")
    class CompanySettingsTests {

        @Test
        @DisplayName("Should render company settings page")
        void shouldRenderCompanySettingsPage() throws Exception {
            mockMvc.perform(get("/settings"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("settings/company"))
                    .andExpect(model().attributeExists("config", "bankAccounts"));
        }

        @Test
        @DisplayName("Should update company settings")
        void shouldUpdateCompanySettings() throws Exception {
            CompanyConfig config = companyConfigService.getConfig();

            mockMvc.perform(post("/settings/company").with(csrf())
                            .param("id", config.getId().toString())
                            .param("companyName", "PT Test Updated")
                            .param("companyAddress", "Jl. Test No. 1")
                            .param("companyPhone", "021-1234567")
                            .param("companyEmail", "test@test.com")
                            .param("fiscalYearStartMonth", "1")
                            .param("currencyCode", "IDR"))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("Should reject invalid company settings")
        void shouldRejectInvalidCompanySettings() throws Exception {
            mockMvc.perform(post("/settings/company").with(csrf())
                            .param("companyName", "")) // blank name = validation error
                    .andExpect(status().isOk())
                    .andExpect(view().name("settings/company"));
        }
    }

    @Nested
    @DisplayName("Company Logo")
    class CompanyLogoTests {

        @Test
        @DisplayName("Should reject empty logo file")
        void shouldRejectEmptyLogoFile() throws Exception {
            MockMultipartFile emptyFile = new MockMultipartFile(
                    "logoFile", "", "image/png", new byte[0]);

            mockMvc.perform(multipart("/settings/company/logo").file(emptyFile).with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("Should reject unsupported logo file type")
        void shouldRejectUnsupportedLogoFileType() throws Exception {
            MockMultipartFile invalidFile = new MockMultipartFile(
                    "logoFile", "test.bmp", "image/bmp", new byte[]{1, 2, 3});

            mockMvc.perform(multipart("/settings/company/logo").file(invalidFile).with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("Should reject logo file exceeding max size")
        void shouldRejectLogoFileExceedingMaxSize() throws Exception {
            byte[] largeFile = new byte[3 * 1024 * 1024]; // 3MB
            MockMultipartFile oversizedFile = new MockMultipartFile(
                    "logoFile", "large.png", "image/png", largeFile);

            mockMvc.perform(multipart("/settings/company/logo").file(oversizedFile).with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("Should return not found when no logo exists")
        void shouldReturnNotFoundWhenNoLogoExists() throws Exception {
            // Ensure no logo path is set
            CompanyConfig config = companyConfigService.getConfig();
            config.setCompanyLogoPath(null);
            companyConfigService.save(config);

            mockMvc.perform(get("/settings/company/logo"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should delete company logo")
        void shouldDeleteCompanyLogo() throws Exception {
            mockMvc.perform(post("/settings/company/logo/delete").with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }
    }

    @Nested
    @DisplayName("Bank Account Management")
    class BankAccountTests {

        @Test
        @DisplayName("Should list bank accounts")
        void shouldListBankAccounts() throws Exception {
            mockMvc.perform(get("/settings/bank-accounts"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("settings/bank-accounts"))
                    .andExpect(model().attributeExists("bankAccounts"));
        }

        @Test
        @DisplayName("Should return fragment for HTMX request")
        void shouldReturnFragmentForHtmxRequest() throws Exception {
            mockMvc.perform(get("/settings/bank-accounts")
                            .header("HX-Request", "true"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("settings/fragments/bank-table :: table"));
        }

        @Test
        @DisplayName("Should show new bank account form")
        void shouldShowNewBankAccountForm() throws Exception {
            mockMvc.perform(get("/settings/bank-accounts/new"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("settings/bank-form"))
                    .andExpect(model().attributeExists("bankAccount", "glAccounts"));
        }

        @Test
        @DisplayName("Should create bank account")
        void shouldCreateBankAccount() throws Exception {
            mockMvc.perform(post("/settings/bank-accounts/new").with(csrf())
                            .param("bankName", "BCA")
                            .param("accountNumber", "9999888877")
                            .param("accountName", "PT Test")
                            .param("currencyCode", "IDR"))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("Should reject bank account with missing required fields")
        void shouldRejectBankAccountWithMissingFields() throws Exception {
            mockMvc.perform(post("/settings/bank-accounts/new").with(csrf())
                            .param("bankName", "")
                            .param("accountNumber", "")
                            .param("accountName", ""))
                    .andExpect(status().isOk())
                    .andExpect(view().name("settings/bank-form"))
                    .andExpect(model().attributeHasFieldErrors("bankAccount", "bankName", "accountNumber", "accountName"));
        }
    }

    @Nested
    @DisplayName("Device Token Management")
    class DeviceTokenTests {

        @Test
        @DisplayName("Should list device tokens")
        void shouldListDeviceTokens() throws Exception {
            mockMvc.perform(get("/settings/devices"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("settings/devices"))
                    .andExpect(model().attributeExists("deviceTokens"));
        }

        @Test
        @DisplayName("Should handle revoke all device tokens")
        void shouldHandleRevokeAllDeviceTokens() throws Exception {
            mockMvc.perform(post("/settings/devices/revoke-all").with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }
    }

    @Nested
    @DisplayName("Other Settings Pages")
    class OtherSettingsTests {

        @Test
        @DisplayName("Should render about page")
        void shouldRenderAboutPage() throws Exception {
            mockMvc.perform(get("/settings/about"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("settings/about"))
                    .andExpect(model().attributeExists("gitCommitId", "gitBranch"));
        }

        @Test
        @DisplayName("Should render privacy page")
        void shouldRenderPrivacyPage() throws Exception {
            mockMvc.perform(get("/settings/privacy"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("privacy"));
        }

        @Test
        @DisplayName("Should render audit logs page")
        void shouldRenderAuditLogsPage() throws Exception {
            mockMvc.perform(get("/settings/audit-logs"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("settings/audit-logs"))
                    .andExpect(model().attributeExists("auditLogs", "eventTypes"));
        }

        @Test
        @DisplayName("Should render audit logs page with filters")
        void shouldRenderAuditLogsPageWithFilters() throws Exception {
            mockMvc.perform(get("/settings/audit-logs")
                            .param("eventType", "LOGIN_SUCCESS")
                            .param("startDate", "2024-01-01")
                            .param("endDate", "2024-12-31"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("settings/audit-logs"));
        }

        @Test
        @DisplayName("Should return fragment for audit logs HTMX request")
        void shouldReturnFragmentForAuditLogsHtmxRequest() throws Exception {
            mockMvc.perform(get("/settings/audit-logs")
                            .header("HX-Request", "true"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("settings/fragments/audit-log-table :: table"));
        }

        @Test
        @DisplayName("Should render telegram settings page")
        void shouldRenderTelegramSettingsPage() throws Exception {
            mockMvc.perform(get("/settings/telegram"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("settings/telegram"))
                    .andExpect(model().attributeExists("telegramEnabled", "botUsername"));
        }
    }
}
