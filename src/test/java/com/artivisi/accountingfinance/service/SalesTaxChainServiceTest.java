package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.InvoiceLine;
import com.artivisi.accountingfinance.entity.InvoicePayment;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.entity.TaxFiling;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.entity.WithholdingPayment;
import com.artivisi.accountingfinance.enums.PayeeType;
import com.artivisi.accountingfinance.enums.PaymentMethod;
import com.artivisi.accountingfinance.enums.SalesTaxExceptionType;
import com.artivisi.accountingfinance.enums.SalesTaxReconLine;
import com.artivisi.accountingfinance.enums.TaxFilingStatus;
import com.artivisi.accountingfinance.enums.TaxFilingType;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.enums.VoidReason;
import com.artivisi.accountingfinance.enums.WithholdingTreatment;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.SalesTaxChainService.ChainIssue;
import com.artivisi.accountingfinance.service.SalesTaxChainService.ReconLine;
import com.artivisi.accountingfinance.service.SalesTaxChainService.Reconciliation;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chain, exceptions and reconciliation against the integration DB. V800 chart: revenue 4.1.01, 4.1.02, Bank BCA 1.1.02,
 * Hutang PPN 2.1.03; V915 adds Kredit Pajak PPh 23 1.1.26. Reserved test years 2144-2152, 2098.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "app.encryption.key=Kea3OzaaPR89QOebS1b427C8xxRoztOft8rlSEL3W7k=")
@Transactional
@WithMockUser(username = "tester")
@DisplayName("Sales tax chain")
class SalesTaxChainServiceTest {

    @Autowired private SalesTaxChainService chainService;
    @Autowired private InvoiceTaxLinkService linkService;
    @Autowired private InvoiceService invoiceService;
    @Autowired private ClientService clientService;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionApiService transactionApiService;
    @Autowired private TaxTransactionDetailService taxDetailService;
    @Autowired private TaxTransactionDetailRepository taxDetailRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private ReportService reportService;
    @Autowired private TaxFilingService taxFilingService;
    @Autowired private VendorService vendorService;
    @Autowired private WithholdingPaymentService withholdingPaymentService;
    @Autowired private CompanyConfigService companyConfigService;
    @Autowired private EntityManager entityManager;

    /** chk_tax_filing_year stops at 2100. */
    private static final int FILING_YEAR = 2098;

    private int sequence;

    // ==================== reconciliation ====================

    @Test
    @DisplayName("A clean invoice: revenue equals DPP, no reconciling line, PPN and PPh 23 effects exact and tied to the ledger")
    void cleanChainReconciles() {
        Invoice invoice = sent(date(2144, 3, 10), "11", "4.1.01:10000000");
        postRevenue(invoice);
        faktur(invoice, date(2144, 3, 12));
        InvoicePayment receipt = pay(invoice, date(2144, 3, 20), "11100000", "200000");
        Transaction bupotTransaction = bupot(invoice, receipt, date(2144, 3, 25), "200000").getTransaction();
        transactionService.post(bupotTransaction.getId(), "tester");

        Reconciliation recon = recon(2144, 3);

        assertThat(recon.bookedRevenue()).isEqualByComparingTo("10000000");
        assertThat(recon.bookedRevenue()).isEqualByComparingTo(reportService
                .generateIncomeStatementExcludingClosing(date(2144, 3, 1), date(2144, 3, 31)).totalRevenue());
        assertThat(recon.lines()).allSatisfy(line -> assertThat(line.amount()).isEqualByComparingTo("0"));
        assertThat(recon.sptDpp()).isEqualByComparingTo("10000000");
        assertThat(recon.unreconciled()).isEqualByComparingTo("0");
        assertThat(recon.taxEffects().ppnPerFaktur()).isEqualByComparingTo("1100000");
        assertThat(recon.taxEffects().ppnPerLedger()).isEqualByComparingTo("1100000");
        assertThat(recon.taxEffects().ppnDifference()).isEqualByComparingTo("0");
        assertThat(recon.taxEffects().invoiceValue()).isEqualByComparingTo("11100000");
        assertThat(recon.taxEffects().pph23PerReceipts()).isEqualByComparingTo("200000");
        assertThat(recon.taxEffects().pph23PerBupot()).isEqualByComparingTo("200000");
        assertThat(recon.taxEffects().pph23PerLedger()).isEqualByComparingTo("200000");
        assertThat(recon.taxEffects().pph23Difference()).isEqualByComparingTo("0");
        assertThat(recon.taxEffects().netOfPph23()).isEqualByComparingTo("10900000");
        assertThat(recon.exportedDpp()).isEqualByComparingTo("10000000");
        assertThat(recon.exportDifference()).isEqualByComparingTo("0");
        assertThat(exceptions(2144)).isEmpty();
    }

