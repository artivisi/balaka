package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.FiscalPeriod;
import com.artivisi.accountingfinance.enums.FiscalPeriodStatus;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for FiscalPeriodService.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("FiscalPeriodService Integration Tests")
class FiscalPeriodServiceTest {

    @Autowired
    private FiscalPeriodService fiscalPeriodService;

    @Nested
    @DisplayName("Find Operations")
    class FindOperationsTests {

        @Test
        @DisplayName("Should throw exception for non-existent ID")
        void shouldThrowExceptionForNonExistentId() {
            UUID randomId = UUID.randomUUID();
            assertThatThrownBy(() -> fiscalPeriodService.findById(randomId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Fiscal period not found");
        }

        @Test
        @DisplayName("Should find all fiscal periods with pagination")
        void shouldFindAllFiscalPeriodsWithPagination() {
            Page<FiscalPeriod> result = fiscalPeriodService.findAll(PageRequest.of(0, 10));
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should find fiscal periods by filters with null values")
        void shouldFindByFiltersWithNullValues() {
            Page<FiscalPeriod> result = fiscalPeriodService.findByFilters(
                    null, null, PageRequest.of(0, 10));
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should find fiscal periods by filters with year")
        void shouldFindByFiltersWithYear() {
            Page<FiscalPeriod> result = fiscalPeriodService.findByFilters(
                    2025, null, PageRequest.of(0, 10));
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should find fiscal periods by filters with status OPEN")
        void shouldFindByFiltersWithStatusOpen() {
            Page<FiscalPeriod> result = fiscalPeriodService.findByFilters(
                    null, FiscalPeriodStatus.OPEN, PageRequest.of(0, 10));
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should find fiscal periods by filters with status MONTH_CLOSED")
        void shouldFindByFiltersWithStatusMonthClosed() {
            Page<FiscalPeriod> result = fiscalPeriodService.findByFilters(
                    null, FiscalPeriodStatus.MONTH_CLOSED, PageRequest.of(0, 10));
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should find fiscal periods by filters with status TAX_FILED")
        void shouldFindByFiltersWithStatusTaxFiled() {
            Page<FiscalPeriod> result = fiscalPeriodService.findByFilters(
                    null, FiscalPeriodStatus.TAX_FILED, PageRequest.of(0, 10));
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should find fiscal periods by year")
        void shouldFindByYear() {
            List<FiscalPeriod> result = fiscalPeriodService.findByYear(2025);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should find open periods")
        void shouldFindOpenPeriods() {
            List<FiscalPeriod> openPeriods = fiscalPeriodService.findOpenPeriods();
            assertThat(openPeriods).isNotNull();
        }

        @Test
        @DisplayName("Should find distinct years")
        void shouldFindDistinctYears() {
            List<Integer> years = fiscalPeriodService.findDistinctYears();
            assertThat(years).isNotNull();
        }

        @Test
        @DisplayName("Should find by year and month - empty for non-existent")
        void shouldFindByYearAndMonthEmptyForNonExistent() {
            Optional<FiscalPeriod> result = fiscalPeriodService.findByYearAndMonth(2099, 12);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should find by date - empty for far future")
        void shouldFindByDateEmptyForFarFuture() {
            Optional<FiscalPeriod> result = fiscalPeriodService.findByDate(LocalDate.of(2099, 12, 31));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should count by status")
        void shouldCountByStatus() {
            long count = fiscalPeriodService.countByStatus(FiscalPeriodStatus.OPEN);
            assertThat(count).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Create Operations")
    class CreateOperationsTests {

        @Test
        @DisplayName("Should create fiscal period")
        void shouldCreateFiscalPeriod() {
            // Use far future year/month to avoid conflicts
            FiscalPeriod created = fiscalPeriodService.create(2098, 1);

            assertThat(created.getId()).isNotNull();
            assertThat(created.getYear()).isEqualTo(2098);
            assertThat(created.getMonth()).isEqualTo(1);
            assertThat(created.getStatus()).isEqualTo(FiscalPeriodStatus.OPEN);
        }

        @Test
        @DisplayName("Should reject duplicate fiscal period")
        void shouldRejectDuplicateFiscalPeriod() {
            // Create first
            fiscalPeriodService.create(2097, 6);

            // Try to create duplicate
            assertThatThrownBy(() -> fiscalPeriodService.create(2097, 6))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Periode fiskal 2097-06 sudah ada");
        }

        @Test
        @DisplayName("Should get or create fiscal period - create new")
        void shouldGetOrCreateFiscalPeriodCreateNew() {
            FiscalPeriod period = fiscalPeriodService.getOrCreate(2096, 3);

            assertThat(period).isNotNull();
            assertThat(period.getYear()).isEqualTo(2096);
            assertThat(period.getMonth()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should get or create fiscal period - get existing")
        void shouldGetOrCreateFiscalPeriodGetExisting() {
            // Create first
            FiscalPeriod created = fiscalPeriodService.create(2095, 7);

            // Get or create should return existing
            FiscalPeriod retrieved = fiscalPeriodService.getOrCreate(2095, 7);

            assertThat(retrieved.getId()).isEqualTo(created.getId());
        }
    }

    @Nested
    @DisplayName("Status Transition Operations")
    class StatusTransitionTests {

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should close month")
        void shouldCloseMonth() {
            // Create an open period
            FiscalPeriod period = fiscalPeriodService.create(2094, 1);

            // Close it
            FiscalPeriod closed = fiscalPeriodService.closeMonth(period.getId(), "Monthly closing");

            assertThat(closed.getStatus()).isEqualTo(FiscalPeriodStatus.MONTH_CLOSED);
            assertThat(closed.getMonthClosedAt()).isNotNull();
            assertThat(closed.getMonthClosedBy()).isEqualTo("admin");
        }

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should reopen closed month")
        void shouldReopenClosedMonth() {
            // Create and close a period
            FiscalPeriod period = fiscalPeriodService.create(2093, 2);
            fiscalPeriodService.closeMonth(period.getId(), "Closing");

            // Reopen it
            FiscalPeriod reopened = fiscalPeriodService.reopen(period.getId(), "Correction needed");

            assertThat(reopened.getStatus()).isEqualTo(FiscalPeriodStatus.OPEN);
            assertThat(reopened.getMonthClosedAt()).isNull();
        }

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should file tax after month closed")
        void shouldFileTaxAfterMonthClosed() {
            // Create, close, then file tax
            FiscalPeriod period = fiscalPeriodService.create(2092, 3);
            fiscalPeriodService.closeMonth(period.getId(), "Closing");

            FiscalPeriod taxFiled = fiscalPeriodService.fileTax(period.getId(), "Tax filed");

            assertThat(taxFiled.getStatus()).isEqualTo(FiscalPeriodStatus.TAX_FILED);
            assertThat(taxFiled.getTaxFiledAt()).isNotNull();
            assertThat(taxFiled.getTaxFiledBy()).isEqualTo("admin");
        }

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should reject close month for already closed period")
        void shouldRejectCloseMonthForAlreadyClosedPeriod() {
            FiscalPeriod period = fiscalPeriodService.create(2091, 4);
            fiscalPeriodService.closeMonth(period.getId(), "Closing");

            assertThatThrownBy(() -> fiscalPeriodService.closeMonth(period.getId(), "Second close"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot close month");
        }

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should reject file tax for open period")
        void shouldRejectFileTaxForOpenPeriod() {
            FiscalPeriod period = fiscalPeriodService.create(2090, 5);

            assertThatThrownBy(() -> fiscalPeriodService.fileTax(period.getId(), "Tax"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot file tax");
        }

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should reject reopen for open period")
        void shouldRejectReopenForOpenPeriod() {
            FiscalPeriod period = fiscalPeriodService.create(2089, 6);

            assertThatThrownBy(() -> fiscalPeriodService.reopen(period.getId(), "Reopen"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot reopen");
        }
    }

    @Nested
    @DisplayName("Posting Validation Operations")
    class PostingValidationTests {

        @Test
        @DisplayName("Should validate period open for posting - true for non-existent period")
        void shouldValidatePeriodOpenForPostingTrueForNonExistentPeriod() {
            boolean isOpen = fiscalPeriodService.isPeriodOpenForPosting(LocalDate.of(2088, 1, 15));
            assertThat(isOpen).isTrue();
        }

        @Test
        @DisplayName("Should validate period open for posting - true for open period")
        void shouldValidatePeriodOpenForPostingTrueForOpenPeriod() {
            fiscalPeriodService.create(2087, 2);

            boolean isOpen = fiscalPeriodService.isPeriodOpenForPosting(LocalDate.of(2087, 2, 15));
            assertThat(isOpen).isTrue();
        }

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should validate period open for posting - false for closed period")
        void shouldValidatePeriodOpenForPostingFalseForClosedPeriod() {
            FiscalPeriod period = fiscalPeriodService.create(2086, 3);
            fiscalPeriodService.closeMonth(period.getId(), "Closing");

            boolean isOpen = fiscalPeriodService.isPeriodOpenForPosting(LocalDate.of(2086, 3, 15));
            assertThat(isOpen).isFalse();
        }

        @Test
        @DisplayName("Should not throw for posting validation on non-existent period")
        void shouldNotThrowForPostingValidationOnNonExistentPeriod() {
            // Should not throw for non-existent period
            fiscalPeriodService.validatePeriodOpenForPosting(LocalDate.of(2085, 1, 15));
        }

        @Test
        @WithMockUser(username = "admin")
        @DisplayName("Should throw for posting validation on closed period")
        void shouldThrowForPostingValidationOnClosedPeriod() {
            FiscalPeriod period = fiscalPeriodService.create(2084, 4);
            fiscalPeriodService.closeMonth(period.getId(), "Closing");

            assertThatThrownBy(() -> fiscalPeriodService.validatePeriodOpenForPosting(LocalDate.of(2084, 4, 15)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot post journal entry");
        }
    }
}
