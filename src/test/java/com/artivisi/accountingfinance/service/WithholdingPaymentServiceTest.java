package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.dto.JournalEntryRequest;
import com.artivisi.accountingfinance.entity.Bill;
import com.artivisi.accountingfinance.entity.BillLine;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.entity.WithholdingDeposit;
import com.artivisi.accountingfinance.entity.WithholdingPayment;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.BillStatus;
import com.artivisi.accountingfinance.enums.NormalBalance;
import com.artivisi.accountingfinance.enums.PayeeType;
import com.artivisi.accountingfinance.enums.PaymentMethod;
import com.artivisi.accountingfinance.enums.TaxType;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.enums.VoidReason;
import com.artivisi.accountingfinance.enums.WithholdingDepositStatus;
import com.artivisi.accountingfinance.enums.WithholdingRateBasis;
import com.artivisi.accountingfinance.enums.WithholdingTreatment;
import com.artivisi.accountingfinance.repository.BillPaymentRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Payment flow, deposits, report and export against the integration DB. V800 chart: Bank BCA 1.1.02,
 * Hutang PPh 21 2.1.20, Hutang PPh 23 2.1.21, Hutang PPh 26 2.1.25. Reserved test years 2132-2141.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "app.encryption.key=Kea3OzaaPR89QOebS1b427C8xxRoztOft8rlSEL3W7k=")
@Transactional
@WithMockUser(username = "tester")
@DisplayName("Withholding payment flow")
class WithholdingPaymentServiceTest {

    private static final String NPWP = "0123456789012345";
    private static final String NIK = "3174012345678901";

    @Autowired private WithholdingPaymentService paymentService;
    @Autowired private WithholdingDepositService depositService;
    @Autowired private WithholdingQueryService queryService;
    @Autowired private VendorService vendorService;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionApiService transactionApiService;
    @Autowired private TaxTransactionDetailService taxDetailService;
    @Autowired private CoretaxExportService coretaxExportService;
    @Autowired private BillService billService;
    @Autowired private BillPaymentRepository billPaymentRepository;
    @Autowired private CompanyConfigService companyConfigService;
    @Autowired private ChartOfAccountService chartOfAccountService;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private EntityManager entityManager;

    private int sequence;

    // ==================== payment flow ====================

    @Test
    @DisplayName("Payment is a DRAFT with no journal until posted; posting credits Hutang PPh 21 and the bank")
    void draftUntilPosted() {
        Vendor payee = individual(NIK, null);
        BigDecimal payableBefore = balance("2.1.20");

        WithholdingPayment payment = pay(payee, null, "10000000", LocalDate.of(2132, 3, 10));

        assertThat(payment.getTaxAmount()).isEqualByComparingTo("250000");
        assertThat(payment.getNetAmount()).isEqualByComparingTo("9750000");
        assertThat(payment.getDppAmount()).isEqualByComparingTo("5000000");
        Transaction draft = transactionService.findById(payment.getTransaction().getId());
        assertThat(draft.getStatus()).isEqualTo(TransactionStatus.DRAFT);
        assertThat(draft.getJournalEntries()).isEmpty();
        assertThat(balance("2.1.20")).isEqualByComparingTo(payableBefore);
        assertThat(queryService.getMasaStatus(2132, 3, LocalDate.of(2132, 4, 1)).get(0).withheld()).isEqualByComparingTo("0");
        assertThat(queryService.getMasaStatus(2132, 3, LocalDate.of(2132, 4, 1)).get(0).draftWithheld()).isEqualByComparingTo("250000");

        paymentService.post(payment.getId(), "tester");
        entityManager.flush();
        entityManager.clear();

        Transaction posted = transactionService.findByIdWithJournalEntries(draft.getId());
        assertThat(posted.getStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(lines(posted)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "5.1.05", new BigDecimal("10000000"),
                "1.1.02", new BigDecimal("-9750000"),
                "2.1.20", new BigDecimal("-250000")));
        assertThat(queryService.getMasaStatus(2132, 3, LocalDate.of(2132, 4, 1)).get(0).withheld()).isEqualByComparingTo("250000");
    }

    @Test
    @DisplayName("Posting through the generic transaction service counts too; tax detail stays the payee's")
    void genericPostKeepsPayeeDetail() {
        Vendor payee = individual(null, NPWP);
        WithholdingPayment payment = pay(payee, null, "10000000", LocalDate.of(2132, 4, 10));

        transactionService.post(payment.getTransaction().getId(), "tester");

        List<TaxTransactionDetail> details = taxDetailService.findByTransactionId(payment.getTransaction().getId());
        assertThat(details).hasSize(1);
        TaxTransactionDetail detail = details.get(0);
        assertThat(detail.getTaxType()).isEqualTo(TaxType.PPH_21);
        assertThat(detail.getCounterpartyName()).isEqualTo(payee.getName());
        assertThat(detail.getCounterpartyIdType()).isEqualTo("TIN");
        assertThat(detail.getCounterpartyNpwp()).isEqualTo(NPWP);
        assertThat(detail.getGrossAmount()).isEqualByComparingTo("10000000");
        assertThat(detail.getDpp()).isEqualByComparingTo("5000000");
        assertThat(detail.getTaxAmount()).isEqualByComparingTo("250000");
        assertThat(detail.getTaxRate()).isEqualByComparingTo("5.00");
        assertThat(detail.getTaxObjectCode()).isEqualTo("21-100-09");
    }