    @Test
    @DisplayName("Revenue with no faktur, faktur in another masa and revenue in another masa each get their own exact line")
    void timingAndMissingFakturLines() {
        Invoice clean = sent(date(2145, 5, 2), "11", "4.1.01:10000000");
        postRevenue(clean);
        faktur(clean, date(2145, 5, 2));

        Invoice noFaktur = sent(date(2145, 5, 3), "11", "4.1.01:5000000");
        postRevenue(noFaktur);

        Invoice fakturLater = sent(date(2145, 5, 4), "11", "4.1.01:3000000");
        postRevenue(fakturLater);
        faktur(fakturLater, date(2145, 6, 1));

        Invoice revenueEarlier = sent(date(2145, 4, 28), "11", "4.1.01:2000000");
        postRevenue(revenueEarlier);
        faktur(revenueEarlier, date(2145, 5, 1));

        Reconciliation may = recon(2145, 5);
        assertThat(may.bookedRevenue()).isEqualByComparingTo("18000000");
        assertThat(line(may, SalesTaxReconLine.REVENUE_NO_FAKTUR).amount()).isEqualByComparingTo("-5000000");
        assertThat(line(may, SalesTaxReconLine.FAKTUR_IN_OTHER_MASA).amount()).isEqualByComparingTo("-3000000");
        assertThat(line(may, SalesTaxReconLine.REVENUE_IN_OTHER_MASA).amount()).isEqualByComparingTo("2000000");
        assertThat(line(may, SalesTaxReconLine.OTHER_DIFFERENCE).amount()).isEqualByComparingTo("0");
        assertThat(may.sptDpp()).isEqualByComparingTo("12000000");
        assertThat(may.unreconciled()).isEqualByComparingTo("0");
        assertThat(line(may, SalesTaxReconLine.REVENUE_NO_FAKTUR).items()).singleElement()
                .satisfies(item -> assertThat(item.invoiceNumber()).isEqualTo(noFaktur.getInvoiceNumber()));

        // The export selects by transaction date: fakturLater (faktur in June) is in, revenueEarlier (booked in April) is out.
        assertThat(may.exportedDpp()).isEqualByComparingTo("13000000");
        assertThat(may.exportDifference()).isEqualByComparingTo("1000000");

        Reconciliation june = recon(2145, 6);
        assertThat(june.bookedRevenue()).isEqualByComparingTo("0");
        assertThat(line(june, SalesTaxReconLine.REVENUE_IN_OTHER_MASA).amount()).isEqualByComparingTo("3000000");
        assertThat(june.sptDpp()).isEqualByComparingTo("3000000");
    }

    @Test
    @DisplayName("Revenue booked gross of PPN or net of PPh 23 shows as PPN and PPh 23 lines; other gaps stay separate")
    void ppnAndPph23ReconcilingLines() {
        Transaction grossOfPpn = postedRevenueJournal(date(2146, 2, 1), "11100000");
        fakturOn(grossOfPpn, date(2146, 2, 1), "01", "10000000", "1100000");

        Transaction netOfPph23 = postedRevenueJournal(date(2146, 2, 2), "9800000");
        fakturOn(netOfPph23, date(2146, 2, 2), "01", "10000000", "0");
        bupotRowOn(netOfPph23, "BP" + next(), "200000");

        Transaction cashBasis = postedRevenueJournal(date(2146, 2, 3), "10900000");
        fakturOn(cashBasis, date(2146, 2, 3), "01", "10000000", "1100000");
        bupotRowOn(cashBasis, "BP" + next(), "200000");

        Transaction nilaiLain = postedRevenueJournal(date(2146, 2, 4), "12000000");
        fakturOn(nilaiLain, date(2146, 2, 4), "04", "11000000", "1320000");

        Transaction unexplained = postedRevenueJournal(date(2146, 2, 5), "4000000");
        fakturOn(unexplained, date(2146, 2, 5), "01", "4500000", "495000");

        // Interest income: no faktur expected, and no way to tell it apart from a missing one.
        postedRevenueJournal(date(2146, 2, 6), "700000");

        Reconciliation recon = recon(2146, 2);
        assertThat(recon.bookedRevenue()).isEqualByComparingTo("48500000");
        assertThat(line(recon, SalesTaxReconLine.PPN_IN_REVENUE).amount()).isEqualByComparingTo("-2200000");
        assertThat(line(recon, SalesTaxReconLine.PPH23_NETTED_FROM_REVENUE).amount()).isEqualByComparingTo("400000");
        assertThat(line(recon, SalesTaxReconLine.DPP_NILAI_LAIN).amount()).isEqualByComparingTo("-1000000");
        assertThat(line(recon, SalesTaxReconLine.OTHER_DIFFERENCE).amount()).isEqualByComparingTo("500000");
        assertThat(line(recon, SalesTaxReconLine.REVENUE_NO_FAKTUR).amount()).isEqualByComparingTo("-700000");
        assertThat(recon.sptDpp()).isEqualByComparingTo("45500000");
        assertThat(recon.unreconciled()).isEqualByComparingTo("0");

        assertThat(recon.taxEffects().ppnPerFaktur()).isEqualByComparingTo("4015000");
        assertThat(recon.taxEffects().ppnPerLedger()).isEqualByComparingTo("0");
        assertThat(recon.taxEffects().ppnDifference()).isEqualByComparingTo("4015000");
        assertThat(recon.taxEffects().pph23PerBupot()).isEqualByComparingTo("400000");
        assertThat(recon.taxEffects().pph23PerLedger()).isEqualByComparingTo("0");
        assertThat(recon.taxEffects().pph23Difference()).isEqualByComparingTo("400000");
    }

