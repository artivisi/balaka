package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.Tag;
import com.artivisi.accountingfinance.entity.TagType;
import com.artivisi.accountingfinance.enums.TemplateType;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TagRepository;
import com.artivisi.accountingfinance.repository.TagTypeRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@DisplayName("Transaction Tag Assignment Tests")
@Import(ServiceTestDataInitializer.class)
class TransactionTagAssignmentTest extends PlaywrightTestBase {

    @Autowired
    private TagTypeRepository tagTypeRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private JournalTemplateRepository templateRepository;

    private UUID templateId;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
        ensureTestTags();
        // Find a SIMPLE template to use (DETAILED templates don't have #amount field)
        templateId = templateRepository.findAll().stream()
                .filter(t -> Boolean.TRUE.equals(t.getIsCurrentVersion()))
                .filter(t -> t.getTemplateType() == TemplateType.SIMPLE)
                .map(JournalTemplate::getId)
                .findFirst()
                .orElse(null);
    }

    private void ensureTestTags() {
        if (tagTypeRepository.findAllActive().stream().noneMatch(t -> "DEPT".equals(t.getCode()))) {
            TagType dept = new TagType();
            dept.setCode("DEPT");
            dept.setName("Departemen");
            dept.setActive(true);
            dept = tagTypeRepository.save(dept);

            Tag mkt = new Tag();
            mkt.setTagType(dept);
            mkt.setCode("MKT");
            mkt.setName("Marketing");
            mkt.setActive(true);
            tagRepository.save(mkt);

            Tag fin = new Tag();
            fin.setTagType(dept);
            fin.setCode("FIN");
            fin.setName("Finance");
            fin.setActive(true);
            tagRepository.save(fin);
        }
    }

    @Test
    @DisplayName("Should show tag checkboxes on transaction form")
    void shouldShowTagCheckboxesOnForm() {
        if (templateId == null) return;

        navigateTo("/transactions/new?templateId=" + templateId);
        waitForPageLoad();
        page.locator("#amount").waitFor(new Locator.WaitForOptions().setTimeout(15000));

        assertThat(page.locator("input[name='tagIds']").first()).isVisible();
        assertThat(page.locator("text=Departemen").first()).isVisible();
    }

    @Test
    @DisplayName("Should create transaction with tags and see them in detail")
    void shouldCreateTransactionWithTags() {
        if (templateId == null) return;

        navigateTo("/transactions/new?templateId=" + templateId);
        waitForPageLoad();
        page.locator("#amount").waitFor(new Locator.WaitForOptions().setTimeout(15000));

        // Fill required fields
        page.locator("#transactionDate").fill("2025-01-15");
        page.locator("#amount").fill("100000");
        page.locator("#description").fill("Test transaksi dengan label");

        // Check first tag (Finance — alphabetical order)
        page.locator("input[name='tagIds']").first().check();

        // Save as draft
        page.locator("#btn-simpan-draft").click();
        // Wait for redirect to detail page
        page.waitForURL(java.util.regex.Pattern.compile(".*/transactions/[0-9a-f]{8}-.*"),
                new Page.WaitForURLOptions().setTimeout(15000));
        waitForPageLoad();

        // Detail page should show the LABEL section with the tag
        assertThat(page.locator("text=Departemen:").first()).isVisible();
    }

    @Test
    @DisplayName("Should create transaction without tags works fine")
    void shouldCreateTransactionWithoutTags() {
        if (templateId == null) return;

        navigateTo("/transactions/new?templateId=" + templateId);
        waitForPageLoad();
        page.locator("#amount").waitFor(new Locator.WaitForOptions().setTimeout(15000));

        page.locator("#transactionDate").fill("2025-01-16");
        page.locator("#amount").fill("200000");
        page.locator("#description").fill("Test transaksi tanpa label");

        // Don't check any tags
        page.locator("#btn-simpan-draft").click();
        page.waitForURL(java.util.regex.Pattern.compile(".*/transactions/[0-9a-f]{8}-.*"),
                new Page.WaitForURLOptions().setTimeout(15000));
        waitForPageLoad();

        // Should succeed - detail page loads
        assertThat(page.locator("#page-title")).isVisible();
    }
}
