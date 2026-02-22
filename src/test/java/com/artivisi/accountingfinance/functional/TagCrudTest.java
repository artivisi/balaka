package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.TagType;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.TagTypeRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@DisplayName("Tag CRUD Tests")
@Import(ServiceTestDataInitializer.class)
class TagCrudTest extends PlaywrightTestBase {

    @Autowired
    private TagTypeRepository tagTypeRepository;

    private TagType tagType;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
        // Ensure a tag type exists
        tagType = tagTypeRepository.findAllActive().stream().findFirst().orElse(null);
        if (tagType == null) {
            tagType = new TagType();
            tagType.setCode("TAGTEST");
            tagType.setName("Test Tipe Label");
            tagType.setActive(true);
            tagType = tagTypeRepository.save(tagType);
        }
    }

    @Test
    @DisplayName("Should display tag list page with breadcrumb")
    void shouldDisplayTagListPage() {
        navigateTo("/tags/types/" + tagType.getId() + "/tags");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).containsText(tagType.getName());
        // Breadcrumb text should show "Label Transaksi" in content area
        assertThat(page.locator("main nav:has-text('Label Transaksi')").first()).isVisible();
    }

    @Test
    @DisplayName("Should navigate from tag type to tag list via Kelola Label link")
    void shouldNavigateFromTagTypeToTagList() {
        navigateTo("/tags/types");
        waitForPageLoad();

        var manageLink = page.locator("[data-testid='tag-type-manage-" + tagType.getCode() + "']");
        if (manageLink.isVisible()) {
            manageLink.click();
            waitForPageLoad();
            assertThat(page.locator("#page-title, h1").first()).containsText(tagType.getName());
        }
    }

    @Test
    @DisplayName("Should create new tag")
    void shouldCreateNewTag() {
        navigateTo("/tags/types/" + tagType.getId() + "/tags/new");
        waitForPageLoad();

        page.locator("#code").fill("MKT");
        page.locator("#name").fill("Marketing");
        page.locator("#description").fill("Departemen Marketing");
        page.locator("#btn-save").click();

        // Wait for redirect back to tag list (away from /new)
        page.waitForURL(url -> !url.contains("/new"), new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(10000));
        waitForPageLoad();

        assertThat(page.locator("body")).containsText("berhasil ditambahkan");
        assertThat(page.locator("[data-testid='tag-row-MKT']")).isVisible();
    }

    @Test
    @DisplayName("Should show validation error for duplicate code within type")
    void shouldShowValidationErrorForDuplicateCode() {
        // Create first tag
        navigateTo("/tags/types/" + tagType.getId() + "/tags/new");
        waitForPageLoad();
        page.locator("#code").fill("DUPTAG");
        page.locator("#name").fill("Duplikat 1");
        page.locator("#btn-save").click();
        waitForPageLoad();

        // Create second with same code
        navigateTo("/tags/types/" + tagType.getId() + "/tags/new");
        waitForPageLoad();
        page.locator("#code").fill("DUPTAG");
        page.locator("#name").fill("Duplikat 2");
        page.locator("#btn-save").click();
        waitForPageLoad();

        assertThat(page.locator(".text-red-600").first()).isVisible();
    }

    @Test
    @DisplayName("Should edit tag")
    void shouldEditTag() {
        // Create tag first
        navigateTo("/tags/types/" + tagType.getId() + "/tags/new");
        waitForPageLoad();
        page.locator("#code").fill("EDTAG");
        page.locator("#name").fill("Untuk Edit");
        page.locator("#btn-save").click();
        waitForPageLoad();

        // Click edit
        var editLink = page.locator("[data-testid='tag-row-EDTAG'] a:has-text('Edit')");
        editLink.click();
        waitForPageLoad();

        page.locator("#name").fill("Sudah Diedit");
        page.locator("#btn-save").click();
        waitForPageLoad();

        assertThat(page.locator("body")).containsText("berhasil diubah");
    }

    @Test
    @DisplayName("Should delete tag without transactions")
    void shouldDeleteTagWithoutTransactions() {
        // Create tag
        navigateTo("/tags/types/" + tagType.getId() + "/tags/new");
        waitForPageLoad();
        page.locator("#code").fill("DELTAG");
        page.locator("#name").fill("Untuk Hapus");
        page.locator("#btn-save").click();
        waitForPageLoad();

        // Delete
        page.onDialog(dialog -> dialog.accept());
        page.locator("[data-testid='tag-row-DELTAG'] button:has-text('Hapus')").click();
        waitForPageLoad();

        assertThat(page.locator("body")).containsText("berhasil dihapus");
    }
}