    @Test
    @DisplayName("A faktur on a posted transaction with no revenue counts as DPP with its own line; a draft faktur is not in the DPP")
    void fakturWithoutRevenueLine() {
        Transaction noRevenue = postedJournal(date(2147, 7, 1), "1.1.02", "2.1.03", "500000");
        fakturOn(noRevenue, date(2147, 7, 1), "01", "4545455", "500000");

        Invoice draftRevenue = sent(date(2147, 7, 2), "11", "4.1.01:9000000");
        faktur(draftRevenue, date(2147, 7, 2));

        Reconciliation recon = recon(2147, 7);
        assertThat(recon.bookedRevenue()).isEqualByComparingTo("0");
        assertThat(line(recon, SalesTaxReconLine.FAKTUR_NO_REVENUE).amount()).isEqualByComparingTo("4545455");
        assertThat(recon.sptDpp()).isEqualByComparingTo("4545455");
        assertThat(recon.taxEffects().ppnPerLedger()).isEqualByComparingTo("500000");
    }

    @Test
    @DisplayName("A faktur of a two-account invoice covers both revenue transactions; a closing entry stays out of booked revenue")
    void multiAccountInvoiceIsOneUnit() {
        Invoice invoice = sent(date(2147, 9, 5), "11", "4.1.01:6000000", "4.1.02:4000000");
        assertThat(revenueTransactions(invoice)).hasSize(2);
        postRevenue(invoice);
        faktur(invoice, date(2147, 9, 5));

        Reconciliation recon = recon(2147, 9);
        assertThat(recon.bookedRevenue()).isEqualByComparingTo("10000000");
        assertThat(recon.sptDpp()).isEqualByComparingTo("10000000");
        assertThat(recon.lines()).allSatisfy(l -> assertThat(l.amount()).isEqualByComparingTo("0"));
    }

    @Test
    @DisplayName("Unnumbered PPN row is not a faktur: it is outside the DPP but in the e-Faktur export, and the gap is reported")
    void unnumberedRowOutsideDpp() {
        Transaction revenue = postedRevenueJournal(date(2147, 11, 1), "2000000");
        TaxTransactionDetail row = new TaxTransactionDetail();
        row.setTaxType(TaxType.PPN_KELUARAN);
        row.setCounterpartyName("Pelanggan");
        row.setDpp(new BigDecimal("2000000"));
        row.setPpn(new BigDecimal("220000"));
        taxDetailService.save(revenue.getId(), row);

        Reconciliation recon = recon(2147, 11);
        assertThat(recon.sptDpp()).isEqualByComparingTo("0");
        assertThat(line(recon, SalesTaxReconLine.REVENUE_NO_FAKTUR).amount()).isEqualByComparingTo("-2000000");
        assertThat(recon.exportedDpp()).isEqualByComparingTo("2000000");
        assertThat(recon.exportDifference()).isEqualByComparingTo("2000000");
    }

    @Test
    @DisplayName("The filing register's SPT Masa PPN of the masa is shown; other masa and other types are not")
    void filingsOfTheMasa() {
        taxFilingService.create(filing(TaxFilingType.PPN, FILING_YEAR, 12), null);
        taxFilingService.create(filing(TaxFilingType.PPN, FILING_YEAR, 11), null);
        taxFilingService.create(filing(TaxFilingType.PPH21, FILING_YEAR, 12), null);

        assertThat(recon(FILING_YEAR, 12).filings()).singleElement().satisfies(f -> {
            assertThat(f.pembetulanNo()).isZero();
            assertThat(f.status()).isEqualTo(TaxFilingStatus.NOT_FILED);
        });
    }

    // ==================== exception: paid with no faktur ====================

