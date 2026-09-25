package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.AmortizationEntry;
import com.artivisi.accountingfinance.entity.AmortizationSchedule;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.enums.AmortizationFrequency;
import com.artivisi.accountingfinance.enums.ScheduleStatus;
import com.artivisi.accountingfinance.enums.ScheduleType;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.AmortizationBatchService;
import com.artivisi.accountingfinance.service.AmortizationEntryService;
import com.artivisi.accountingfinance.service.AmortizationScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequestMapping("/amortization")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.AMORTIZATION_VIEW + "')")
public class AmortizationController {

    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String ATTR_ERROR_MESSAGE = "errorMessage";
    private static final String ATTR_ENTRY_ERROR_MESSAGE = "entryErrorMessage";
    private static final String VIEW_FORM = "amortization/form";
    private static final String VIEW_ENTRY_TABLE = "amortization/fragments/entry-table :: table";
    private static final String REDIRECT_AMORTIZATION_PREFIX = "redirect:/amortization/";
    private static final String ATTR_TYPES = "types";
    private static final String ATTR_SCHEDULE = "schedule";
    private static final String ATTR_ENTRIES = "entries";

    private final AmortizationScheduleService scheduleService;
    private final AmortizationEntryService entryService;
    private final AmortizationBatchService batchService;
    private final ChartOfAccountRepository chartOfAccountRepository;

