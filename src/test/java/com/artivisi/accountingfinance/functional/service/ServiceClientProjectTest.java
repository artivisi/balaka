package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Seeded service clients (src/test/resources/testdata/service/clients.csv) in the client list,
 * and the client-to-project relationship from projects.csv as shown on client and project pages.
 * Project list/detail and milestones are covered by ProjectControllerFunctionalTest and
 * ServiceMilestonePaymentTermTest.
 */
@DisplayName("Service Industry - Clients & Projects")
@Import(ServiceTestDataInitializer.class)
class ServiceClientProjectTest extends PlaywrightTestBase {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ProjectRepository projectRepository;

    private int projectCount(String clientCode) {
        UUID clientId = clientRepository.findByCode(clientCode)
                .orElseThrow(() -> new IllegalStateException("Seeded client missing: " + clientCode))
                .getId();
        return (int) projectRepository.countByClientId(clientId);
    }

    @BeforeEach
    void login() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should find each seeded client in client list by code with name link, contact, email and status")
    void shouldDisplayClientList() {
        navigateTo("/clients");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Daftar Klien");

        assertClientRow("MANDIRI", "PT Bank Mandiri Tbk", "Budi Gunawan", "info@mandiri.co.id");
        assertClientRow("TELKOM", "PT Telkom Indonesia Tbk", "Ririek Adriansyah", "info@telkom.co.id");
        assertClientRow("PERTAMINA", "PT Pertamina", "Nicke Widyawati", "pcc@pertamina.com");
    }

    @Test
    @DisplayName("Should list only the client's own projects with status on client detail")
    void shouldDisplayClientProjects() {
        navigateTo("/clients");
        waitForPageLoad();
        search("MANDIRI");
        page.locator("[data-testid='client-name-MANDIRI']").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/clients/MANDIRI", page.url());
        assertThat(page.locator("[data-testid='client-code']")).hasText("MANDIRI");
        Locator projects = page.locator("[data-testid='client-detail'] a[href^='/projects/']");
        // Other classes add projects to seeded clients in the shared DB: the page must list
        // exactly the client's projects in the repository, including the seeded ones.
        assertThat(projects).hasCount(projectCount("MANDIRI"));
        assertProjectLink("PRJ-2024-001", "Core Banking System Consultation", "Aktif");
        assertProjectLink("PRJ-2024-002", "IT Security Training", "Selesai");

        navigateTo("/clients/PERTAMINA");
        waitForPageLoad();
        assertThat(projects).hasCount(projectCount("PERTAMINA"));
        assertProjectLink("PRJ-2024-004", "ERP Implementation Support", "Aktif");
    }

    @Test
    @DisplayName("Should open project from client detail and link back to the client")
    void shouldNavigateFromClientToProject() {
        navigateTo("/clients/TELKOM");
        waitForPageLoad();
        page.locator("[data-testid='client-detail'] a[href='/projects/PRJ-2024-003']").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/projects/PRJ-2024-003", page.url());
        Locator detail = page.locator("[data-testid='project-detail']");
        assertThat(detail.locator("h2")).hasText("Digital Transformation Roadmap");
        Locator clientLink = detail.locator("a[href='/clients/TELKOM']");
        assertThat(clientLink).hasText("PT Telkom Indonesia Tbk");

        clientLink.click();
        waitForPageLoad();
        assertEquals(baseUrl() + "/clients/TELKOM", page.url());
        assertThat(page.locator("[data-testid='client-detail'] h2")).hasText("PT Telkom Indonesia Tbk");
    }

    private void assertClientRow(String code, String name, String contact, String email) {
        search(code);
        assertThat(page.locator("[data-testid='client-table'] tbody tr")).hasCount(1);
        Locator cells = page.locator("[data-testid='client-row-" + code + "'] td");
        assertThat(cells.nth(0)).hasText(code);
        assertThat(cells.nth(1)).hasText(name);
        assertThat(page.locator("[data-testid='client-name-" + code + "']")).hasAttribute("href", "/clients/" + code);
        assertThat(cells.nth(2)).hasText(contact);
        assertThat(cells.nth(3)).hasText(email);
        assertThat(cells.nth(4)).hasText("Aktif");
    }

    private void assertProjectLink(String code, String name, String status) {
        Locator link = page.locator("[data-testid='client-detail'] a[href='/projects/" + code + "']");
        assertThat(link).containsText(name);
        assertThat(link).containsText(code);
        assertThat(link.locator("span.rounded-full")).hasText(status);
    }

    private void search(String keyword) {
        Locator input = page.locator("#search-input");
        input.fill(keyword);
        input.press("End");
        assertThat(page.locator("[data-testid='client-row-" + keyword + "']")).isVisible();
    }
}