    @Test
    @DisplayName("Paid with no faktur: paid and part-paid taxed invoices appear; with faktur, unpaid, untaxed, cancelled do not")
    void paidNoFaktur() {
        Invoice paid = sent(date(2148, 1, 5), "11", "4.1.01:1000000");
        pay(paid, date(2148, 1, 20), "1110000", "0");
        Invoice partPaid = sent(date(2148, 1, 6), "11", "4.1.01:2000000");
        pay(partPaid, date(2148, 1, 21), "500000", "0");
        Invoice blankRow = sent(date(2148, 1, 7), "11", "4.1.01:3000000");
        pay(blankRow, date(2148, 1, 22), "3330000", "0");
        TaxTransactionDetail unnumbered = new TaxTransactionDetail();
        unnumbered.setTaxType(TaxType.PPN_KELUARAN);
        unnumbered.setCounterpartyName("Pelanggan");
        unnumbered.setDpp(new BigDecimal("3000000"));
        unnumbered.setPpn(new BigDecimal("330000"));
        taxDetailService.save(revenueTransactions(blankRow).getFirst().getId(), unnumbered);

        Invoice markedPaid = sent(date(2148, 1, 8), "11", "4.1.01:4000000");
        invoiceService.markAsPaid(markedPaid.getId());
        markedPaid.setPaidAt(LocalDateTime.of(2148, 2, 3, 9, 0));
        invoiceRepository.save(markedPaid);

        Invoice voidedFaktur = sent(date(2148, 1, 9), "11", "4.1.01:5000000");
        postRevenue(voidedFaktur);
        faktur(voidedFaktur, date(2148, 1, 9));
        pay(voidedFaktur, date(2148, 1, 23), "5550000", "0");
        transactionService.voidTransaction(revenueTransactions(voidedFaktur).getFirst().getId(), VoidReason.INPUT_ERROR, "x", "tester");

        Invoice hasFaktur = sent(date(2148, 1, 10), "11", "4.1.01:6000000");
        faktur(hasFaktur, date(2148, 1, 10));
        postRevenue(hasFaktur);
        pay(hasFaktur, date(2148, 1, 24), "6660000", "0");

        Invoice unpaid = sent(date(2148, 1, 11), "11", "4.1.01:7000000");
        Invoice untaxed = sent(date(2148, 1, 12), null, "4.1.01:8000000");
        pay(untaxed, date(2148, 1, 25), "8000000", "0");
        Invoice cancelled = sent(date(2148, 1, 13), "11", "4.1.01:9000000");
        pay(cancelled, date(2148, 1, 26), "100000", "0");
        invoiceService.cancel(cancelled.getId());

        List<ChainIssue> found = exceptions(2148, SalesTaxExceptionType.PAID_NO_FAKTUR);
        assertThat(found).extracting(ChainIssue::invoiceNumber).containsExactly(
                paid.getInvoiceNumber(), partPaid.getInvoiceNumber(), blankRow.getInvoiceNumber(),
                voidedFaktur.getInvoiceNumber(), markedPaid.getInvoiceNumber());
        assertThat(found.get(0).amount()).isEqualByComparingTo("1110000");
        assertThat(found.get(0).date()).isEqualTo(date(2148, 1, 20));
        assertThat(found.get(4).date()).isEqualTo(date(2148, 2, 3));
        assertThat(found).extracting(ChainIssue::invoiceNumber).doesNotContain(
                hasFaktur.getInvoiceNumber(), unpaid.getInvoiceNumber(), untaxed.getInvoiceNumber(), cancelled.getInvoiceNumber());

        assertThat(chainService.findExceptions(date(2148, 1, 21), date(2148, 1, 22), SalesTaxExceptionType.PAID_NO_FAKTUR))
                .extracting(ChainIssue::invoiceNumber)
                .containsExactly(partPaid.getInvoiceNumber(), blankRow.getInvoiceNumber());
    }

    // ==================== exception: faktur with no booked revenue ====================

    @Test
    @DisplayName("Faktur with no booked revenue: draft revenue, voided revenue and a non-revenue transaction appear; posted revenue and a part-posted invoice do not")
    void fakturNoRevenue() {
        Invoice draftRevenue = sent(date(2149, 3, 1), "11", "4.1.01:1000000");
        String draftNumber = faktur(draftRevenue, date(2149, 3, 1)).getFakturNumber();

        Invoice voidedRevenue = sent(date(2149, 3, 2), "11", "4.1.01:2000000");
        postRevenue(voidedRevenue);
        String voidedNumber = faktur(voidedRevenue, date(2149, 3, 2)).getFakturNumber();
        transactionService.voidTransaction(revenueTransactions(voidedRevenue).getFirst().getId(), VoidReason.INPUT_ERROR, "x", "tester");

        Transaction noRevenue = postedJournal(date(2149, 3, 3), "1.1.02", "2.1.03", "330000");
        String noRevenueNumber = fakturOn(noRevenue, date(2149, 3, 3), "01", "3000000", "330000").getFakturNumber();

        Invoice posted = sent(date(2149, 3, 4), "11", "4.1.01:4000000");
        postRevenue(posted);
        String postedNumber = faktur(posted, date(2149, 3, 4)).getFakturNumber();

        Invoice partPosted = sent(date(2149, 3, 5), "11", "4.1.01:5000000", "4.1.02:6000000");
        String partPostedNumber = faktur(partPosted, date(2149, 3, 5)).getFakturNumber();
        transactionService.post(revenueTransactions(partPosted).get(1).getId(), "tester");

        Transaction purchase = postedJournal(date(2149, 3, 6), "1.1.25", "1.1.02", "110000");
        TaxTransactionDetail masukan = new TaxTransactionDetail();
        masukan.setTaxType(TaxType.PPN_MASUKAN);
        masukan.setFakturNumber("FM" + next());
        masukan.setFakturDate(date(2149, 3, 6));
        masukan.setCounterpartyName("Pemasok");
        masukan.setDpp(new BigDecimal("1000000"));
        masukan.setPpn(new BigDecimal("110000"));
        taxDetailService.save(purchase.getId(), masukan);

        List<ChainIssue> found = exceptions(2149, SalesTaxExceptionType.FAKTUR_NO_REVENUE);
        assertThat(found).extracting(ChainIssue::reference).containsExactly(draftNumber, voidedNumber, noRevenueNumber);
        assertThat(found.get(0).invoiceNumber()).isEqualTo(draftRevenue.getInvoiceNumber());
        assertThat(found.get(0).amount()).isEqualByComparingTo("1000000");
        assertThat(found.get(2).invoiceNumber()).isNull();
        assertThat(found.get(2).transactionId()).isEqualTo(noRevenue.getId());
        assertThat(found).extracting(ChainIssue::reference).doesNotContain(postedNumber, partPostedNumber);
    }

