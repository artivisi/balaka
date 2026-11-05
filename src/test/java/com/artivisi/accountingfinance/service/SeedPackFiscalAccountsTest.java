package com.artivisi.accountingfinance.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Later features resolve the seed accounts by code, so every pack must carry the same codes.
 */
@DisplayName("Seed pack fiscal accounts")
class SeedPackFiscalAccountsTest {

    private static final int CODE = 0;
    private static final int NAME = 1;
    private static final int TYPE = 2;
    private static final int PARENT = 3;
    private static final int TREATMENT = 7;

    @ParameterizedTest
    @ValueSource(strings = {"it-service", "online-seller", "coffee-shop", "campus"})
    @DisplayName("Seed accounts present with stable codes; non-header expense accounts classified")
    void packCarriesSeedAccounts(String pack) throws IOException {
        List<String[]> rows = Files.readAllLines(Path.of("industry-seed", pack, "seed-data", "02_chart_of_accounts.csv"))
                .stream().skip(1).map(l -> l.split(",", -1)).toList();
        rows.forEach(r -> assertThat(r).as(String.join(",", r)).hasSize(11));
        Map<String, String[]> byCode = rows.stream().collect(Collectors.toMap(r -> r[CODE], Function.identity()));

        assertAccount(byCode, "2.1.25", "Hutang PPh 26", "LIABILITY", "");
        assertAccount(byCode, "2.1.21", "Hutang PPh 23", "LIABILITY", "");
        assertThat(rows).as("exactly one Hutang PPh 21").filteredOn(r -> "Hutang PPh 21".equals(r[NAME])).hasSize(1);
        assertAccount(byCode, "5.8.01", "Beban Bonus", "EXPENSE", "DEDUCTIBLE");
        assertAccount(byCode, "5.8.02", "Beban THR", "EXPENSE", "DEDUCTIBLE");
        assertAccount(byCode, "5.8.03", "Beban Natura/Kenikmatan", "EXPENSE", "CONDITIONAL");
        assertAccount(byCode, "5.8.04", "Beban Tidak Dapat Dikurangkan", "EXPENSE", "NON_DEDUCTIBLE");
        if ("campus".equals(pack)) {
            assertThat(byCode).doesNotContainKeys("2.1.40", "3.2.03");
        } else {
            assertAccount(byCode, "2.1.40", "Hutang Dividen", "LIABILITY", "");
            assertAccount(byCode, "3.2.03", "Dividen", "EQUITY", "");
        }

        assertThat(rows).as("Beban Jasa Profesional is a service account")
                .filteredOn(r -> "Beban Jasa Profesional".equals(r[NAME])).singleElement()
                .satisfies(r -> assertThat(r[8]).isEqualTo("true"));

        assertThat(rows).as("close check roles in %s", pack).filteredOn(r -> !r[9].isEmpty())
                .extracting(r -> r[CODE] + "=" + r[9])
                .containsExactlyInAnyOrder("1.1.26=TAX_CREDIT", "2.1.50=SHAREHOLDER_CURRENT");
        assertAccount(byCode, "2.1.50", "Rekening Koran Pemegang Saham/Direksi", "LIABILITY", "");

        // Self-test roles: only on leaf expense accounts; PPh 23 and 4(2) roles only on service accounts.
        assertThat(rows).as("one OTHER_EXPENSE account in %s", pack).filteredOn(r -> "OTHER_EXPENSE".equals(r[10]))
                .singleElement().satisfies(r -> assertThat(r[NAME]).isEqualTo("Beban Operasional Lainnya"));
        assertThat(rows).as("SERVICE_PPH23 accounts in %s", pack).filteredOn(r -> "SERVICE_PPH23".equals(r[10]))
                .isNotEmpty().allSatisfy(r -> assertThat(r[8]).isEqualTo("true"));
        assertThat(rows).as("RENT_PPH42 accounts in %s", pack).filteredOn(r -> "RENT_PPH42".equals(r[10]))
                .allSatisfy(r -> {
                    assertThat(r[8]).isEqualTo("true");
                    assertThat(r[NAME]).contains("Sewa");
                })
                .hasSize("campus".equals(pack) ? 0 : 1);
        assertThat(rows).as("self-test roles on expense accounts only").filteredOn(r -> !r[10].isEmpty())
                .allSatisfy(r -> assertThat(r[TYPE]).isEqualTo("EXPENSE"));

        Set<String> parents = rows.stream().map(r -> r[PARENT]).collect(Collectors.toSet());
        for (String[] r : rows) {
            boolean leafExpense = "EXPENSE".equals(r[TYPE]) && !parents.contains(r[CODE]);
            assertThat(r[TREATMENT].isEmpty()).as("fiscal_treatment of %s in %s", r[CODE], pack).isEqualTo(!leafExpense);
            assertThat(r[8].isEmpty()).as("service_expense of %s in %s", r[CODE], pack).isEqualTo(!leafExpense);
            assertThat(byCode).as("parent of %s", r[CODE]).matches(m -> r[PARENT].isEmpty() || m.containsKey(r[PARENT]));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"it-service", "online-seller", "coffee-shop", "campus"})
    @DisplayName("Withholding templates are not system templates: the importer skips the lines of system templates")
    void withholdingTemplatesImportWithLines(String pack) throws IOException {
        Path seed = Path.of("industry-seed", pack, "seed-data");
        List<String> templates = Files.readAllLines(seed.resolve("04_journal_templates.csv"));
        assertThat(templates).filteredOn(l -> l.startsWith("Pembayaran dengan Pemotongan PPh,")).singleElement()
                .satisfies(l -> assertThat(l.split(",")[5]).isEqualTo("false"));
        assertThat(templates).filteredOn(l -> l.startsWith("Setor PPh Dipotong,")).singleElement()
                .satisfies(l -> assertThat(l.split(",")[5]).isEqualTo("false"));
        List<String> lines = Files.readAllLines(seed.resolve("05_journal_template_lines.csv"));
        assertThat(lines).filteredOn(l -> l.startsWith("Pembayaran dengan Pemotongan PPh,")).hasSize(3);
        assertThat(lines).filteredOn(l -> l.startsWith("Setor PPh Dipotong,")).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"it-service", "online-seller", "coffee-shop", "campus"})
    @DisplayName("Sales tax chain: every pack has Kredit Pajak PPh 23 and the customer bupot template, not as a system template")
    void salesTaxChainSeed(String pack) throws IOException {
        Path seed = Path.of("industry-seed", pack, "seed-data");
        assertThat(Files.readAllLines(seed.resolve("02_chart_of_accounts.csv")))
                .filteredOn(l -> l.split(",")[1].equals("Kredit Pajak PPh 23")).singleElement()
                .satisfies(l -> assertThat(l).startsWith("1.1.26,Kredit Pajak PPh 23,ASSET,1.1,DEBIT"));
        assertThat(Files.readAllLines(seed.resolve("04_journal_templates.csv")))
                .filteredOn(l -> l.startsWith("Pemotongan PPh 23 oleh Pelanggan,")).singleElement()
                .satisfies(l -> assertThat(l.split(",")[5]).isEqualTo("false"));
        assertThat(Files.readAllLines(seed.resolve("05_journal_template_lines.csv")))
                .filteredOn(l -> l.startsWith("Pemotongan PPh 23 oleh Pelanggan,"))
                .containsExactly("Pemotongan PPh 23 oleh Pelanggan,1,,KREDIT_PPH23,DEBIT,amount,Kredit Pajak PPh 23",
                        "Pemotongan PPh 23 oleh Pelanggan,2,,PIUTANG,CREDIT,amount,Piutang usaha");
    }

    @ParameterizedTest
    @ValueSource(strings = {"it-service", "online-seller", "coffee-shop", "campus"})
    @DisplayName("Asset disposal: every pack has one gain and one loss account and both templates, not as system templates")
    void assetDisposalSeed(String pack) throws IOException {
        Path seed = Path.of("industry-seed", pack, "seed-data");
        List<String[]> accounts = Files.readAllLines(seed.resolve("02_chart_of_accounts.csv")).stream().skip(1)
                .map(l -> l.split(",", -1)).toList();
        assertThat(accounts).filteredOn(r -> "Laba Penjualan Aset".equals(r[NAME])).singleElement()
                .satisfies(r -> assertThat(r[TYPE]).isEqualTo("REVENUE"));
        assertThat(accounts).filteredOn(r -> "Rugi Penjualan Aset".equals(r[NAME])).singleElement()
                .satisfies(r -> {
                    assertThat(r[TYPE]).isEqualTo("EXPENSE");
                    assertThat(r[TREATMENT]).isEqualTo("DEDUCTIBLE");
                });
        List<String> templates = Files.readAllLines(seed.resolve("04_journal_templates.csv"));
        for (String name : List.of("Pelepasan Aset Tetap", "Penghapusan Aset Tetap")) {
            assertThat(templates).filteredOn(l -> l.startsWith(name + ",")).singleElement()
                    .satisfies(l -> assertThat(l).contains(",false,true,1,0,"));
        }
        List<String> lines = Files.readAllLines(seed.resolve("05_journal_template_lines.csv"));
        assertThat(lines).filteredOn(l -> l.startsWith("Pelepasan Aset Tetap,")).containsExactly(
                "Pelepasan Aset Tetap,1,,KAS_PENERIMAAN,DEBIT,disposalProceeds,Hasil penjualan aset",
                "Pelepasan Aset Tetap,2,,AKUM_PENYUSUTAN,DEBIT,accumulatedDepreciation,Hapus akumulasi penyusutan",
                "Pelepasan Aset Tetap,3,,RUGI_PELEPASAN,DEBIT,lossOnDisposal,Rugi pelepasan aset",
                "Pelepasan Aset Tetap,4,,ASET_TETAP,CREDIT,assetCost,Hapus nilai perolehan aset",
                "Pelepasan Aset Tetap,5,,LABA_PELEPASAN,CREDIT,gainOnDisposal,Laba pelepasan aset");
        assertThat(lines).filteredOn(l -> l.startsWith("Penghapusan Aset Tetap,")).containsExactly(
                "Penghapusan Aset Tetap,1,,AKUM_PENYUSUTAN,DEBIT,accumulatedDepreciation,Hapus akumulasi penyusutan",
                "Penghapusan Aset Tetap,2,,RUGI_PELEPASAN,DEBIT,lossOnDisposal,Rugi pelepasan aset",
                "Penghapusan Aset Tetap,3,,ASET_TETAP,CREDIT,assetCost,Hapus nilai perolehan aset");
    }

    private void assertAccount(Map<String, String[]> byCode, String code, String name, String type, String treatment) {
        assertThat(byCode).containsKey(code);
        String[] row = byCode.get(code);
        assertThat(row[NAME]).isEqualTo(name);
        assertThat(row[TYPE]).isEqualTo(type);
        assertThat(row[TREATMENT]).isEqualTo(treatment);
    }
}