    @Test
    @DisplayName("Without NPWP or NIK: 20% higher; blank strings count as absent")
    void noIdIsTwentyPercentHigher() {
        Vendor payee = individual(null, null);
        WithholdingPayment payment = pay(payee, null, "10000000", LocalDate.of(2132, 5, 10));
        assertThat(payment.getTaxAmount()).isEqualByComparingTo("300000");
        assertThat(payment.getRateBasis()).isEqualTo(WithholdingRateBasis.PPH21_PASAL_17_NO_ID);
        assertThat(payment.getTaxDetail().getCounterpartyIdType()).isEqualTo("NONE");
        assertThat(payment.isHasTaxId()).isFalse();

        Vendor blank = individual("  ", "  ");
        assertThat(pay(blank, null, "10000000", LocalDate.of(2132, 5, 11)).getTaxAmount()).isEqualByComparingTo("300000");

        Vendor nikOnly = individual(NIK, null);
        WithholdingPayment withNik = pay(nikOnly, null, "10000000", LocalDate.of(2132, 5, 12));
        assertThat(withNik.getTaxAmount()).isEqualByComparingTo("250000");
        assertThat(withNik.getTaxDetail().getCounterpartyIdType()).isEqualTo("NIK");
        assertThat(withNik.getTaxDetail().getCounterpartyNik()).isEqualTo(NIK);
    }

    @Test
    @DisplayName("Brackets accumulate per payee per masa and restart in the next masa; other payees do not share them")
    void bracketsPerPayeePerMasa() {
        Vendor payee = individual(NIK, null);
        assertThat(pay(payee, null, "100000000", LocalDate.of(2133, 1, 5)).getTaxAmount()).isEqualByComparingTo("2500000");
        assertThat(pay(payee, null, "100000000", LocalDate.of(2133, 1, 25)).getTaxAmount()).isEqualByComparingTo("6500000");
        assertThat(pay(payee, null, "100000000", LocalDate.of(2133, 2, 5)).getTaxAmount()).isEqualByComparingTo("2500000");
        assertThat(pay(individual(NIK, null), null, "100000000", LocalDate.of(2133, 1, 26)).getTaxAmount()).isEqualByComparingTo("2500000");
    }

    @Test
    @DisplayName("Cancelling a draft removes its rows and moves later drafts down the brackets; posted tax stays")
    void cancelRecalculatesDrafts() {
        Vendor payee = individual(NIK, null);
        WithholdingPayment first = pay(payee, null, "100000000", LocalDate.of(2133, 3, 5));
        WithholdingPayment second = pay(payee, null, "100000000", LocalDate.of(2133, 3, 6));
        UUID firstTransaction = first.getTransaction().getId();
        assertThat(second.getTaxAmount()).isEqualByComparingTo("6500000");

        paymentService.cancel(first.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Transaction.class, firstTransaction)).isNull();
        WithholdingPayment recalculated = queryService.findPayment(second.getId());
        assertThat(recalculated.getTaxAmount()).isEqualByComparingTo("2500000");
        assertThat(recalculated.getNetAmount()).isEqualByComparingTo("97500000");
        assertThat(recalculated.getTaxDetail().getTaxAmount()).isEqualByComparingTo("2500000");

        paymentService.post(second.getId(), "tester");
        entityManager.flush();
        entityManager.clear();
        Transaction posted = transactionService.findByIdWithJournalEntries(second.getTransaction().getId());
        assertThat(lines(posted)).containsEntry("2.1.20", new BigDecimal("-2500000")).containsEntry("1.1.02", new BigDecimal("-97500000"));