    // ==================== exception: duplicate faktur ====================

    @Test
    @DisplayName("Duplicate faktur: two on one invoice appear; one each on two invoices, or a replacement after a void, do not")
    void duplicateFaktur() {
        Invoice twice = sent(date(2150, 4, 1), "11", "4.1.01:1000000", "4.1.02:1000000");
        List<Transaction> revenue = revenueTransactions(twice);
        postRevenue(twice);
        String first = fakturOn(revenue.get(0), date(2150, 4, 1), "01", "1000000", "110000").getFakturNumber();
        String second = fakturOn(revenue.get(1), date(2150, 4, 15), "01", "1000000", "110000").getFakturNumber();

        Invoice onceA = sent(date(2150, 4, 2), "11", "4.1.01:2000000");
        postRevenue(onceA);
        faktur(onceA, date(2150, 4, 2));
        Invoice onceB = sent(date(2150, 4, 3), "11", "4.1.01:2000000");
        postRevenue(onceB);
        faktur(onceB, date(2150, 4, 3));

        Invoice replaced = sent(date(2150, 4, 4), "11", "4.1.01:3000000", "4.1.02:3000000");
        postRevenue(replaced);
        List<Transaction> replacedRevenue = revenueTransactions(replaced);
        fakturOn(replacedRevenue.get(0), date(2150, 4, 4), "01", "6000000", "660000");
        transactionService.voidTransaction(replacedRevenue.get(0).getId(), VoidReason.INPUT_ERROR, "x", "tester");
        fakturOn(replacedRevenue.get(1), date(2150, 4, 5), "01", "6000000", "660000");

        assertThat(exceptions(2150, SalesTaxExceptionType.DUPLICATE_FAKTUR)).singleElement().satisfies(e -> {
            assertThat(e.invoiceNumber()).isEqualTo(twice.getInvoiceNumber());
            assertThat(e.date()).isEqualTo(date(2150, 4, 15));
            assertThat(e.amount()).isEqualByComparingTo("2000000");
            assertThat(e.reference()).isEqualTo(List.of(first, second).stream().sorted().reduce((a, b) -> a + ", " + b).orElseThrow());
        });
    }

