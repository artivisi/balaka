package com.artivisi.accountingfinance.migration;

import com.artivisi.accountingfinance.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every column written through {@link EncryptedStringConverter} must be as wide in the migrated schema as the
 * entity declares: with app.encryption.key set the stored value is "ENC:" + Base64(IV + ciphertext + tag), far
 * longer than the plaintext. Hibernate's ddl-auto=validate does not compare VARCHAR lengths, so a narrow
 * column only shows up as an insert failure on an instance that has the key.
 */
@DisplayName("Encrypted columns are as wide as their entity mapping")
class EncryptedColumnWidthTest {

    private record EncryptedColumn(String table, String column, int declaredLength) {
    }

    @Test
    @DisplayName("Each @Convert(EncryptedStringConverter) column has at least the declared VARCHAR length after all migrations")
    void encryptedColumnsAreWideEnough() {
        List<EncryptedColumn> columns = encryptedColumns();
        assertThat(columns).as("encrypted columns found by the entity scan")
                .extracting(EncryptedColumn::column)
                .contains("account_number", "nik_ktp", "counterparty_nik");

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            for (EncryptedColumn c : columns) {
                List<Integer> widths = jdbc.query(
                        "SELECT character_maximum_length FROM information_schema.columns "
                                + "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                        (rs, i) -> (Integer) rs.getObject(1), c.table(), c.column());
                assertThat(widths).as("column %s.%s exists", c.table(), c.column()).hasSize(1);
                Integer width = widths.getFirst();
                if (width != null) {
                    assertThat(width).as("width of %s.%s", c.table(), c.column())
                            .isGreaterThanOrEqualTo(c.declaredLength());
                }
            }
        }
    }

    private static List<EncryptedColumn> encryptedColumns() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        List<EncryptedColumn> result = new ArrayList<>();
        for (BeanDefinition bean : scanner.findCandidateComponents("com.artivisi.accountingfinance.entity")) {
            Class<?> type = ClassUtils.resolveClassName(bean.getBeanClassName(), EncryptedColumnWidthTest.class.getClassLoader());
            Table table = type.getAnnotation(Table.class);
            ReflectionUtils.doWithFields(type, field -> {
                Convert convert = field.getAnnotation(Convert.class);
                if (convert == null || convert.converter() != EncryptedStringConverter.class) {
                    return;
                }
                assertThat(table).as("@Table on %s", type.getSimpleName()).isNotNull();
                Column column = field.getAnnotation(Column.class);
                assertThat(column).as("@Column on %s.%s", type.getSimpleName(), field.getName()).isNotNull();
                result.add(new EncryptedColumn(table.name(), column.name(), column.length()));
            });
        }
        return result;
    }
}
