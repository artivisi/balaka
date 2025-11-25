package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.AmortizationEntry;
import com.artivisi.accountingfinance.entity.AmortizationSchedule;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.enums.AmortizationFrequency;
import com.artivisi.accountingfinance.enums.ScheduleStatus;
import com.artivisi.accountingfinance.enums.ScheduleType;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.service.AmortizationBatchService;
import com.artivisi.accountingfinance.service.AmortizationEntryService;
import com.artivisi.accountingfinance.service.AmortizationScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

@Controller
@RequestMapping("/amortization")
@RequiredArgsConstructor
public class AmortizationController {

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

        model.addAttribute("currentPage", "amortization");
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedType", type);
        model.addAttribute("searchQuery", search);
        model.addAttribute("statuses", ScheduleStatus.values());
        model.addAttribute("types", ScheduleType.values());

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
    public String create(Model model) {
        model.addAttribute("currentPage", "amortization");
        model.addAttribute("isEdit", false);
        model.addAttribute("types", ScheduleType.values());
        model.addAttribute("frequencies", AmortizationFrequency.values());
        model.addAttribute("accounts", chartOfAccountRepository.findByActiveOrderByAccountCodeAsc(true));
        return "amortization/form";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        model.addAttribute("currentPage", "amortization");
        AmortizationSchedule schedule = scheduleService.findById(id);
        List<AmortizationEntry> entries = entryService.findByScheduleId(id);

        model.addAttribute("schedule", schedule);
        model.addAttribute("entries", entries);
        return "amortization/detail";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        model.addAttribute("currentPage", "amortization");
        model.addAttribute("isEdit", true);
        model.addAttribute("schedule", scheduleService.findById(id));
        model.addAttribute("types", ScheduleType.values());
        model.addAttribute("frequencies", AmortizationFrequency.values());
        model.addAttribute("accounts", chartOfAccountRepository.findByActiveOrderByAccountCodeAsc(true));
        return "amortization/form";
    }

    @PostMapping
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

        AmortizationSchedule saved = scheduleService.create(schedule);
        redirectAttributes.addFlashAttribute("successMessage", "Jadwal amortisasi berhasil dibuat");
        return "redirect:/amortization/" + saved.getId();
    }

    @PostMapping("/{id}")
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

        scheduleService.update(id, schedule);
        redirectAttributes.addFlashAttribute("successMessage", "Jadwal amortisasi berhasil diperbarui");
        return "redirect:/amortization/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        scheduleService.cancel(id);
        redirectAttributes.addFlashAttribute("successMessage", "Jadwal amortisasi berhasil dibatalkan");
        return "redirect:/amortization/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        scheduleService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Jadwal amortisasi berhasil dihapus");
        return "redirect:/amortization";
    }

    // Entry operations

    @PostMapping("/{id}/entries/{entryId}/post")
    public String postEntry(
            @PathVariable UUID id,
            @PathVariable UUID entryId,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model,
            RedirectAttributes redirectAttributes) {

        entryService.postEntry(entryId);

        if ("true".equals(hxRequest)) {
            AmortizationSchedule schedule = scheduleService.findById(id);
            List<AmortizationEntry> entries = entryService.findByScheduleId(id);
            model.addAttribute("schedule", schedule);
            model.addAttribute("entries", entries);
            return "amortization/fragments/entry-table :: table";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Entry berhasil diposting");
        return "redirect:/amortization/" + id;
    }

    @PostMapping("/{id}/entries/{entryId}/skip")
    public String skipEntry(
            @PathVariable UUID id,
            @PathVariable UUID entryId,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model,
            RedirectAttributes redirectAttributes) {

        entryService.skipEntry(entryId);

        if ("true".equals(hxRequest)) {
            AmortizationSchedule schedule = scheduleService.findById(id);
            List<AmortizationEntry> entries = entryService.findByScheduleId(id);
            model.addAttribute("schedule", schedule);
            model.addAttribute("entries", entries);
            return "amortization/fragments/entry-table :: table";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Entry berhasil dilewati");
        return "redirect:/amortization/" + id;
    }

    @PostMapping("/{id}/entries/post-all")
    public String postAllEntries(
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        entryService.postAllPending(id);
        redirectAttributes.addFlashAttribute("successMessage", "Semua entry pending berhasil diposting");
        return "redirect:/amortization/" + id;
    }

    @PostMapping("/batch/process")
    public String processBatch(RedirectAttributes redirectAttributes) {
        AmortizationBatchService.BatchResult result = batchService.processAutoPostEntries(LocalDate.now());

        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("warningMessage",
                    String.format("Batch selesai: %d berhasil, %d gagal", result.successCount(), result.errorCount()));
        } else {
            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Batch selesai: %d entry berhasil diproses", result.successCount()));
        }
        return "redirect:/amortization";
    }
}