        assertThatThrownBy(() -> paymentService.cancel(second.getId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("void transaksi");
    }

    @Test
    @DisplayName("A draft cannot be cancelled once a later payment in the masa, computed with it as prior gross, is posted")
    void cancelRefusedAfterLaterPosted() {
        Vendor payee = individual(NIK, null);
        WithholdingPayment first = pay(payee, null, "100000000", LocalDate.of(2140, 3, 5));
        WithholdingPayment second = pay(payee, null, "100000000", LocalDate.of(2140, 3, 6));
        assertThat(second.getTaxAmount()).isEqualByComparingTo("6500000");
        paymentService.post(second.getId(), "tester");
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> paymentService.cancel(first.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Draf ini tidak dapat dibatalkan: pembayaran lebih baru ke penerima ini pada masa 2140-03 sudah "
                        + "diposting dengan bruto draf ini dihitung sebagai penghasilan sebelumnya. "
                        + "Void pembayaran tersebut lebih dulu, lalu batalkan draf ini.");
        entityManager.clear();
        assertThat(queryService.findPayment(first.getId()).getTransaction().getStatus()).isEqualTo(TransactionStatus.DRAFT);

        // An earlier posted payment does not block: it was computed without this draft
        WithholdingPayment third = pay(payee, null, "10000000", LocalDate.of(2140, 3, 7));
        paymentService.cancel(third.getId());
        entityManager.flush();
    }

    @Test
    @DisplayName("Generic edit, delete and account-mapping routes refuse a withholding draft")
    void genericRoutesRefuseDraft() {
        Vendor payee = individual(NIK, null);
        WithholdingPayment payment = pay(payee, null, "10000000", LocalDate.of(2133, 4, 5));
        UUID id = payment.getTransaction().getId();
        Transaction draft = transactionService.findById(id);
        Transaction data = new Transaction();
        data.setAmount(new BigDecimal("1"));

        assertThatThrownBy(() -> transactionService.delete(id)).hasMessageContaining("Pemotongan PPh");
        assertThatThrownBy(() -> transactionService.update(draft, data)).hasMessageContaining("Pemotongan PPh");
        assertThatThrownBy(() -> transactionService.replaceAccountMappings(draft, Map.of())).hasMessageContaining("Pemotongan PPh");
        assertThatThrownBy(() -> transactionService.assertEditable(id)).hasMessageContaining("Pemotongan PPh");
    }

    @Test
    @DisplayName("Voiding a PPh 21 payment with a later payment in the masa is refused; the latest can be voided and frees its bracket")
    void voidOrder() {
        Vendor payee = individual(NIK, null);
        WithholdingPayment first = pay(payee, null, "100000000", LocalDate.of(2133, 5, 5));
        paymentService.post(first.getId(), "tester");
        WithholdingPayment second = pay(payee, null, "100000000", LocalDate.of(2133, 5, 6));
        paymentService.post(second.getId(), "tester");

        assertThatThrownBy(() -> transactionService.voidTransaction(first.getTransaction().getId(), VoidReason.INPUT_ERROR, "x", "tester"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("lebih baru");

        transactionService.voidTransaction(second.getTransaction().getId(), VoidReason.INPUT_ERROR, "x", "tester");
        assertThat(queryService.getMasaStatus(2133, 5, LocalDate.of(2133, 6, 1)).get(0).withheld()).isEqualByComparingTo("2500000");
        assertThat(pay(payee, null, "100000000", LocalDate.of(2133, 5, 7)).getTaxAmount()).isEqualByComparingTo("6500000");
    }

    @Test
    @DisplayName("PPh 23 credits Hutang PPh 23 at the configured rate, doubled without an ID")
    void pph23() {
        Vendor withId = payee(PayeeType.BADAN, WithholdingTreatment.PPH_23, "24-104-03", null, NPWP);
        WithholdingPayment payment = pay(withId, null, "10000000", LocalDate.of(2134, 1, 10));
        assertThat(payment.getTaxAmount()).isEqualByComparingTo("200000");
        paymentService.post(payment.getId(), "tester");
        entityManager.flush();
        entityManager.clear();
        assertThat(lines(transactionService.findByIdWithJournalEntries(payment.getTransaction().getId())))
                .containsEntry("2.1.21", new BigDecimal("-200000")).containsEntry("1.1.02", new BigDecimal("-9800000"));

        Vendor noId = payee(PayeeType.BADAN, WithholdingTreatment.PPH_23, "24-104-03", null, null);
        assertThat(pay(noId, null, "10000000", LocalDate.of(2134, 1, 11)).getTaxAmount()).isEqualByComparingTo("400000");
    }

    @Test
    @DisplayName("PPh 26 credits Hutang PPh 26; treaty rate with a valid DGT; 0% treaty posts no tax line")
    void pph26() {
        Vendor general = foreign(null, null, null);
        WithholdingPayment payment = pay(general, null, "10000000", LocalDate.of(2134, 2, 10));
        assertThat(payment.getTaxAmount()).isEqualByComparingTo("2000000");
        assertThat(payment.getTaxDetail().getCounterpartyIdType()).isEqualTo("FOREIGN");
        assertThat(payment.getTaxDetail().getCounterpartyCountry()).isEqualTo("SG");
        assertThat(payment.getTaxDetail().getCounterpartyIdNumber()).isEqualTo("TIN-SG-1");
        paymentService.post(payment.getId(), "tester");
        entityManager.flush();
        entityManager.clear();
        assertThat(lines(transactionService.findByIdWithJournalEntries(payment.getTransaction().getId())))
                .containsEntry("2.1.25", new BigDecimal("-2000000")).containsEntry("1.1.02", new BigDecimal("-8000000"));

        Vendor treaty = foreign("10", "DGT-9", LocalDate.of(2134, 12, 31));
        WithholdingPayment reduced = pay(treaty, null, "10000000", LocalDate.of(2134, 2, 11));
        assertThat(reduced.getTaxAmount()).isEqualByComparingTo("1000000");
        assertThat(reduced.getRateBasis()).isEqualTo(WithholdingRateBasis.PPH26_TREATY);
        assertThat(reduced.getTaxDetail().getDgtFormNumber()).isEqualTo("DGT-9");

        WithholdingPayment expired = pay(treaty, null, "10000000", LocalDate.of(2135, 1, 2));
        assertThat(expired.getTaxAmount()).isEqualByComparingTo("2000000");
        assertThat(expired.getRateBasis()).isEqualTo(WithholdingRateBasis.PPH26_TREATY_DGT_INVALID);
        assertThat(expired.getTaxDetail().getDgtFormNumber()).isNull();

        Vendor zero = foreign("0", "DGT-0", LocalDate.of(2134, 12, 31));
        WithholdingPayment exempt = pay(zero, null, "10000000", LocalDate.of(2134, 2, 12));
        assertThat(exempt.getTaxAmount()).isEqualByComparingTo("0");
        paymentService.post(exempt.getId(), "tester");
        entityManager.flush();
        entityManager.clear();
        assertThat(lines(transactionService.findByIdWithJournalEntries(exempt.getTransaction().getId())))
                .doesNotContainKey("2.1.25").containsEntry("1.1.02", new BigDecimal("-10000000"));
    }

    @Test
    @DisplayName("Rejected: unclassified vendor, treatment not valid for the payee type, missing treatment or object code, wrong accounts")
    void validation() {
        Vendor unclassified = new Vendor();
        unclassified.setCode("WHV-" + UUID.randomUUID().toString().substring(0, 8));
        unclassified.setName("Belum Klasifikasi");
        Vendor savedUnclassified = vendorService.create(unclassified);
        assertThatThrownBy(() -> pay(savedUnclassified, WithholdingTreatment.PPH_23, "1000", LocalDate.of(2135, 2, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Jenis penerima");

        Vendor individual = individual(NIK, null);
        assertThatThrownBy(() -> pay(individual, WithholdingTreatment.PPH_26, "1000", LocalDate.of(2135, 2, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tidak berlaku");

        Vendor noDefault = payee(PayeeType.ORANG_PRIBADI, null, null, NIK, null);
        assertThatThrownBy(() -> pay(noDefault, null, "1000", LocalDate.of(2135, 2, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Perlakuan pajak");

        assertThatThrownBy(() -> paymentService.create(command(individual, null, "1000", LocalDate.of(2135, 2, 1))
                .taxObjectCode("24-104-03").build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("24-104-03");
        assertThatThrownBy(() -> paymentService.create(command(individual, null, "1000", LocalDate.of(2135, 2, 1))
                .bankAccountId(account("5.1.05").getId()).build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Akun bank");
        assertThatThrownBy(() -> paymentService.create(command(individual, null, "1000", LocalDate.of(2135, 2, 1))
                .expenseAccountId(account("1.1.02").getId()).build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Akun beban");
        assertThatThrownBy(() -> paymentService.create(command(individual, null, "0", LocalDate.of(2135, 2, 1)).build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lebih dari nol");
    }

    @Test
    @DisplayName("Payee master: NIK and NPWP are encrypted at rest; format and combinations are validated")
    void payeeMaster() {
        Vendor payee = individual(NIK, NPWP);
        entityManager.flush();
        Object[] raw = (Object[]) entityManager.createNativeQuery("SELECT nik, npwp FROM vendors WHERE id = :id")
                .setParameter("id", payee.getId()).getSingleResult();
        assertThat((String) raw[0]).startsWith("ENC:").doesNotContain(NIK);
        assertThat((String) raw[1]).startsWith("ENC:").doesNotContain(NPWP);
        entityManager.clear();
        Vendor reloaded = vendorService.findById(payee.getId());
        assertThat(reloaded.getNik()).isEqualTo(NIK);
        assertThat(reloaded.getNpwp()).isEqualTo(NPWP);

        WithholdingPayment payment = pay(reloaded, null, "1000000", LocalDate.of(2135, 3, 1));
        entityManager.flush();
        String detailNik = (String) entityManager.createNativeQuery("SELECT counterparty_nik FROM tax_transaction_details WHERE id = :id")
                .setParameter("id", payment.getTaxDetail().getId()).getSingleResult();
        assertThat(detailNik).startsWith("ENC:");

        Vendor badNik = newVendor(PayeeType.ORANG_PRIBADI, null, null, "123", null);
        assertThatThrownBy(() -> vendorService.create(badNik)).hasMessageContaining("NIK harus 16 digit");
        Vendor wrongTreatment = newVendor(PayeeType.BADAN, WithholdingTreatment.PPH_26, null, null, null);
        assertThatThrownBy(() -> vendorService.create(wrongTreatment)).hasMessageContaining("tidak berlaku");
        Vendor noCountry = newVendor(PayeeType.LUAR_NEGERI, WithholdingTreatment.PPH_26, null, null, null);
        assertThatThrownBy(() -> vendorService.create(noCountry)).hasMessageContaining("Kode negara");
        Vendor treatyDomestic = newVendor(PayeeType.BADAN, null, null, null, null);
        treatyDomestic.setTreatyRate(BigDecimal.TEN);
        assertThatThrownBy(() -> vendorService.create(treatyDomestic)).hasMessageContaining("P3B");
        Vendor wrongObject = newVendor(PayeeType.BADAN, WithholdingTreatment.PPH_23, "21-100-09", null, null);
        assertThatThrownBy(() -> vendorService.create(wrongObject)).hasMessageContaining("21-100-09");
    }

    @Test
    @DisplayName("A missing Hutang PPh account fails loudly")
    void missingPayableAccountFails() {
        ChartOfAccount pph23 = account("2.1.21");
        pph23.setAccountName("Dinamai Ulang");
        accountRepository.saveAndFlush(pph23);
        Vendor payee = payee(PayeeType.BADAN, WithholdingTreatment.PPH_23, "24-104-03", null, NPWP);
        assertThatThrownBy(() -> pay(payee, null, "1000", LocalDate.of(2135, 4, 1)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("'Hutang PPh 23', ditemukan 0");
    }

    // ==================== bills ====================

    @Test
    @DisplayName("Bill payment debits the payable; BillPayment exists only after posting; void reverses it")
    void billPayment() {
        CompanyConfig config = companyConfigService.getConfig();
        config.setPayableAccount(account("2.1.01"));
        config.setInputTaxAccount(account("1.1.25"));
        Vendor payee = payee(PayeeType.BADAN, WithholdingTreatment.PPH_23, "24-104-03", null, NPWP);
        Bill bill = approvedBill(payee, "11000000");

        WithholdingPayment payment = paymentService.create(command(payee, null, "10000000", LocalDate.of(2135, 6, 10))
                .expenseAccountId(null).billId(bill.getId()).settledAmount(new BigDecimal("11000000"))
                .paymentMethod(PaymentMethod.TRANSFER).build());

        assertThat(payment.getTaxAmount()).isEqualByComparingTo("200000");
        assertThat(payment.getNetAmount()).isEqualByComparingTo("10800000");
        assertThat(billPaymentRepository.findByBillIdOrderByPaymentDateAsc(bill.getId())).isEmpty();
        assertThat(billService.findById(bill.getId()).getStatus()).isEqualTo(BillStatus.APPROVED);

        assertThatThrownBy(() -> paymentService.create(command(payee, null, "1000", LocalDate.of(2135, 6, 11))
                .expenseAccountId(null).billId(bill.getId()).settledAmount(new BigDecimal("1000"))
                .paymentMethod(PaymentMethod.TRANSFER).build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("melebihi total tagihan");

        paymentService.post(payment.getId(), "tester");
        entityManager.flush();

        assertThat(billService.findById(bill.getId()).getStatus()).isEqualTo(BillStatus.PAID);
        assertThat(billPaymentRepository.findByBillIdOrderByPaymentDateAsc(bill.getId())).singleElement()
                .satisfies(p -> assertThat(p.getAmount()).isEqualByComparingTo("11000000"));
        entityManager.clear();
        assertThat(lines(transactionService.findByIdWithJournalEntries(payment.getTransaction().getId()))).containsExactlyInAnyOrderEntriesOf(Map.of(
                "2.1.01", new BigDecimal("11000000"),
                "1.1.02", new BigDecimal("-10800000"),
                "2.1.21", new BigDecimal("-200000")));

        transactionService.voidTransaction(payment.getTransaction().getId(), VoidReason.INPUT_ERROR, "x", "tester");
        entityManager.flush();
        assertThat(billPaymentRepository.findByBillIdOrderByPaymentDateAsc(bill.getId())).isEmpty();
        assertThat(billService.findById(bill.getId()).getStatus()).isEqualTo(BillStatus.APPROVED);
    }

    @Test
    @DisplayName("Another vendor's bill and a bill without settlement data are rejected")
    void billValidation() {
        CompanyConfig config = companyConfigService.getConfig();
        config.setPayableAccount(account("2.1.01"));
        config.setInputTaxAccount(account("1.1.25"));
        Vendor owner = payee(PayeeType.BADAN, WithholdingTreatment.PPH_23, "24-104-03", null, NPWP);
        Vendor other = payee(PayeeType.BADAN, WithholdingTreatment.PPH_23, "24-104-03", null, NPWP);
        Bill bill = approvedBill(owner, "1000000");

        assertThatThrownBy(() -> paymentService.create(command(other, null, "1000", LocalDate.of(2135, 7, 1))
                .expenseAccountId(null).billId(bill.getId()).settledAmount(new BigDecimal("1000"))
                .paymentMethod(PaymentMethod.TRANSFER).build()))
                .hasMessageContaining("bukan milik penerima");
        assertThatThrownBy(() -> paymentService.create(command(owner, null, "1000", LocalDate.of(2135, 7, 1))
                .expenseAccountId(null).billId(bill.getId()).paymentMethod(PaymentMethod.TRANSFER).build()))
                .hasMessageContaining("Jumlah pelunasan");
    }

    // ==================== deposits ====================

    @Test
    @DisplayName("Deposit lifecycle: draft does not count, over-deposit rejected, on time vs late vs overdue against the 15th")
    void depositStatus() {
        Vendor payee = individual(NIK, null);
        WithholdingPayment payment = pay(payee, null, "10000000", LocalDate.of(2136, 1, 10));

        assertThatThrownBy(() -> deposit(WithholdingTreatment.PPH_21_BUKAN_PEGAWAI, 2136, 1, "1000", LocalDate.of(2136, 2, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("melebihi");

        paymentService.post(payment.getId(), "tester");
        assertThat(status(2136, 1, LocalDate.of(2136, 2, 15)).status()).isEqualTo(WithholdingDepositStatus.OPEN);
        assertThat(status(2136, 1, LocalDate.of(2136, 2, 15)).dueDate()).isEqualTo(LocalDate.of(2136, 2, 15));
        assertThat(status(2136, 1, LocalDate.of(2136, 2, 16)).status()).isEqualTo(WithholdingDepositStatus.OVERDUE);

        WithholdingDeposit partial = deposit(WithholdingTreatment.PPH_21_BUKAN_PEGAWAI, 2136, 1, "100000", LocalDate.of(2136, 2, 10));
        assertThat(transactionService.findById(partial.getTransaction().getId()).getStatus()).isEqualTo(TransactionStatus.DRAFT);
        assertThat(status(2136, 1, LocalDate.of(2136, 2, 16)).deposited()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> deposit(WithholdingTreatment.PPH_21_BUKAN_PEGAWAI, 2136, 1, "150001", LocalDate.of(2136, 2, 10)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("150000");

        BigDecimal before = balance("2.1.20");
        depositService.post(partial.getId(), "tester");
        entityManager.flush();
        assertThat(balance("2.1.20")).isEqualByComparingTo(before.add(new BigDecimal("100000")));
        WithholdingQueryService.MasaStatus afterPartial = status(2136, 1, LocalDate.of(2136, 2, 16));
        assertThat(afterPartial.deposited()).isEqualByComparingTo("100000");
        assertThat(afterPartial.outstanding()).isEqualByComparingTo("150000");
        assertThat(afterPartial.status()).isEqualTo(WithholdingDepositStatus.OVERDUE);

        WithholdingDeposit rest = deposit(WithholdingTreatment.PPH_21_BUKAN_PEGAWAI, 2136, 1, "150000", LocalDate.of(2136, 2, 15));
        depositService.post(rest.getId(), "tester");
        entityManager.flush();
        assertThat(status(2136, 1, LocalDate.of(2136, 3, 1)).status()).isEqualTo(WithholdingDepositStatus.ON_TIME);

        Vendor late = individual(NIK, null);
        WithholdingPayment latePayment = pay(late, null, "10000000", LocalDate.of(2136, 2, 10));
        paymentService.post(latePayment.getId(), "tester");
        WithholdingDeposit lateDeposit = deposit(WithholdingTreatment.PPH_21_BUKAN_PEGAWAI, 2136, 2, "250000", LocalDate.of(2136, 3, 16));
        depositService.post(lateDeposit.getId(), "tester");
        entityManager.flush();
        assertThat(status(2136, 2, LocalDate.of(2136, 4, 1)).status()).isEqualTo(WithholdingDepositStatus.LATE);

        transactionService.voidTransaction(lateDeposit.getTransaction().getId(), VoidReason.INPUT_ERROR, "x", "tester");
        entityManager.flush();
        entityManager.clear();
        assertThat(status(2136, 2, LocalDate.of(2136, 4, 1)).status()).isEqualTo(WithholdingDepositStatus.OVERDUE);
    }

    @Test
    @DisplayName("Voiding a deposited payment shows the over-deposit; cancel removes a draft deposit; each treatment has its own row")
    void depositEdges() {
        Vendor payee = payee(PayeeType.BADAN, WithholdingTreatment.PPH_23, "24-104-03", null, NPWP);
        WithholdingPayment payment = pay(payee, null, "10000000", LocalDate.of(2136, 5, 10));
        paymentService.post(payment.getId(), "tester");
        WithholdingDeposit draft = deposit(WithholdingTreatment.PPH_23, 2136, 5, "200000", LocalDate.of(2136, 6, 1));
        depositService.cancel(draft.getId());
        entityManager.flush();
        assertThat(queryService.findDeposits(2136, 5)).isEmpty();

        WithholdingDeposit posted = deposit(WithholdingTreatment.PPH_23, 2136, 5, "200000", LocalDate.of(2136, 6, 1));
        depositService.post(posted.getId(), "tester");
        assertThatThrownBy(() -> depositService.cancel(posted.getId())).hasMessageContaining("void transaksi");

        transactionService.voidTransaction(payment.getTransaction().getId(), VoidReason.INPUT_ERROR, "x", "tester");
        entityManager.flush();
        entityManager.clear();

        List<WithholdingQueryService.MasaStatus> rows = queryService.getMasaStatus(2136, 5, LocalDate.of(2136, 7, 1));
        assertThat(rows).extracting(WithholdingQueryService.MasaStatus::treatment)
                .containsExactly(WithholdingTreatment.PPH_21_BUKAN_PEGAWAI, WithholdingTreatment.PPH_23, WithholdingTreatment.PPH_26);
        assertThat(rows.get(0).status()).isEqualTo(WithholdingDepositStatus.NOTHING_DUE);
        assertThat(rows.get(1).status()).isEqualTo(WithholdingDepositStatus.OVERDEPOSITED);
        assertThat(rows.get(1).outstanding()).isEqualByComparingTo("-200000");
        assertThat(rows.get(2).dueDate()).isEqualTo(LocalDate.of(2136, 6, 15));
    }

    // ==================== report ====================

    @Test
    @DisplayName("Report lists posted service debits with no PPh detail in the masa; PPN detail does not document; other cases excluded")
    void undocumentedReport() {
        ChartOfAccount service = expenseAccount(Boolean.TRUE);
        ChartOfAccount notService = expenseAccount(Boolean.FALSE);
        LocalDate date = LocalDate.of(2137, 3, 10);

        Transaction undocumented = postedJournal(service, "1000000", date);
        Transaction ppnOnly = postedJournal(service, "2000000", date);
        addDetail(ppnOnly, TaxType.PPN_MASUKAN);
        Transaction documented = postedJournal(service, "3000000", date);
        addDetail(documented, TaxType.PPH_42);
        postedJournal(notService, "4000000", date);
        postedJournal(service, "5000000", LocalDate.of(2137, 4, 1));
        draftJournal(service, "6000000", date);
        Transaction voided = postedJournal(service, "7000000", date);
        transactionService.voidTransaction(voided.getId(), VoidReason.INPUT_ERROR, "x", "tester");

        Vendor payee = individual(NIK, null);
        WithholdingPayment flow = paymentService.create(command(payee, null, "8000000", date).expenseAccountId(service.getId()).build());
        paymentService.post(flow.getId(), "tester");
        entityManager.flush();
        entityManager.clear();

        WithholdingQueryService.UndocumentedReport report = queryService.findUndocumentedServicePayments(2137, 3);
        assertThat(report.rows()).extracting(r -> r.transactionId())
                .containsExactlyInAnyOrder(undocumented.getId(), ppnOnly.getId());
        assertThat(report.total()).isEqualByComparingTo("3000000");
        assertThat(report.rows()).allSatisfy(r -> assertThat(r.accountCode()).isEqualTo(service.getAccountCode()));
    }

    @Test
    @DisplayName("Report names unclassified expense accounts with activity in the masa")
    void undocumentedReportFlagsUnclassified() {
        ChartOfAccount unclassified = expenseAccount(null);
        postedJournal(unclassified, "1000000", LocalDate.of(2137, 6, 10));
        postedJournal(expenseAccount(Boolean.TRUE), "1000000", LocalDate.of(2137, 7, 10));

        assertThat(queryService.findUndocumentedServicePayments(2137, 6).unclassifiedAccounts())
                .containsExactly(unclassified.getAccountCode() + " " + unclassified.getAccountName());
        assertThat(queryService.findUndocumentedServicePayments(2137, 6).rows()).isEmpty();
        assertThat(queryService.findUndocumentedServicePayments(2137, 7).unclassifiedAccounts()).isEmpty();
    }

    @Test
    @DisplayName("A bill accrual on a service account is flagged until the bill has a withholding payment")
    void billAccrualCoveredByWithholdingPayment() {
        CompanyConfig config = companyConfigService.getConfig();
        config.setPayableAccount(account("2.1.01"));
        config.setInputTaxAccount(account("1.1.25"));
        ChartOfAccount service = expenseAccount(Boolean.TRUE);
        Vendor payee = payee(PayeeType.BADAN, WithholdingTreatment.PPH_23, "24-104-03", null, NPWP);
        Bill bill = billOn(payee, service, "1000000", LocalDate.of(2137, 9, 1));
        Transaction accrual = transactionService.findByStatus(TransactionStatus.DRAFT).stream()
                .filter(t -> "BILL".equals(t.getSourceDocumentType()) && bill.getId().equals(t.getSourceDocumentId()))
                .findFirst().orElseThrow();
        transactionService.post(accrual.getId(), "tester");
        entityManager.flush();
        assertThat(queryService.findUndocumentedServicePayments(2137, 9).rows()).extracting(r -> r.transactionId()).containsExactly(accrual.getId());

        paymentService.create(command(payee, null, "1000000", LocalDate.of(2137, 9, 20)).expenseAccountId(null)
                .billId(bill.getId()).settledAmount(new BigDecimal("1000000")).paymentMethod(PaymentMethod.TRANSFER).build());
        entityManager.flush();
        assertThat(queryService.findUndocumentedServicePayments(2137, 9).rows()).isEmpty();
    }

    // ==================== export ====================

    @Test
    @DisplayName("PPh 21 goes to BP21 only; PPh 23/26 to Unifikasi only; drafts in neither; PPh 26 sheet carries treaty data")
    void exports() throws IOException {
        LocalDate date = LocalDate.of(2138, 3, 10);
        Vendor individual = individual(NIK, null);
        paymentService.post(pay(individual, null, "10000000", date).getId(), "tester");
        pay(individual(NIK, null), null, "99000000", date);
        Vendor noId = individual(null, null);
        paymentService.post(pay(noId, null, "10000000", date).getId(), "tester");
        Vendor company = payee(PayeeType.BADAN, WithholdingTreatment.PPH_23, "24-104-03", null, NPWP);
        paymentService.post(pay(company, null, "10000000", date).getId(), "tester");
        Vendor treaty = foreign("10", "DGT-7", LocalDate.of(2138, 12, 31));
        paymentService.post(pay(treaty, null, "10000000", date).getId(), "tester");
        entityManager.flush();
        LocalDate start = YearMonth.of(2138, 3).atDay(1);
        LocalDate end = YearMonth.of(2138, 3).atEndOfMonth();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(coretaxExportService.exportBp21(start, end)))) {
            Sheet sheet = workbook.getSheet("DATA");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            Map<String, Row> byName = rowsBy(sheet, 6);
            Row withNik = byName.get(individual.getName());
            assertThat(withNik.getCell(4).getStringCellValue()).isEqualTo("NIK");
            assertThat(withNik.getCell(5).getStringCellValue()).isEqualTo(NIK);
            assertThat(withNik.getCell(8).getStringCellValue()).isEqualTo("21-100-09");
            assertThat(withNik.getCell(9).getNumericCellValue()).isEqualTo(10000000d);
            assertThat(withNik.getCell(10).getNumericCellValue()).isEqualTo(5000000d);
            assertThat(withNik.getCell(11).getNumericCellValue()).isEqualTo(5d);
            assertThat(withNik.getCell(12).getNumericCellValue()).isEqualTo(250000d);
            Row without = byName.get(noId.getName());
            assertThat(without.getCell(4).getStringCellValue()).isEqualTo("NONE");
            assertThat(without.getCell(12).getNumericCellValue()).isEqualTo(300000d);
        }

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(coretaxExportService.exportBupotUnifikasi(start, end)))) {
            Sheet data = workbook.getSheet("DATA");
            assertThat(data.getLastRowNum()).isEqualTo(2);
            Map<String, Row> byName = rowsBy(data, 7);
            assertThat(byName.get(company.getName()).getCell(11).getNumericCellValue()).isEqualTo(200000d);
            assertThat(byName.get(company.getName()).getCell(12).getStringCellValue()).isEmpty();
            assertThat(byName.get(treaty.getName()).getCell(4).getStringCellValue()).isEqualTo("FOREIGN");
            assertThat(byName.get(treaty.getName()).getCell(5).getStringCellValue()).isEqualTo("TIN-SG-1");
            assertThat(byName.get(treaty.getName()).getCell(11).getNumericCellValue()).isEqualTo(1000000d);
            assertThat(byName.get(treaty.getName()).getCell(12).getStringCellValue()).isEqualTo("DGT");

            Sheet pph26 = workbook.getSheet("PPH26");
            assertThat(pph26.getLastRowNum()).isEqualTo(1);
            Row row = pph26.getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo(treaty.getName());
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("SG");
            assertThat(row.getCell(3).getStringCellValue()).isEqualTo("DGT-7");
            assertThat(row.getCell(6).getNumericCellValue()).isEqualTo(10d);
        }
    }

    @Test
    @DisplayName("Query API filters payments by treatment and payee and returns drafts; payees exclude unclassified vendors")
    void queryApi() {
        Vendor one = individual(NIK, null);
        Vendor two = payee(PayeeType.BADAN, WithholdingTreatment.PPH_23, "24-104-03", null, NPWP);
        WithholdingPayment first = pay(one, null, "1000000", LocalDate.of(2139, 1, 10));
        WithholdingPayment second = pay(two, null, "1000000", LocalDate.of(2139, 1, 11));
        pay(one, null, "1000000", LocalDate.of(2139, 2, 11));

        assertThat(queryService.findPayments(2139, 1, null, null)).extracting(WithholdingPayment::getId).containsExactly(first.getId(), second.getId());
        assertThat(queryService.findPayments(2139, 1, WithholdingTreatment.PPH_23, null)).extracting(WithholdingPayment::getId).containsExactly(second.getId());
        assertThat(queryService.findPayments(2139, 1, null, one.getId())).extracting(WithholdingPayment::getId).containsExactly(first.getId());
        assertThat(queryService.findPayees()).extracting(Vendor::getId).contains(one.getId(), two.getId());
        assertThat(queryService.findPayees()).allSatisfy(v -> assertThat(v.getPayeeType()).isNotNull());
    }

    // ==================== helpers ====================

    private Map<String, Row> rowsBy(Sheet sheet, int nameColumn) {
        Map<String, Row> result = new java.util.HashMap<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            result.put(sheet.getRow(i).getCell(nameColumn).getStringCellValue(), sheet.getRow(i));
        }
        return result;
    }

    private WithholdingQueryService.MasaStatus status(int year, int month, LocalDate asOf) {
        return queryService.getMasaStatus(year, month, asOf).get(0);
    }

    private WithholdingDeposit deposit(WithholdingTreatment treatment, int year, int month, String amount, LocalDate date) {
        return depositService.create(WithholdingDepositService.CreateDeposit.builder()
                .treatment(treatment).year(year).month(month).amount(new BigDecimal(amount)).depositDate(date)
                .bankAccountId(account("1.1.02").getId()).billingCode("B-1").ntpn("N-1").createdBy("tester").build());
    }

    private WithholdingPayment pay(Vendor payee, WithholdingTreatment treatment, String gross, LocalDate date) {
        return paymentService.create(command(payee, treatment, gross, date).build());
    }

    private WithholdingPaymentService.CreatePayment.CreatePaymentBuilder command(Vendor payee, WithholdingTreatment treatment, String gross, LocalDate date) {
        return WithholdingPaymentService.CreatePayment.builder()
                .vendorId(payee.getId()).treatment(treatment).paymentDate(date).grossAmount(new BigDecimal(gross))
                .expenseAccountId(account("5.1.05").getId()).bankAccountId(account("1.1.02").getId())
                .description("Bayar " + payee.getName()).createdBy("tester");
    }

    private Vendor individual(String nik, String npwp) {
        return payee(PayeeType.ORANG_PRIBADI, WithholdingTreatment.PPH_21_BUKAN_PEGAWAI, "21-100-09", nik, npwp);
    }

    private Vendor foreign(String treatyRate, String dgt, LocalDate validUntil) {
        Vendor vendor = newVendor(PayeeType.LUAR_NEGERI, WithholdingTreatment.PPH_26, "27-100-99", null, null);
        vendor.setCountryCode("SG");
        vendor.setForeignTin("TIN-SG-1");
        vendor.setTreatyRate(treatyRate == null ? null : new BigDecimal(treatyRate));
        vendor.setDgtFormNumber(dgt);
        vendor.setDgtValidUntil(validUntil);
        return vendorService.create(vendor);
    }

    private Vendor payee(PayeeType type, WithholdingTreatment treatment, String objectCode, String nik, String npwp) {
        return vendorService.create(newVendor(type, treatment, objectCode, nik, npwp));
    }

    private Vendor newVendor(PayeeType type, WithholdingTreatment treatment, String objectCode, String nik, String npwp) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Vendor vendor = new Vendor();
        vendor.setCode("WH-" + suffix);
        vendor.setName("Penerima " + suffix);
        vendor.setAddress("Jl. Uji " + suffix);
        vendor.setPayeeType(type);
        vendor.setDefaultWithholding(treatment);
        vendor.setDefaultTaxObjectCode(objectCode);
        vendor.setNik(nik);
        vendor.setNpwp(npwp);
        return vendor;
    }

    private Bill approvedBill(Vendor payee, String total) {
        return billOn(payee, account("5.1.05"), total, LocalDate.of(2135, 6, 1));
    }

    private Bill billOn(Vendor payee, ChartOfAccount expense, String total, LocalDate date) {
        Bill bill = new Bill();
        bill.setVendor(payee);
        bill.setBillDate(date);
        bill.setDueDate(date.plusDays(30));
        BillLine line = new BillLine();
        line.setDescription("Jasa");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal(total));
        line.setExpenseAccount(expense);
        List<BillLine> lines = new ArrayList<>(List.of(line));
        Bill saved = billService.create(bill, lines);
        return billService.approve(saved.getId());
    }

    private ChartOfAccount expenseAccount(Boolean serviceExpense) {
        ChartOfAccount account = new ChartOfAccount();
        account.setAccountCode("5.9.T" + (++sequence) + UUID.randomUUID().toString().substring(0, 4));
        account.setAccountName("Beban Uji " + UUID.randomUUID().toString().substring(0, 8));
        account.setAccountType(AccountType.EXPENSE);
        account.setNormalBalance(NormalBalance.DEBIT);
        account.setIsHeader(false);
        account.setServiceExpense(serviceExpense);
        return chartOfAccountService.create(account);
    }

    private Transaction postedJournal(ChartOfAccount account, String amount, LocalDate date) {
        return transactionService.post(draftJournal(account, amount, date), "tester");
    }

    private UUID draftJournal(ChartOfAccount account, String amount, LocalDate date) {
        BigDecimal value = new BigDecimal(amount);
        return transactionApiService.createJournalEntry(new JournalEntryRequest(date, "Jasa " + amount, null, List.of(
                new JournalEntryRequest.JournalLineRequest(account.getId(), value, BigDecimal.ZERO),
                new JournalEntryRequest.JournalLineRequest(account("1.1.02").getId(), BigDecimal.ZERO, value))), "tester").transactionId();
    }

    private void addDetail(Transaction transaction, TaxType type) {
        TaxTransactionDetail detail = new TaxTransactionDetail();
        detail.setTaxType(type);
        detail.setCounterpartyName("Lawan");
        detail.setDpp(BigDecimal.TEN);
        detail.setPpn(BigDecimal.ONE);
        detail.setGrossAmount(BigDecimal.TEN);
        detail.setTaxRate(BigDecimal.ONE);
        detail.setTaxAmount(BigDecimal.ONE);
        taxDetailService.save(transaction.getId(), detail);
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Account missing from V800: " + code));
    }

    private Map<String, BigDecimal> lines(Transaction transaction) {
        return transaction.getJournalEntries().stream().collect(Collectors.toMap(
                (JournalEntry e) -> e.getAccount().getAccountCode(),
                (JournalEntry e) -> e.getDebitAmount().subtract(e.getCreditAmount()).setScale(0)));
    }

    private BigDecimal balance(String code) {
        entityManager.flush();
        return (BigDecimal) entityManager.createQuery("""
                SELECT COALESCE(SUM(j.debitAmount - j.creditAmount), 0) FROM JournalEntry j
                WHERE j.account.accountCode = :code AND j.transaction.status <> com.artivisi.accountingfinance.enums.TransactionStatus.DRAFT""")
                .setParameter("code", code).getSingleResult();
    }
}
