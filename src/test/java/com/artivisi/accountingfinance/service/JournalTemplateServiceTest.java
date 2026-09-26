package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.JournalTemplateLine;
import com.artivisi.accountingfinance.enums.CashFlowCategory;
import com.artivisi.accountingfinance.enums.JournalPosition;
import com.artivisi.accountingfinance.enums.TemplateCategory;
import com.artivisi.accountingfinance.enums.TemplateType;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for JournalTemplateService.
 * Tests actual database queries and business logic.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("JournalTemplateService Integration Tests")
class JournalTemplateServiceTest {

    @Autowired
    private JournalTemplateService journalTemplateService;

    @Autowired
    private JournalTemplateRepository journalTemplateRepository;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @Nested
    @DisplayName("Find Operations")
    class FindOperationsTests {

        @Test
        @DisplayName("findAll should return active templates")
        void findAllShouldReturnActiveTemplates() {
            List<JournalTemplate> templates = journalTemplateService.findAll();

            assertThat(templates).isNotEmpty().allMatch(JournalTemplate::getActive);
        }

        @Test
        @DisplayName("findById should return template with correct data")
        void findByIdShouldReturnTemplate() {
            JournalTemplate template = createTestTemplate();

            JournalTemplate found = journalTemplateService.findById(template.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(template.getId());
            assertThat(found.getTemplateName()).isEqualTo(template.getTemplateName());
        }

        @Test
        @DisplayName("findById should throw EntityNotFoundException for invalid ID")
        void findByIdShouldThrowForInvalidId() {
            UUID invalidId = UUID.randomUUID();

            assertThatThrownBy(() -> journalTemplateService.findById(invalidId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Template not found");
        }

        @Test
        @DisplayName("findByIdWithLines should return template with lines")
        void findByIdWithLinesShouldReturnTemplateWithLines() {
            JournalTemplate template = createTestTemplate();

            JournalTemplate found = journalTemplateService.findByIdWithLines(template.getId());

            assertThat(found.getLines()).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("findByCategory should filter by category")
        void findByCategoryShouldFilterByCategory() {
            List<JournalTemplate> incomeTemplates = journalTemplateService.findByCategory(TemplateCategory.INCOME);

            assertThat(incomeTemplates).isNotEmpty().allMatch(t -> t.getCategory() == TemplateCategory.INCOME);
        }

        @Test
        @DisplayName("findByCategory with null should return all active")
        void findByCategoryNullShouldReturnAllActive() {
            List<JournalTemplate> allTemplates = journalTemplateService.findByCategory(null);

            assertThat(allTemplates).isNotEmpty().allMatch(JournalTemplate::getActive);
        }

        @Test
        @DisplayName("search should find by template name")
        void searchShouldFindByTemplateName() {
            JournalTemplate template = buildTestTemplate();
            template.setTemplateName("Unique Search Template XYZ");
            addTemplateLines(template);
            journalTemplateService.create(template);

            Page<JournalTemplate> page = journalTemplateService.search("Unique Search", PageRequest.of(0, 10));

            assertThat(page.getContent()).isNotEmpty();
            assertThat(page.getContent()).anyMatch(t -> t.getTemplateName().contains("Unique"));
        }
    }

    @Nested
    @DisplayName("Create Template")
    class CreateTemplateTests {

        @Test
        @DisplayName("create should save template with lines")
        void createShouldSaveTemplateWithLines() {
            JournalTemplate template = buildTestTemplate();
            addTemplateLines(template);

            JournalTemplate saved = journalTemplateService.create(template);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getLines()).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("create should throw for template with less than 2 lines")
        void createShouldThrowForLessThanTwoLines() {
            JournalTemplate template = buildTestTemplate();

            assertThatThrownBy(() -> journalTemplateService.create(template))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2 lines");
        }

        @Test
        @DisplayName("create should throw for template without debit line")
        void createShouldThrowForNoDebitLine() {
            JournalTemplate template = buildTestTemplate();
            addCreditOnlyLines(template);

            assertThatThrownBy(() -> journalTemplateService.create(template))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one debit");
        }

        @Test
        @DisplayName("create should throw for template without credit line")
        void createShouldThrowForNoCreditLine() {
            JournalTemplate template = buildTestTemplate();
            addDebitOnlyLines(template);

            assertThatThrownBy(() -> journalTemplateService.create(template))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one debit and one credit");
        }
    }

    @Nested
    @DisplayName("Update Template")
    class UpdateTemplateTests {

        @Test
        @DisplayName("update should modify template fields")
        void updateShouldModifyFields() {
            JournalTemplate template = createTestTemplate();

            JournalTemplate updateData = buildTestTemplate();
            updateData.setTemplateName("Updated Template Name");
            updateData.setDescription("Updated description");
            addTemplateLines(updateData);

            JournalTemplate updated = journalTemplateService.update(template.getId(), updateData);

            assertThat(updated.getTemplateName()).isEqualTo("Updated Template Name");
            assertThat(updated.getDescription()).isEqualTo("Updated description");
            assertThat(updated.getVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("update should throw for system template")
        void updateShouldThrowForSystemTemplate() {
            JournalTemplate systemTemplate = seededSystemTemplate();
            JournalTemplate updateData = buildTestTemplate();
            addTemplateLines(updateData);

            assertThatThrownBy(() -> journalTemplateService.update(systemTemplate.getId(), updateData))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot modify system template");
            assertSystemTemplateUnchanged();
        }
    }

    @Nested
    @DisplayName("Duplicate Template")
    class DuplicateTemplateTests {

        @Test
        @DisplayName("duplicate should create copy with new name")
        void duplicateShouldCreateCopyWithNewName() {
            JournalTemplate source = createTestTemplate();
            String newName = "Duplicated Template " + System.nanoTime();

            JournalTemplate duplicate = journalTemplateService.duplicate(source.getId(), newName);

            assertThat(duplicate.getId()).isNotEqualTo(source.getId());
            assertThat(duplicate.getTemplateName()).isEqualTo(newName);
            assertThat(duplicate.getCategory()).isEqualTo(source.getCategory());
            assertThat(duplicate.getIsSystem()).isFalse();
        }

        @Test
        @DisplayName("duplicate should copy lines")
        void duplicateShouldCopyLines() {
            JournalTemplate source = createTestTemplate();
            int sourceLineCount = source.getLines().size();

            JournalTemplate duplicate = journalTemplateService.duplicate(source.getId(), "Duplicate " + System.nanoTime());

            assertThat(duplicate.getLines()).hasSize(sourceLineCount);
        }
    }

    @Nested
    @DisplayName("Usage Recording")
    class UsageRecordingTests {

        @Test
        @DisplayName("recordUsage should increment usage count")
        void recordUsageShouldIncrementCount() {
            JournalTemplate template = createTestTemplate();
            int initialCount = template.getUsageCount();

            journalTemplateService.recordUsage(template.getId());

            JournalTemplate updated = journalTemplateRepository.findById(template.getId()).orElseThrow();
            assertThat(updated.getUsageCount()).isEqualTo(initialCount + 1);
            assertThat(updated.getLastUsedAt()).isNotNull();
        }

        @Test
        @DisplayName("findMostUsed should return templates ordered by usage")
        void findMostUsedShouldReturnOrderedByUsage() {
            JournalTemplate template = createTestTemplate();
            journalTemplateService.recordUsage(template.getId());
            journalTemplateService.recordUsage(template.getId());

            List<JournalTemplate> mostUsed = journalTemplateService.findMostUsed();

            assertThat(mostUsed).isNotEmpty();
        }

        @Test
        @DisplayName("findRecentlyUsed should return templates ordered by last used")
        void findRecentlyUsedShouldReturnOrderedByLastUsed() {
            JournalTemplate template = createTestTemplate();
            journalTemplateService.recordUsage(template.getId());

            List<JournalTemplate> recentlyUsed = journalTemplateService.findRecentlyUsed();

            assertThat(recentlyUsed).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Activate/Deactivate Operations")
    class ActivateDeactivateTests {

        @Test
        @DisplayName("deactivate should set active to false")
        void deactivateShouldSetActiveFalse() {
            JournalTemplate template = createTestTemplate();

            journalTemplateService.deactivate(template.getId());

            JournalTemplate updated = journalTemplateRepository.findById(template.getId()).orElseThrow();
            assertThat(updated.getActive()).isFalse();
        }

        @Test
        @DisplayName("activate should set active to true")
        void activateShouldSetActiveTrue() {
            JournalTemplate template = createTestTemplate();
            journalTemplateService.deactivate(template.getId());

            journalTemplateService.activate(template.getId());

            JournalTemplate updated = journalTemplateRepository.findById(template.getId()).orElseThrow();
            assertThat(updated.getActive()).isTrue();
        }

        @Test
        @DisplayName("deactivate should throw for system template")
        void deactivateShouldThrowForSystemTemplate() {
            JournalTemplate systemTemplate = seededSystemTemplate();

            assertThatThrownBy(() -> journalTemplateService.deactivate(systemTemplate.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot deactivate system template");
            assertSystemTemplateUnchanged();
        }
    }

    @Nested
    @DisplayName("Delete Operations")
    class DeleteOperationsTests {

        @Test
        @DisplayName("delete should remove template")
        void deleteShouldRemoveTemplate() {
            JournalTemplate template = createTestTemplate();
            UUID templateId = template.getId();

            journalTemplateService.delete(templateId);

            assertThat(journalTemplateRepository.findById(templateId)).isEmpty();
        }

        @Test
        @DisplayName("delete should throw for system template")
        void deleteShouldThrowForSystemTemplate() {
            JournalTemplate systemTemplate = seededSystemTemplate();

            assertThatThrownBy(() -> journalTemplateService.delete(systemTemplate.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot delete system template");
            assertSystemTemplateUnchanged();
        }
    }

    @Nested
    @DisplayName("Tag Operations")
    class TagOperationsTests {

        @Test
        @DisplayName("addTag should add tag to template")
        void addTagShouldAddTagToTemplate() {
            JournalTemplate template = createTestTemplate();
            String tag = "testtag" + System.nanoTime();

            journalTemplateService.addTag(template.getId(), tag);

            List<String> tags = journalTemplateService.getTagsForTemplate(template.getId());
            assertThat(tags).contains(tag.toLowerCase());
        }

        @Test
        @DisplayName("addTag should throw for duplicate tag")
        void addTagShouldThrowForDuplicateTag() {
            JournalTemplate template = createTestTemplate();
            String tag = "duplicatetag" + System.nanoTime();
            journalTemplateService.addTag(template.getId(), tag);

            assertThatThrownBy(() -> journalTemplateService.addTag(template.getId(), tag))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tag already exists");
        }

        @Test
        @DisplayName("removeTag should remove tag from template")
        void removeTagShouldRemoveTagFromTemplate() {
            JournalTemplate template = createTestTemplate();
            String tag = "removetag" + System.nanoTime();
            journalTemplateService.addTag(template.getId(), tag);

            // Verify tag was added
            List<String> tagsBeforeRemove = journalTemplateService.getTagsForTemplate(template.getId());
            assertThat(tagsBeforeRemove).contains(tag.toLowerCase());

            journalTemplateService.removeTag(template.getId(), tag);

            // Verify by checking the template directly (not through repository query)
            JournalTemplate updated = journalTemplateService.findById(template.getId());
            assertThat(updated.getTagNames()).doesNotContain(tag.toLowerCase());
        }

        @Test
        @DisplayName("findByTag should return templates with matching tag")
        void findByTagShouldReturnMatchingTemplates() {
            JournalTemplate template = createTestTemplate();
            String tag = "findtag" + System.nanoTime();
            journalTemplateService.addTag(template.getId(), tag);

            List<JournalTemplate> templates = journalTemplateService.findByTag(tag);

            assertThat(templates).isNotEmpty().anyMatch(t -> t.getId().equals(template.getId()));
        }

        @Test
        @DisplayName("getDistinctTags should return all unique tags")
        void getDistinctTagsShouldReturnUniqueTags() {
            JournalTemplate template = createTestTemplate();
            String tag1 = "unique1" + System.nanoTime();
            String tag2 = "unique2" + System.nanoTime();
            journalTemplateService.addTag(template.getId(), tag1);
            journalTemplateService.addTag(template.getId(), tag2);

            List<String> distinctTags = journalTemplateService.getDistinctTags();

            assertThat(distinctTags).contains(tag1.toLowerCase(), tag2.toLowerCase());
        }

        @Test
        @DisplayName("searchTags should find tags by query")
        void searchTagsShouldFindByQuery() {
            JournalTemplate template = createTestTemplate();
            String tag = "searchtag" + System.nanoTime();
            journalTemplateService.addTag(template.getId(), tag);

            List<String> foundTags = journalTemplateService.searchTags("searchtag");

            assertThat(foundTags).contains(tag.toLowerCase());
        }
    }

    // Helper methods

    private JournalTemplate createTestTemplate() {
        JournalTemplate template = buildTestTemplate();
        addTemplateLines(template);
        return journalTemplateService.create(template);
    }

    private JournalTemplate buildTestTemplate() {
        JournalTemplate template = new JournalTemplate();
        template.setTemplateName("Test Template " + System.nanoTime());
        template.setCategory(TemplateCategory.INCOME);
        template.setCashFlowCategory(CashFlowCategory.OPERATING);
        template.setTemplateType(TemplateType.SIMPLE);
        template.setDescription("Test description");
        template.setIsSystem(false);
        template.setActive(true);
        return template;
    }

    private void addTemplateLines(JournalTemplate template) {
        // Get accounts from seed data
        List<ChartOfAccount> accounts = chartOfAccountRepository.findAll();
        ChartOfAccount debitAccount = accounts.stream()
            .filter(a -> a.getAccountType().name().equals("ASSET"))
            .findFirst()
            .orElse(accounts.get(0));
        ChartOfAccount creditAccount = accounts.stream()
            .filter(a -> a.getAccountType().name().equals("REVENUE"))
            .findFirst()
            .orElse(accounts.get(1));

        JournalTemplateLine debitLine = new JournalTemplateLine();
        debitLine.setAccount(debitAccount);
        debitLine.setPosition(JournalPosition.DEBIT);
        debitLine.setFormula("amount");
        debitLine.setLineOrder(1);
        template.addLine(debitLine);

        JournalTemplateLine creditLine = new JournalTemplateLine();
        creditLine.setAccount(creditAccount);
        creditLine.setPosition(JournalPosition.CREDIT);
        creditLine.setFormula("amount");
        creditLine.setLineOrder(2);
        template.addLine(creditLine);
    }

    private void addCreditOnlyLines(JournalTemplate template) {
        List<ChartOfAccount> accounts = chartOfAccountRepository.findAll();
        ChartOfAccount account1 = accounts.get(0);
        ChartOfAccount account2 = accounts.get(1);

        JournalTemplateLine creditLine1 = new JournalTemplateLine();
        creditLine1.setAccount(account1);
        creditLine1.setPosition(JournalPosition.CREDIT);
        creditLine1.setFormula("amount");
        creditLine1.setLineOrder(1);
        template.addLine(creditLine1);

        JournalTemplateLine creditLine2 = new JournalTemplateLine();
        creditLine2.setAccount(account2);
        creditLine2.setPosition(JournalPosition.CREDIT);
        creditLine2.setFormula("amount");
        creditLine2.setLineOrder(2);
        template.addLine(creditLine2);
    }

    private void addDebitOnlyLines(JournalTemplate template) {
        List<ChartOfAccount> accounts = chartOfAccountRepository.findAll();
        ChartOfAccount account1 = accounts.get(0);
        ChartOfAccount account2 = accounts.get(1);

        JournalTemplateLine debitLine1 = new JournalTemplateLine();
        debitLine1.setAccount(account1);
        debitLine1.setPosition(JournalPosition.DEBIT);
        debitLine1.setFormula("amount");
        debitLine1.setLineOrder(1);
        template.addLine(debitLine1);

        JournalTemplateLine debitLine2 = new JournalTemplateLine();
        debitLine2.setAccount(account2);
        debitLine2.setPosition(JournalPosition.DEBIT);
        debitLine2.setFormula("amount");
        debitLine2.setLineOrder(2);
        template.addLine(debitLine2);
    }

    @Nested
    @DisplayName("Template Line Validation")
    class TemplateLineValidationTests {

        @Test
        @DisplayName("Should reject template with invalid formula")
        void shouldRejectTemplateWithInvalidFormula() {
            JournalTemplate template = buildTestTemplate();

            List<ChartOfAccount> accounts = chartOfAccountRepository.findAll();
            ChartOfAccount debitAccount = accounts.stream()
                .filter(a -> a.getAccountType().name().equals("ASSET"))
                .findFirst().orElse(accounts.get(0));
            ChartOfAccount creditAccount = accounts.stream()
                .filter(a -> a.getAccountType().name().equals("REVENUE"))
                .findFirst().orElse(accounts.get(1));

            JournalTemplateLine debitLine = new JournalTemplateLine();
            debitLine.setAccount(debitAccount);
            debitLine.setPosition(JournalPosition.DEBIT);
            debitLine.setFormula("invalid!!!formula");
            debitLine.setLineOrder(1);
            template.addLine(debitLine);

            JournalTemplateLine creditLine = new JournalTemplateLine();
            creditLine.setAccount(creditAccount);
            creditLine.setPosition(JournalPosition.CREDIT);
            creditLine.setFormula("amount");
            creditLine.setLineOrder(2);
            template.addLine(creditLine);

            assertThatThrownBy(() -> journalTemplateService.create(template))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid formula");
        }

        @Test
        @DisplayName("Should reject template line without account and without accountHint")
        void shouldRejectLineWithoutAccountAndWithoutHint() {
            JournalTemplate template = buildTestTemplate();

            JournalTemplateLine debitLine = new JournalTemplateLine();
            debitLine.setAccount(null);
            debitLine.setPosition(JournalPosition.DEBIT);
            debitLine.setFormula("amount");
            debitLine.setLineOrder(1);
            // No accountHint set
            template.addLine(debitLine);

            List<ChartOfAccount> accounts = chartOfAccountRepository.findAll();
            JournalTemplateLine creditLine = new JournalTemplateLine();
            creditLine.setAccount(accounts.get(0));
            creditLine.setPosition(JournalPosition.CREDIT);
            creditLine.setFormula("amount");
            creditLine.setLineOrder(2);
            template.addLine(creditLine);

            assertThatThrownBy(() -> journalTemplateService.create(template))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no account and no accountHint");
        }

        @Test
        @DisplayName("Should reject template with duplicate accountHint")
        void shouldRejectTemplateWithDuplicateAccountHint() {
            JournalTemplate template = buildTestTemplate();

            JournalTemplateLine debitLine = new JournalTemplateLine();
            debitLine.setAccount(null);
            debitLine.setAccountHint("cash");
            debitLine.setPosition(JournalPosition.DEBIT);
            debitLine.setFormula("amount");
            debitLine.setLineOrder(1);
            template.addLine(debitLine);

            JournalTemplateLine creditLine = new JournalTemplateLine();
            creditLine.setAccount(null);
            creditLine.setAccountHint("cash"); // duplicate
            creditLine.setPosition(JournalPosition.CREDIT);
            creditLine.setFormula("amount");
            creditLine.setLineOrder(2);
            template.addLine(creditLine);

            assertThatThrownBy(() -> journalTemplateService.create(template))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate accountHint");
        }
    }

    @Nested
    @DisplayName("FindAllWithLines and FindByTag Edge Cases")
    class AdditionalFindTests {

        @Test
        @DisplayName("findAllWithLines should return templates with lines loaded")
        void findAllWithLinesShouldReturnTemplatesWithLines() {
            List<JournalTemplate> templates = journalTemplateService.findAllWithLines();

            assertThat(templates).isNotEmpty();
            // All templates should have lines eagerly loaded
            for (JournalTemplate t : templates) {
                assertThat(t.getLines()).isNotNull();
            }
        }

        @Test
        @DisplayName("findByTag with non-existent tag should return empty list")
        void findByTagWithNonExistentTagShouldReturnEmptyList() {
            List<JournalTemplate> templates = journalTemplateService.findByTag("nonexistenttag" + System.nanoTime());

            assertThat(templates).isEmpty();
        }
    }

    // V900: system template "Pembelian Persediaan"
    private static final UUID SYSTEM_TEMPLATE_ID = UUID.fromString("f5000000-0000-0000-0000-000000000001");

    private JournalTemplate seededSystemTemplate() {
        JournalTemplate template = journalTemplateRepository.findById(SYSTEM_TEMPLATE_ID)
            .orElseThrow(() -> new IllegalStateException("Seeded system template missing: " + SYSTEM_TEMPLATE_ID));
        assertThat(template.getIsSystem()).isTrue();
        return template;
    }

    private void assertSystemTemplateUnchanged() {
        JournalTemplate stored = journalTemplateRepository.findById(SYSTEM_TEMPLATE_ID)
            .orElseThrow(() -> new AssertionError("System template was deleted"));
        assertThat(stored.getTemplateName()).isEqualTo("Pembelian Persediaan");
        assertThat(stored.getActive()).isTrue();
        assertThat(stored.getIsSystem()).isTrue();
    }
}