    @Test
    @DisplayName("Recording a second faktur through the invoice is refused and leaves the first in place")
    void secondFakturRefused() {
        Invoice invoice = sent(date(2150, 8, 1), "11", "4.1.01:1000000");
        String first = faktur(invoice, date(2150, 8, 1)).getFakturNumber();

        assertThatThrownBy(() -> faktur(invoice, date(2150, 8, 2)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(first);
        refresh();
        assertThat(chainService.getChain(invoice.getId()).fakturs()).hasSize(1);
    }

    // ==================== exception: missing bupot ====================

    @Test
    @DisplayName("Missing bupot: withheld with none, part-covered and voided appear with the open amount; covered, draft, none withheld and vendor rows do not")
    void missingBupot() {
        Invoice none = sent(date(2151, 6, 1), "11", "4.1.01:10000000");
        pay(none, date(2151, 6, 10), "11100000", "200000");

        Invoice part = sent(date(2151, 6, 2), "11", "4.1.01:10000000");
        InvoicePayment partReceipt = pay(part, date(2151, 6, 11), "11100000", "200000");
        bupot(part, partReceipt, date(2151, 6, 12), "150000");

        Invoice voided = sent(date(2151, 6, 3), "11", "4.1.01:10000000");
        InvoicePayment voidedReceipt = pay(voided, date(2151, 6, 13), "11100000", "200000");
        Transaction voidedBupot = bupot(voided, voidedReceipt, date(2151, 6, 14), "200000").getTransaction();
        transactionService.post(voidedBupot.getId(), "tester");
        transactionService.voidTransaction(voidedBupot.getId(), VoidReason.INPUT_ERROR, "x", "tester");

        Invoice covered = sent(date(2151, 6, 4), "11", "4.1.01:10000000");
        InvoicePayment coveredReceipt = pay(covered, date(2151, 6, 15), "11100000", "200000");
        transactionService.post(bupot(covered, coveredReceipt, date(2151, 6, 16), "200000").getTransaction().getId(), "tester");

        Invoice draftBupot = sent(date(2151, 6, 5), "11", "4.1.01:10000000");
        InvoicePayment draftReceipt = pay(draftBupot, date(2151, 6, 17), "11100000", "200000");
        bupot(draftBupot, draftReceipt, date(2151, 6, 18), "200000");

        Invoice unlinked = sent(date(2151, 6, 6), "11", "4.1.01:10000000");
        pay(unlinked, date(2151, 6, 19), "11100000", "200000");
        bupot(unlinked, null, date(2151, 6, 20), "200000");

        Invoice noneWithheld = sent(date(2151, 6, 7), "11", "4.1.01:10000000");
        pay(noneWithheld, date(2151, 6, 21), "11100000", "0");

        Vendor vendor = new Vendor();
        vendor.setCode("WH-" + next());
        vendor.setName("Vendor Jasa " + sequence);
        vendor.setAddress("Jl. Uji");
        vendor.setPayeeType(PayeeType.BADAN);
        vendor.setDefaultWithholding(WithholdingTreatment.PPH_23);
        vendor.setDefaultTaxObjectCode("24-104-01");
        vendor.setNpwp("0123456789012345");
        vendor = vendorService.create(vendor);
        WithholdingPayment vendorPayment = withholdingPaymentService.create(WithholdingPaymentService.CreatePayment.builder()
                .vendorId(vendor.getId()).treatment(WithholdingTreatment.PPH_23).paymentDate(date(2151, 6, 22))
                .grossAmount(new BigDecimal("5000000")).expenseAccountId(account("5.1.05").getId())
                .bankAccountId(account("1.1.02").getId()).description("Bayar vendor").createdBy("tester").build());
        withholdingPaymentService.post(vendorPayment.getId(), "tester");

        List<ChainIssue> found = exceptions(2151, SalesTaxExceptionType.MISSING_BUPOT);
        assertThat(found).extracting(ChainIssue::invoiceNumber)
                .containsExactly(none.getInvoiceNumber(), part.getInvoiceNumber(), voided.getInvoiceNumber());
        assertThat(found).extracting(ChainIssue::amount)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("200000"), new BigDecimal("50000"), new BigDecimal("200000"));
        assertThat(found).extracting(ChainIssue::date)
                .containsExactly(date(2151, 6, 10), date(2151, 6, 11), date(2151, 6, 13));
    }

    @Test
    @DisplayName("Missing bupot outside receipts: an unnumbered PPh 23 row on revenue and a Kredit Pajak debit with no row appear; numbering the row clears it")
    void missingBupotOnJournals() {
        Transaction revenueWithRow = postedRevenueJournal(date(2151, 9, 1), "9800000");
        TaxTransactionDetail row = bupotRowOn(revenueWithRow, null, "200000");
        Transaction bareCredit = postedJournal(date(2151, 9, 2), "1.1.26", "1.1.02", "300000");
        Transaction documentedCredit = postedJournal(date(2151, 9, 3), "1.1.26", "1.1.02", "400000");
        bupotRowOn(documentedCredit, "BP" + next(), "400000");

        List<ChainIssue> found = exceptions(2151, SalesTaxExceptionType.MISSING_BUPOT).stream()
                .filter(e -> e.date().getMonthValue() == 9).toList();
        assertThat(found).extracting(ChainIssue::transactionId).containsExactly(revenueWithRow.getId(), bareCredit.getId());
        assertThat(found.get(0).amount()).isEqualByComparingTo("200000");
        assertThat(found.get(1).amount()).isEqualByComparingTo("300000");

        row.setBupotNumber("BP" + next());
        taxDetailRepository.save(row);
        refresh();
        assertThat(exceptions(2151, SalesTaxExceptionType.MISSING_BUPOT).stream().filter(e -> e.date().getMonthValue() == 9))
                .extracting(ChainIssue::transactionId).containsExactly(bareCredit.getId());
    }

    // ==================== linking ====================

    @Test
    @DisplayName("The chain of one invoice links revenue, faktur, receipt and bupot, and lists its exceptions")
    void chainLinksEverything() {
        Invoice invoice = sent(date(2152, 2, 1), "11", "4.1.01:10000000");
        Transaction revenue = revenueTransactions(invoice).getFirst();
        transactionService.post(revenue.getId(), "tester");
        TaxTransactionDetail faktur = faktur(invoice, date(2152, 2, 2));
        InvoicePayment receipt = pay(invoice, date(2152, 2, 20), "11100000", "200000");
        TaxTransactionDetail bupot = bupot(invoice, receipt, date(2152, 2, 21), "150000");
        refresh();

        SalesTaxChainService.InvoiceChain chain = chainService.getChain(invoice.getId());

        assertThat(chain.invoiceNumber()).isEqualTo(invoice.getInvoiceNumber());
        assertThat(chain.bookedRevenue()).isEqualByComparingTo("10000000");
        assertThat(chain.paidAmount()).isEqualByComparingTo("11100000");
        assertThat(chain.transactions()).extracting(SalesTaxChainService.ChainTransaction::transactionId)
                .containsExactlyInAnyOrder(revenue.getId(), bupot.getTransaction().getId());
        assertThat(chain.fakturs()).singleElement().satisfies(f -> {
            assertThat(f.detailId()).isEqualTo(faktur.getId());
            assertThat(f.transactionId()).isEqualTo(revenue.getId());
            assertThat(f.fakturDate()).isEqualTo(date(2152, 2, 2));
            assertThat(f.dpp()).isEqualByComparingTo("10000000");
            assertThat(f.ppn()).isEqualByComparingTo("1100000");
        });
        assertThat(chain.receipts()).singleElement().satisfies(r -> {
            assertThat(r.paymentId()).isEqualTo(receipt.getId());
            assertThat(r.amount()).isEqualByComparingTo("11100000");
            assertThat(r.cashAmount()).isEqualByComparingTo("10900000");
            assertThat(r.pph23Withheld()).isEqualByComparingTo("200000");
            assertThat(r.bupotOutstanding()).isEqualByComparingTo("50000");
        });
        assertThat(chain.bupots()).singleElement().satisfies(b -> {
            assertThat(b.paymentId()).isEqualTo(receipt.getId());
            assertThat(b.taxAmount()).isEqualByComparingTo("150000");
            assertThat(b.transactionStatus()).isEqualTo(TransactionStatus.DRAFT);
        });
        assertThat(chain.exceptions()).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(SalesTaxExceptionType.MISSING_BUPOT);
            assertThat(e.amount()).isEqualByComparingTo("50000");
        });
    }

    @Test
    @DisplayName("A bupot is a DRAFT with no journal; posting debits Kredit Pajak and credits Piutang; it stays out of Bupot Unifikasi but counts as PPh 23 credit")
    void bupotDraftAndPosting() {
        Invoice invoice = sent(date(2152, 5, 1), "11", "4.1.01:10000000");
        InvoicePayment receipt = pay(invoice, date(2152, 5, 20), "11100000", "200000");
        Transaction draft = bupot(invoice, receipt, date(2152, 5, 21), "200000").getTransaction();

        assertThat(draft.getStatus()).isEqualTo(TransactionStatus.DRAFT);
        assertThat(draft.getSourceDocumentType()).isEqualTo("INVOICE");
        assertThat(draft.getSourceDocumentId()).isEqualTo(invoice.getId());
        assertThat(transactionRepository.findById(draft.getId()).orElseThrow().getJournalEntries()).isEmpty();

        Transaction posted = transactionService.post(draft.getId(), "tester");
        assertThat(posted.getJournalEntries()).hasSize(2).allSatisfy(entry -> {
            if ("1.1.26".equals(entry.getAccount().getAccountCode())) {
                assertThat(entry.getDebitAmount()).isEqualByComparingTo("200000");
            } else {
                assertThat(entry.getAccount().getId()).isEqualTo(companyConfigService.getConfig().getReceivableAccount().getId());
                assertThat(entry.getCreditAmount()).isEqualByComparingTo("200000");
            }
        });

        LocalDate start = date(2152, 5, 1);
        LocalDate end = date(2152, 5, 31);
        assertThat(taxDetailRepository.findEBupotUnifikasiByDateRange(start, end)).isEmpty();
        assertThat(taxDetailRepository.findPPh23ByDateRange(start, end)).hasSize(1);
    }

    @Test
    @DisplayName("Refusals: faktur on a draft invoice, bupot on another invoice's receipt, withheld above the payment, missing fields")
    void refusals() {
        Invoice draft = draftInvoice(date(2152, 7, 1), "11", "4.1.01:1000000");
        assertThatThrownBy(() -> faktur(draft, date(2152, 7, 1)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Draf");

        Invoice a = sent(date(2152, 7, 2), "11", "4.1.01:1000000");
        Invoice b = sent(date(2152, 7, 3), "11", "4.1.01:1000000");
        InvoicePayment receiptOfB = pay(b, date(2152, 7, 4), "1110000", "20000");
        assertThatThrownBy(() -> bupot(a, receiptOfB, date(2152, 7, 5), "20000"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(a.getInvoiceNumber());

        assertThatThrownBy(() -> pay(a, date(2152, 7, 6), "1000", "2000"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("PPh 23");

        assertThatThrownBy(() -> linkService.recordFaktur(InvoiceTaxLinkService.RecordFaktur.builder()
                .invoiceId(a.getId()).fakturDate(date(2152, 7, 2)).transactionCode("01").dpp(BigDecimal.TEN).ppn(BigDecimal.ONE).build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Nomor faktur");
    }

    // ==================== helpers ====================

    private Reconciliation recon(int year, int month) {
        refresh();
        return chainService.getReconciliation(year, month);
    }

    private List<ChainIssue> exceptions(int year) {
        refresh();
        return chainService.findExceptions(date(year, 1, 1), date(year, 12, 31));
    }

    private List<ChainIssue> exceptions(int year, SalesTaxExceptionType type) {
        refresh();
        return chainService.findExceptions(date(year, 1, 1), date(year, 12, 31), type);
    }

    private ReconLine line(Reconciliation recon, SalesTaxReconLine type) {
        return recon.lines().stream().filter(l -> l.type() == type).findFirst().orElseThrow();
    }

    /** Payments are saved without touching Invoice.payments; reports read after a flush and clear, like a new request. */
    private void refresh() {
        entityManager.flush();
        entityManager.clear();
    }

    private LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    private String next() {
        sequence++;
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private TaxFiling filing(TaxFilingType type, int year, int month) {
        TaxFiling filing = new TaxFiling();
        filing.setTaxType(type);
        filing.setPeriodYear(year);
        filing.setPeriodMonth(month);
        filing.setStatus(TaxFilingStatus.NOT_FILED);
        return filing;
    }

    private Invoice draftInvoice(LocalDate date, String taxRate, String... lines) {
        Client client = new Client();
        client.setCode("STC-" + next());
        client.setName("Pelanggan " + client.getCode());
        client = clientService.create(client);

        List<InvoiceLine> invoiceLines = new ArrayList<>();
        for (String spec : lines) {
            String[] parts = spec.split(":");
            Product product = new Product();
            product.setCode("STC-" + next());
            product.setName("Jasa " + parts[0]);
            product.setUnit("paket");
            product.setTrackInventory(false);
            product.setSalesAccount(account(parts[0]));
            InvoiceLine line = new InvoiceLine();
            line.setProduct(productRepository.save(product));
            line.setDescription("Jasa " + parts[0]);
            line.setQuantity(BigDecimal.ONE);
            line.setUnitPrice(new BigDecimal(parts[1]));
            line.setTaxRate(taxRate == null ? null : new BigDecimal(taxRate));
            line.calculateAmounts();
            invoiceLines.add(line);
        }
        Invoice invoice = new Invoice();
        invoice.setClient(client);
        invoice.setInvoiceNumber("STC-" + next());
        invoice.setInvoiceDate(date);
        invoice.setDueDate(date.plusDays(30));
        return invoiceService.create(invoice, invoiceLines);
    }

    private Invoice sent(LocalDate date, String taxRate, String... lines) {
        return invoiceService.send(draftInvoice(date, taxRate, lines).getId());
    }

    private List<Transaction> revenueTransactions(Invoice invoice) {
        return transactionRepository.findBySourceDocumentTypeAndSourceDocumentIdOrderByTransactionDateAsc("INVOICE", invoice.getId()).stream()
                .filter(t -> t.getDescription().startsWith("Invoice "))
                .sorted(java.util.Comparator.comparing(Transaction::getDescription))
                .toList();
    }

    private void postRevenue(Invoice invoice) {
        revenueTransactions(invoice).forEach(t -> transactionService.post(t.getId(), "tester"));
    }

    private TaxTransactionDetail faktur(Invoice invoice, LocalDate date) {
        return linkService.recordFaktur(InvoiceTaxLinkService.RecordFaktur.builder()
                .invoiceId(invoice.getId()).fakturNumber("F" + next()).fakturDate(date).transactionCode("01")
                .dpp(invoice.getAmount()).ppn(invoice.getTaxAmount()).build());
    }

    private TaxTransactionDetail fakturOn(Transaction transaction, LocalDate date, String code, String dpp, String ppn) {
        TaxTransactionDetail detail = new TaxTransactionDetail();
        detail.setTaxType(TaxType.PPN_KELUARAN);
        detail.setFakturNumber("F" + next());
        detail.setFakturDate(date);
        detail.setTransactionCode(code);
        detail.setDpp(new BigDecimal(dpp));
        detail.setPpn(new BigDecimal(ppn));
        detail.setCounterpartyName("Pelanggan");
        return taxDetailService.save(transaction.getId(), detail);
    }

    private TaxTransactionDetail bupotRowOn(Transaction transaction, String number, String tax) {
        TaxTransactionDetail detail = new TaxTransactionDetail();
        detail.setTaxType(TaxType.PPH_23);
        detail.setBupotNumber(number);
        detail.setGrossAmount(new BigDecimal(tax).multiply(new BigDecimal("50")));
        detail.setTaxRate(new BigDecimal("2"));
        detail.setTaxAmount(new BigDecimal(tax));
        detail.setCounterpartyName("Pelanggan");
        return taxDetailService.save(transaction.getId(), detail);
    }

    private InvoicePayment pay(Invoice invoice, LocalDate date, String amount, String withheld) {
        InvoicePayment payment = new InvoicePayment();
        payment.setPaymentDate(date);
        payment.setAmount(new BigDecimal(amount));
        payment.setPph23Withheld(new BigDecimal(withheld));
        payment.setPaymentMethod(PaymentMethod.TRANSFER);
        invoiceService.recordPayment(invoice.getId(), payment);
        return payment;
    }

    private TaxTransactionDetail bupot(Invoice invoice, InvoicePayment receipt, LocalDate date, String tax) {
        return linkService.recordBupot(InvoiceTaxLinkService.RecordBupot.builder()
                .invoiceId(invoice.getId()).paymentId(receipt == null ? null : receipt.getId()).bupotNumber("BP" + next())
                .bupotDate(date).taxObjectCode("24-104-01").grossAmount(new BigDecimal(tax).multiply(new BigDecimal("50")))
                .taxRate(new BigDecimal("2")).taxAmount(new BigDecimal(tax)).createdBy("tester").build());
    }

    private Transaction postedRevenueJournal(LocalDate date, String amount) {
        return postedJournal(date, "1.1.02", "4.1.01", amount);
    }

    private Transaction postedJournal(LocalDate date, String debit, String credit, String amount) {
        BigDecimal value = new BigDecimal(amount);
        UUID id = transactionApiService.createJournalEntry(new JournalEntryRequest(date, "Jurnal " + next(), null, List.of(
                new JournalEntryRequest.JournalLineRequest(account(debit).getId(), value, BigDecimal.ZERO),
                new JournalEntryRequest.JournalLineRequest(account(credit).getId(), BigDecimal.ZERO, value))), "tester").transactionId();
        return transactionService.post(id, "tester");
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Account missing from integration DB: " + code));
    }
}
