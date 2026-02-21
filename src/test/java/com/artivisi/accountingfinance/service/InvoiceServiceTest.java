package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.InvoiceStatus;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for InvoiceService.
 * Tests actual database queries and business logic.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("InvoiceService Integration Tests")
class InvoiceServiceTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Client testClient;

    @BeforeEach
    void setUp() {
        // Create a test client for invoice tests
        testClient = new Client();
        testClient.setCode("CLI-TEST-" + System.currentTimeMillis());
        testClient.setName("Test Client for Invoice");
        testClient = clientRepository.save(testClient);
    }

    @Nested
    @DisplayName("Find Operations")
    class FindOperationsTests {

        @Test
        @DisplayName("findById should return invoice with correct data")
        void findByIdShouldReturnInvoice() {
            Invoice invoice = createTestInvoice(InvoiceStatus.DRAFT);

            Invoice found = invoiceService.findById(invoice.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(invoice.getId());
            assertThat(found.getClient().getId()).isEqualTo(testClient.getId());
        }

        @Test
        @DisplayName("findById should throw EntityNotFoundException for invalid ID")
        void findByIdShouldThrowForInvalidId() {
            UUID invalidId = UUID.randomUUID();

            assertThatThrownBy(() -> invoiceService.findById(invalidId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Invoice not found");
        }

        @Test
        @DisplayName("findByInvoiceNumber should return correct invoice")
        void findByInvoiceNumberShouldReturnInvoice() {
            Invoice invoice = createTestInvoice(InvoiceStatus.DRAFT);

            Invoice found = invoiceService.findByInvoiceNumber(invoice.getInvoiceNumber());

            assertThat(found).isNotNull();
            assertThat(found.getInvoiceNumber()).isEqualTo(invoice.getInvoiceNumber());
        }

        @Test
        @DisplayName("findByInvoiceNumber should throw for invalid number")
        void findByInvoiceNumberShouldThrowForInvalid() {
            assertThatThrownBy(() -> invoiceService.findByInvoiceNumber("INVALID-NUMBER"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Invoice not found with number");
        }

        @Test
        @DisplayName("findAll should return paginated results")
        void findAllShouldReturnPaginatedResults() {
            createTestInvoice(InvoiceStatus.DRAFT);
            createTestInvoice(InvoiceStatus.SENT);

            Page<Invoice> page = invoiceService.findAll(PageRequest.of(0, 10));

            assertThat(page.getContent()).isNotEmpty();
            assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("findByFilters should filter by status")
        void findByFiltersShouldFilterByStatus() {
            createTestInvoice(InvoiceStatus.DRAFT);
            createTestInvoice(InvoiceStatus.SENT);

            Page<Invoice> draftPage = invoiceService.findByFilters(
                InvoiceStatus.DRAFT, null, null, PageRequest.of(0, 10));

            assertThat(draftPage.getContent()).isNotEmpty().allMatch(i -> i.getStatus() == InvoiceStatus.DRAFT);
        }

        @Test
        @DisplayName("findByClientId should return client's invoices")
        void findByClientIdShouldReturnClientInvoices() {
            createTestInvoice(InvoiceStatus.DRAFT);
            createTestInvoice(InvoiceStatus.SENT);

            List<Invoice> invoices = invoiceService.findByClientId(testClient.getId());

            assertThat(invoices).isNotEmpty().allMatch(i -> i.getClient().getId().equals(testClient.getId()));
        }

        @Test
        @DisplayName("findOverdueInvoices should return overdue invoices")
        void findOverdueInvoicesShouldReturnOverdueInvoices() {
            // Create an overdue invoice (due date in the past, status SENT)
            Invoice invoice = new Invoice();
            invoice.setClient(testClient);
            invoice.setInvoiceNumber("INV-OVERDUE-" + System.currentTimeMillis());
            invoice.setInvoiceDate(LocalDate.now().minusDays(60));
            invoice.setDueDate(LocalDate.now().minusDays(30));
            invoice.setAmount(new BigDecimal("1000000"));
            invoice.setStatus(InvoiceStatus.SENT);
            invoiceRepository.save(invoice);

            List<Invoice> overdueInvoices = invoiceService.findOverdueInvoices();

            assertThat(overdueInvoices).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Create Invoice")
    class CreateInvoiceTests {

        @Test
        @DisplayName("create should generate invoice number automatically")
        void createShouldGenerateInvoiceNumber() {
            Invoice invoice = new Invoice();
            invoice.setClient(testClient);
            invoice.setInvoiceDate(LocalDate.now());
            invoice.setDueDate(LocalDate.now().plusDays(30));
            invoice.setAmount(new BigDecimal("5000000"));

            Invoice saved = invoiceService.create(invoice);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getInvoiceNumber()).isNotNull();
            assertThat(saved.getInvoiceNumber()).startsWith("INV-");
            assertThat(saved.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        }

        @Test
        @DisplayName("create should use provided invoice number")
        void createShouldUseProvidedInvoiceNumber() {
            String customNumber = "INV-CUSTOM-" + System.currentTimeMillis();

            Invoice invoice = new Invoice();
            invoice.setClient(testClient);
            invoice.setInvoiceNumber(customNumber);
            invoice.setInvoiceDate(LocalDate.now());
            invoice.setDueDate(LocalDate.now().plusDays(30));
            invoice.setAmount(new BigDecimal("7500000"));

            Invoice saved = invoiceService.create(invoice);

            assertThat(saved.getInvoiceNumber()).isEqualTo(customNumber);
        }

        @Test
        @DisplayName("create should throw for duplicate invoice number")
        void createShouldThrowForDuplicateNumber() {
            String duplicateNumber = "INV-DUP-" + System.currentTimeMillis();

            Invoice first = new Invoice();
            first.setClient(testClient);
            first.setInvoiceNumber(duplicateNumber);
            first.setInvoiceDate(LocalDate.now());
            first.setDueDate(LocalDate.now().plusDays(30));
            first.setAmount(new BigDecimal("1000000"));
            invoiceService.create(first);

            Invoice second = new Invoice();
            second.setClient(testClient);
            second.setInvoiceNumber(duplicateNumber);
            second.setInvoiceDate(LocalDate.now());
            second.setDueDate(LocalDate.now().plusDays(30));
            second.setAmount(new BigDecimal("2000000"));

            assertThatThrownBy(() -> invoiceService.create(second))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invoice number already exists");
        }

        @Test
        @DisplayName("create should associate with project")
        void createShouldAssociateWithProject() {
            List<Project> projects = projectRepository.findAll();
            if (projects.isEmpty()) {
                return; // Skip if no projects
            }

            Project project = projects.get(0);

            Invoice invoice = new Invoice();
            invoice.setClient(testClient);
            invoice.setProject(project);
            invoice.setInvoiceDate(LocalDate.now());
            invoice.setDueDate(LocalDate.now().plusDays(30));
            invoice.setAmount(new BigDecimal("10000000"));

            Invoice saved = invoiceService.create(invoice);

            assertThat(saved.getProject()).isNotNull();
            assertThat(saved.getProject().getId()).isEqualTo(project.getId());
        }
    }

    @Nested
    @DisplayName("Update Invoice")
    class UpdateInvoiceTests {

        @Test
        @DisplayName("update should modify draft invoice")
        void updateShouldModifyDraftInvoice() {
            Invoice invoice = createTestInvoice(InvoiceStatus.DRAFT);

            Invoice updateData = new Invoice();
            updateData.setClient(testClient);
            updateData.setInvoiceNumber(invoice.getInvoiceNumber());
            updateData.setInvoiceDate(LocalDate.now().minusDays(1));
            updateData.setDueDate(LocalDate.now().plusDays(45));
            updateData.setAmount(new BigDecimal("8000000"));
            updateData.setNotes("Updated notes");

            Invoice updated = invoiceService.update(invoice.getId(), updateData);

            assertThat(updated.getAmount()).isEqualByComparingTo("8000000");
            assertThat(updated.getNotes()).isEqualTo("Updated notes");
        }

        @Test
        @DisplayName("update should throw for non-draft invoice")
        void updateShouldThrowForNonDraftInvoice() {
            Invoice invoice = createTestInvoice(InvoiceStatus.SENT);

            Invoice updateData = new Invoice();
            updateData.setClient(testClient);
            updateData.setInvoiceNumber(invoice.getInvoiceNumber());
            updateData.setAmount(new BigDecimal("9000000"));

            assertThatThrownBy(() -> invoiceService.update(invoice.getId(), updateData))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only draft invoices can be edited");
        }

        @Test
        @DisplayName("update should throw for duplicate invoice number")
        void updateShouldThrowForDuplicateNumber() {
            Invoice first = createTestInvoice(InvoiceStatus.DRAFT);
            Invoice second = createTestInvoice(InvoiceStatus.DRAFT);

            Invoice updateData = new Invoice();
            updateData.setClient(testClient);
            updateData.setInvoiceNumber(first.getInvoiceNumber()); // Try to use first's number
            updateData.setInvoiceDate(LocalDate.now());
            updateData.setDueDate(LocalDate.now().plusDays(30));
            updateData.setAmount(new BigDecimal("1000000"));

            assertThatThrownBy(() -> invoiceService.update(second.getId(), updateData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invoice number already exists");
        }
    }

    @Nested
    @DisplayName("Delete Invoice")
    class DeleteInvoiceTests {

        @Test
        @DisplayName("delete should remove draft invoice")
        void deleteShouldRemoveDraftInvoice() {
            Invoice invoice = createTestInvoice(InvoiceStatus.DRAFT);
            UUID invoiceId = invoice.getId();

            invoiceService.delete(invoiceId);

            assertThat(invoiceRepository.findById(invoiceId)).isEmpty();
        }

        @Test
        @DisplayName("delete should remove cancelled invoice")
        void deleteShouldRemoveCancelledInvoice() {
            Invoice invoice = createTestInvoice(InvoiceStatus.CANCELLED);
            UUID invoiceId = invoice.getId();

            invoiceService.delete(invoiceId);

            assertThat(invoiceRepository.findById(invoiceId)).isEmpty();
        }

        @Test
        @DisplayName("delete should throw for sent invoice")
        void deleteShouldThrowForSentInvoice() {
            Invoice invoice = createTestInvoice(InvoiceStatus.SENT);

            assertThatThrownBy(() -> invoiceService.delete(invoice.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only draft or cancelled invoices can be deleted");
        }

        @Test
        @DisplayName("delete should throw for paid invoice")
        void deleteShouldThrowForPaidInvoice() {
            Invoice invoice = createTestInvoice(InvoiceStatus.PAID);

            assertThatThrownBy(() -> invoiceService.delete(invoice.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only draft or cancelled invoices can be deleted");
        }
    }

    @Nested
    @DisplayName("Invoice Status Transitions")
    class StatusTransitionTests {

        @Test
        @DisplayName("send should change draft to sent")
        void sendShouldChangeDraftToSent() {
            Invoice invoice = createTestInvoice(InvoiceStatus.DRAFT);

            Invoice sent = invoiceService.send(invoice.getId());

            assertThat(sent.getStatus()).isEqualTo(InvoiceStatus.SENT);
            assertThat(sent.getSentAt()).isNotNull();
        }

        @Test
        @DisplayName("send should throw for non-draft invoice")
        void sendShouldThrowForNonDraftInvoice() {
            Invoice invoice = createTestInvoice(InvoiceStatus.SENT);

            assertThatThrownBy(() -> invoiceService.send(invoice.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only draft invoices can be sent");
        }

        @Test
        @DisplayName("markAsPaid should change sent to paid")
        void markAsPaidShouldChangeSentToPaid() {
            Invoice invoice = createTestInvoice(InvoiceStatus.SENT);

            Invoice paid = invoiceService.markAsPaid(invoice.getId());

            assertThat(paid.getStatus()).isEqualTo(InvoiceStatus.PAID);
            assertThat(paid.getPaidAt()).isNotNull();
        }

        @Test
        @DisplayName("markAsPaid should change overdue to paid")
        void markAsPaidShouldChangeOverdueToPaid() {
            Invoice invoice = createTestInvoice(InvoiceStatus.OVERDUE);

            Invoice paid = invoiceService.markAsPaid(invoice.getId());

            assertThat(paid.getStatus()).isEqualTo(InvoiceStatus.PAID);
        }

        @Test
        @DisplayName("markAsPaid should throw for draft invoice")
        void markAsPaidShouldThrowForDraftInvoice() {
            Invoice invoice = createTestInvoice(InvoiceStatus.DRAFT);

            assertThatThrownBy(() -> invoiceService.markAsPaid(invoice.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only sent, overdue, or partial invoices can be marked as paid");
        }

        @Test
        @DisplayName("cancel should change any status except paid to cancelled")
        void cancelShouldChangeStatusToCancelled() {
            Invoice draftInvoice = createTestInvoice(InvoiceStatus.DRAFT);
            Invoice sentInvoice = createTestInvoice(InvoiceStatus.SENT);

            Invoice cancelledDraft = invoiceService.cancel(draftInvoice.getId());
            Invoice cancelledSent = invoiceService.cancel(sentInvoice.getId());

            assertThat(cancelledDraft.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
            assertThat(cancelledSent.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
        }

        @Test
        @DisplayName("cancel should throw for paid invoice")
        void cancelShouldThrowForPaidInvoice() {
            Invoice invoice = createTestInvoice(InvoiceStatus.PAID);

            assertThatThrownBy(() -> invoiceService.cancel(invoice.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Paid invoices cannot be cancelled");
        }
    }

    @Nested
    @DisplayName("Link Transaction and Mark Paid")
    class LinkTransactionTests {

        @Test
        @DisplayName("linkTransactionAndMarkPaid should link transaction and change status")
        void linkTransactionAndMarkPaidShouldLinkAndChangeStatus() {
            Invoice invoice = createTestInvoice(InvoiceStatus.SENT);

            // Create a mock transaction
            Transaction transaction = new Transaction();
            transaction.setTransactionNumber("TRX-LINK-" + System.currentTimeMillis());
            transaction.setTransactionDate(LocalDate.now());
            transaction.setAmount(invoice.getAmount());
            transaction.setDescription("Payment for " + invoice.getInvoiceNumber());
            transaction.setStatus(TransactionStatus.POSTED);
            transaction = transactionRepository.save(transaction);

            Invoice result = invoiceService.linkTransactionAndMarkPaid(invoice.getId(), transaction);

            assertThat(result.getStatus()).isEqualTo(InvoiceStatus.PAID);
            assertThat(result.getTransaction()).isNotNull();
            assertThat(result.getTransaction().getId()).isEqualTo(transaction.getId());
            assertThat(result.getPaidAt()).isNotNull();
        }

        @Test
        @DisplayName("linkTransactionAndMarkPaid should throw for draft invoice")
        void linkTransactionAndMarkPaidShouldThrowForDraft() {
            Invoice invoice = createTestInvoice(InvoiceStatus.DRAFT);
            Transaction transaction = new Transaction();

            assertThatThrownBy(() -> invoiceService.linkTransactionAndMarkPaid(invoice.getId(), transaction))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only sent, overdue, or partial invoices can be marked as paid");
        }
    }

    @Nested
    @DisplayName("Update Overdue Invoices")
    class UpdateOverdueTests {

        @Test
        @DisplayName("updateOverdueInvoices should mark overdue invoices")
        void updateOverdueInvoicesShouldMarkOverdue() {
            // Create an invoice that's past due date with SENT status
            Invoice invoice = new Invoice();
            invoice.setClient(testClient);
            invoice.setInvoiceNumber("INV-PAST-" + System.currentTimeMillis());
            invoice.setInvoiceDate(LocalDate.now().minusDays(60));
            invoice.setDueDate(LocalDate.now().minusDays(30));
            invoice.setAmount(new BigDecimal("1000000"));
            invoice.setStatus(InvoiceStatus.SENT);
            invoice = invoiceRepository.save(invoice);

            int count = invoiceService.updateOverdueInvoices();

            Invoice updated = invoiceService.findById(invoice.getId());
            assertThat(updated.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);
            assertThat(count).isGreaterThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Aggregation Queries")
    class AggregationQueriesTests {

        @Test
        @DisplayName("countByStatus should return correct count")
        void countByStatusShouldReturnCorrectCount() {
            createTestInvoice(InvoiceStatus.DRAFT);
            createTestInvoice(InvoiceStatus.DRAFT);
            createTestInvoice(InvoiceStatus.SENT);

            long draftCount = invoiceService.countByStatus(InvoiceStatus.DRAFT);
            long sentCount = invoiceService.countByStatus(InvoiceStatus.SENT);

            assertThat(draftCount).isGreaterThanOrEqualTo(2);
            assertThat(sentCount).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("sumPaidAmountByClientId should sum paid invoices")
        void sumPaidAmountByClientIdShouldSumPaidInvoices() {
            createTestInvoiceWithAmount(InvoiceStatus.PAID, new BigDecimal("5000000"));
            createTestInvoiceWithAmount(InvoiceStatus.PAID, new BigDecimal("3000000"));
            createTestInvoiceWithAmount(InvoiceStatus.DRAFT, new BigDecimal("2000000")); // Not counted

            BigDecimal sum = invoiceService.sumPaidAmountByClientId(testClient.getId());

            assertThat(sum).isGreaterThanOrEqualTo(new BigDecimal("8000000"));
        }
    }

    @Nested
    @DisplayName("Invoice Number Generation")
    class InvoiceNumberGenerationTests {

        @Test
        @DisplayName("should generate unique sequential invoice numbers")
        void shouldGenerateUniqueSequentialNumbers() {
            Invoice inv1 = new Invoice();
            inv1.setClient(testClient);
            inv1.setInvoiceDate(LocalDate.now());
            inv1.setDueDate(LocalDate.now().plusDays(30));
            inv1.setAmount(new BigDecimal("1000000"));

            Invoice inv2 = new Invoice();
            inv2.setClient(testClient);
            inv2.setInvoiceDate(LocalDate.now());
            inv2.setDueDate(LocalDate.now().plusDays(30));
            inv2.setAmount(new BigDecimal("2000000"));

            Invoice saved1 = invoiceService.create(inv1);
            Invoice saved2 = invoiceService.create(inv2);

            assertThat(saved1.getInvoiceNumber()).isNotEqualTo(saved2.getInvoiceNumber());
            // Both should have same prefix (INV-YYYYMM-)
            String prefix1 = saved1.getInvoiceNumber().substring(0, saved1.getInvoiceNumber().lastIndexOf("-") + 1);
            String prefix2 = saved2.getInvoiceNumber().substring(0, saved2.getInvoiceNumber().lastIndexOf("-") + 1);
            assertThat(prefix1).isEqualTo(prefix2);
        }
    }

    // Helper methods

    private Invoice createTestInvoice(InvoiceStatus status) {
        return createTestInvoiceWithAmount(status, new BigDecimal("5000000"));
    }

    private Invoice createTestInvoiceWithAmount(InvoiceStatus status, BigDecimal amount) {
        Invoice invoice = new Invoice();
        invoice.setClient(testClient);
        invoice.setInvoiceNumber("INV-TEST-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4));
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setAmount(amount);
        invoice.setStatus(status);
        return invoiceRepository.save(invoice);
    }
}
