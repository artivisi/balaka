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
        rows.forEach(r -> assertThat(r).as(String.join(",", r)).hasSize(9));
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

    private void assertAccount(Map<String, String[]> byCode, String code, String name, String type, String treatment) {
        assertThat(byCode).containsKey(code);
        String[] row = byCode.get(code);
        assertThat(row[NAME]).isEqualTo(name);
        assertThat(row[TYPE]).isEqualTo(type);
        assertThat(row[TREATMENT]).isEqualTo(treatment);
    }
}