    @GetMapping
    public String list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            @PageableDefault(size = 20) Pageable pageable,
            Model model) {

        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_AMORTIZATION);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedType", type);
        model.addAttribute("searchQuery", search);
        model.addAttribute("statuses", ScheduleStatus.values());
        model.addAttribute(ATTR_TYPES, ScheduleType.values());

        ScheduleStatus statusEnum = status != null && !status.isBlank()
                ? ScheduleStatus.valueOf(status.toUpperCase()) : null;
        ScheduleType typeEnum = type != null && !type.isBlank()
                ? ScheduleType.valueOf(type.toUpperCase()) : null;

        Page<AmortizationSchedule> schedules = scheduleService.findByFilters(
                statusEnum, typeEnum, search, pageable);
        model.addAttribute("schedules", schedules);
        model.addAttribute("pendingCount", entryService.countPendingEntries());

        if ("true".equals(hxRequest)) {
            return "amortization/fragments/schedule-table :: table";
        }
        return "amortization/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.AMORTIZATION_CREATE + "')")
    public String create(Model model) {
        populateFormModel(model, false);
        return VIEW_FORM;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_AMORTIZATION);
        populateEntryModel(model, id);
        return "amortization/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('" + Permission.AMORTIZATION_EDIT + "')")
    public String edit(@PathVariable UUID id, Model model) {
        populateFormModel(model, true);
        model.addAttribute(ATTR_SCHEDULE, scheduleService.findById(id));
        return VIEW_FORM;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permission.AMORTIZATION_CREATE + "')")
    public String create(
            @RequestParam String code,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam String scheduleType,
            @RequestParam UUID sourceAccountId,
            @RequestParam UUID targetAccountId,
            @RequestParam BigDecimal totalAmount,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam String frequency,
            @RequestParam(defaultValue = "false") boolean autoPost,
            @RequestParam(defaultValue = "1") Integer postDay,
            Model model,
            RedirectAttributes redirectAttributes) {

        AmortizationSchedule schedule = new AmortizationSchedule();
        schedule.setCode(code);
        schedule.setName(name);
        schedule.setDescription(description);
        schedule.setScheduleType(ScheduleType.valueOf(scheduleType));
        schedule.setTotalAmount(totalAmount);
        schedule.setStartDate(startDate);
        schedule.setEndDate(endDate);
        schedule.setFrequency(AmortizationFrequency.valueOf(frequency));
        schedule.setAutoPost(autoPost);
        schedule.setPostDay(postDay);

        ChartOfAccount sourceAccount = chartOfAccountRepository.findById(sourceAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Source account not found"));
        ChartOfAccount targetAccount = chartOfAccountRepository.findById(targetAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Target account not found"));

        schedule.setSourceAccount(sourceAccount);
        schedule.setTargetAccount(targetAccount);

        try {
            AmortizationSchedule saved = scheduleService.create(schedule);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Jadwal amortisasi berhasil dibuat");
            return REDIRECT_AMORTIZATION_PREFIX + saved.getId();
        } catch (IllegalArgumentException e) {
            populateFormModel(model, false);
            model.addAttribute(ATTR_SCHEDULE, schedule);
            model.addAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
            return VIEW_FORM;
        }
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permission.AMORTIZATION_EDIT + "')")
    public String update(
            @PathVariable UUID id,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "false") boolean autoPost,
            @RequestParam(defaultValue = "1") Integer postDay,
            RedirectAttributes redirectAttributes) {

        AmortizationSchedule schedule = new AmortizationSchedule();
        schedule.setName(name);
        schedule.setDescription(description);
        schedule.setAutoPost(autoPost);
        schedule.setPostDay(postDay);

        try {
            scheduleService.update(id, schedule);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Jadwal amortisasi berhasil diperbarui");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
        }
        return REDIRECT_AMORTIZATION_PREFIX + id;
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('" + Permission.AMORTIZATION_EDIT + "')")
    public String cancel(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            scheduleService.cancel(id);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Jadwal amortisasi berhasil dibatalkan");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
        }
        return REDIRECT_AMORTIZATION_PREFIX + id;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('" + Permission.AMORTIZATION_EDIT + "')")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            scheduleService.delete(id);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
            return REDIRECT_AMORTIZATION_PREFIX + id;
        }
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Jadwal amortisasi berhasil dihapus");
        return "redirect:/amortization";
    }

    // Entry operations

    @PostMapping("/{id}/entries/{entryId}/post")
    @PreAuthorize("hasAuthority('" + Permission.AMORTIZATION_POST + "')")
    public String postEntry(
            @PathVariable UUID id,
            @PathVariable UUID entryId,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model,
            RedirectAttributes redirectAttributes) {

        String error = null;
        try {
            entryService.postEntry(entryId);
        } catch (IllegalStateException e) {
            error = e.getMessage();
        }

        if ("true".equals(hxRequest)) {
            populateEntryModel(model, id);
            model.addAttribute(ATTR_ENTRY_ERROR_MESSAGE, error);
            return VIEW_ENTRY_TABLE;
        }

        redirectAttributes.addFlashAttribute(error == null ? ATTR_SUCCESS_MESSAGE : ATTR_ERROR_MESSAGE,
                error == null ? "Entry berhasil diposting" : error);
        return REDIRECT_AMORTIZATION_PREFIX + id;
    }

    @PostMapping("/{id}/entries/{entryId}/skip")
    @PreAuthorize("hasAuthority('" + Permission.AMORTIZATION_POST + "')")
    public String skipEntry(
            @PathVariable UUID id,
            @PathVariable UUID entryId,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model,
            RedirectAttributes redirectAttributes) {

        String error = null;
        try {
            entryService.skipEntry(entryId);
        } catch (IllegalStateException e) {
            error = e.getMessage();
        }

        if ("true".equals(hxRequest)) {
            populateEntryModel(model, id);
            model.addAttribute(ATTR_ENTRY_ERROR_MESSAGE, error);
            return VIEW_ENTRY_TABLE;
        }

        redirectAttributes.addFlashAttribute(error == null ? ATTR_SUCCESS_MESSAGE : ATTR_ERROR_MESSAGE,
                error == null ? "Entry berhasil dilewati" : error);
        return REDIRECT_AMORTIZATION_PREFIX + id;
    }

    @PostMapping("/{id}/entries/post-all")
    @PreAuthorize("hasAuthority('" + Permission.AMORTIZATION_POST + "')")
    public String postAllEntries(
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        try {
            entryService.postAllPending(id);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Semua entry pending berhasil diposting");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
        }
        return REDIRECT_AMORTIZATION_PREFIX + id;
    }

    @PostMapping("/batch/process")
    @PreAuthorize("hasAuthority('" + Permission.AMORTIZATION_POST + "')")
    public String processBatch(RedirectAttributes redirectAttributes) {
        AmortizationBatchService.BatchResult result = batchService.processAutoPostEntries(LocalDate.now());

        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("warningMessage",
                    String.format("Batch selesai: %d berhasil, %d gagal", result.successCount(), result.errorCount()));
        } else {
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                    String.format("Batch selesai: %d entry berhasil diproses", result.successCount()));
        }
        return "redirect:/amortization";
    }

    private void populateFormModel(Model model, boolean isEdit) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_AMORTIZATION);
        model.addAttribute("isEdit", isEdit);
        model.addAttribute(ATTR_TYPES, ScheduleType.values());
        model.addAttribute("frequencies", AmortizationFrequency.values());
        model.addAttribute("accounts", chartOfAccountRepository.findByActiveOrderByAccountCodeAsc(true));
    }

    private void populateEntryModel(Model model, UUID scheduleId) {
        List<AmortizationEntry> entries = entryService.findByScheduleId(scheduleId);
        model.addAttribute(ATTR_SCHEDULE, scheduleService.findById(scheduleId));
        model.addAttribute(ATTR_ENTRIES, entries);
        model.addAttribute("transactionIds", entryService.findTransactionIdsByEntryId(entries));
    }
}
