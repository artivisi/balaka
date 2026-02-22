package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.RecurringTransaction;
import com.artivisi.accountingfinance.entity.RecurringTransactionLog;
import com.artivisi.accountingfinance.enums.RecurringFrequency;
import com.artivisi.accountingfinance.enums.RecurringStatus;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.JournalTemplateService;
import com.artivisi.accountingfinance.service.RecurringTransactionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequestMapping("/recurring")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.RECURRING_VIEW + "')")
public class RecurringTransactionController {

    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String ATTR_RECURRING = "recurring";
    private static final String ATTR_FREQUENCIES = "frequencies";

    private final RecurringTransactionService recurringTransactionService;
    private final JournalTemplateService journalTemplateService;

    @GetMapping
    public String list(
            @RequestParam(required = false) RecurringStatus status,
            @PageableDefault(size = 20) Pageable pageable,
            Model model) {
        Page<RecurringTransaction> page = recurringTransactionService.findAll(status, pageable);
        model.addAttribute("recurringList", page);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", RecurringStatus.values());
        model.addAttribute("activeCount", recurringTransactionService.countByStatus(RecurringStatus.ACTIVE));
        model.addAttribute("pausedCount", recurringTransactionService.countByStatus(RecurringStatus.PAUSED));
        model.addAttribute("completedCount", recurringTransactionService.countByStatus(RecurringStatus.COMPLETED));
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_RECURRING);
        return "recurring/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.RECURRING_CREATE + "')")
    public String newForm(Model model) {
        model.addAttribute(ATTR_RECURRING, new RecurringTransaction());
        model.addAttribute(ATTR_TEMPLATES, journalTemplateService.findAll());
        model.addAttribute(ATTR_FREQUENCIES, RecurringFrequency.values());
        model.addAttribute(ATTR_IS_EDIT, false);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_RECURRING);
        return "recurring/form";
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.RECURRING_CREATE + "')")
    public String create(
            @RequestParam String name,
            @RequestParam UUID journalTemplateId,
            @RequestParam BigDecimal amount,
            @RequestParam String description,
            @RequestParam RecurringFrequency frequency,
            @RequestParam(required = false) Integer dayOfMonth,
            @RequestParam(required = false) Integer dayOfWeek,
            @RequestParam LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "false") boolean skipWeekends,
            @RequestParam(required = false, defaultValue = "false") boolean autoPost,
            @RequestParam(required = false) Integer maxOccurrences,
            HttpServletRequest request,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        RecurringTransaction entity = new RecurringTransaction();
        entity.setName(name);
        var templateRef = new com.artivisi.accountingfinance.entity.JournalTemplate();
        templateRef.setId(journalTemplateId);
        entity.setJournalTemplate(templateRef);
        entity.setAmount(amount);
        entity.setDescription(description);
        entity.setFrequency(frequency);
        entity.setDayOfMonth(dayOfMonth);
        entity.setDayOfWeek(dayOfWeek);
        entity.setStartDate(startDate);
        entity.setEndDate(endDate);
        entity.setSkipWeekends(skipWeekends);
        entity.setAutoPost(autoPost);
        entity.setMaxOccurrences(maxOccurrences);
        entity.setCreatedBy(authentication.getName());

        Map<UUID, UUID> accountMappings = extractAccountMappings(request);

        recurringTransactionService.create(entity, accountMappings);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Transaksi berulang berhasil dibuat");
        return REDIRECT_RECURRING;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        RecurringTransaction recurring = recurringTransactionService.findById(id);
        List<RecurringTransactionLog> logs = recurringTransactionService.findLogsByRecurringId(id);

        // Preview next 5 occurrences
        List<LocalDate> preview = List.of();
        if (recurring.isActive() && recurring.getNextRunDate() != null) {
            preview = recurringTransactionService.previewOccurrences(
                    recurring.getFrequency(), recurring.getDayOfMonth(), recurring.getDayOfWeek(),
                    recurring.getNextRunDate(), recurring.isSkipWeekends(),
                    recurring.getEndDate(), recurring.getMaxOccurrences() != null
                            ? recurring.getMaxOccurrences() - recurring.getTotalRuns() : null, 5);
        }

        model.addAttribute(ATTR_RECURRING, recurring);
        model.addAttribute("logs", logs);
        model.addAttribute("preview", preview);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_RECURRING);
        return "recurring/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('" + Permission.RECURRING_EDIT + "')")
    public String editForm(@PathVariable UUID id, Model model) {
        RecurringTransaction recurring = recurringTransactionService.findByIdWithMappings(id);
        if (recurring.isCompleted()) {
            return REDIRECT_RECURRING + "/" + id;
        }
        model.addAttribute(ATTR_RECURRING, recurring);
        model.addAttribute(ATTR_TEMPLATES, journalTemplateService.findAll());
        model.addAttribute(ATTR_FREQUENCIES, RecurringFrequency.values());
        model.addAttribute(ATTR_IS_EDIT, true);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_RECURRING);
        return "recurring/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permission.RECURRING_EDIT + "')")
    public String update(
            @PathVariable UUID id,
            @RequestParam String name,
            @RequestParam UUID journalTemplateId,
            @RequestParam BigDecimal amount,
            @RequestParam String description,
            @RequestParam RecurringFrequency frequency,
            @RequestParam(required = false) Integer dayOfMonth,
            @RequestParam(required = false) Integer dayOfWeek,
            @RequestParam LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "false") boolean skipWeekends,
            @RequestParam(required = false, defaultValue = "false") boolean autoPost,
            @RequestParam(required = false) Integer maxOccurrences,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        RecurringTransaction data = new RecurringTransaction();
        data.setName(name);
        var templateRef = new com.artivisi.accountingfinance.entity.JournalTemplate();
        templateRef.setId(journalTemplateId);
        data.setJournalTemplate(templateRef);
        data.setAmount(amount);
        data.setDescription(description);
        data.setFrequency(frequency);
        data.setDayOfMonth(dayOfMonth);
        data.setDayOfWeek(dayOfWeek);
        data.setStartDate(startDate);
        data.setEndDate(endDate);
        data.setSkipWeekends(skipWeekends);
        data.setAutoPost(autoPost);
        data.setMaxOccurrences(maxOccurrences);

        Map<UUID, UUID> accountMappings = extractAccountMappings(request);

        recurringTransactionService.update(id, data, accountMappings);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Transaksi berulang berhasil diperbarui");
        return REDIRECT_RECURRING + "/" + id;
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAuthority('" + Permission.RECURRING_EDIT + "')")
    public String pause(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        recurringTransactionService.pause(id);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Transaksi berulang dijeda");
        return REDIRECT_RECURRING + "/" + id;
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAuthority('" + Permission.RECURRING_EDIT + "')")
    public String resume(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        recurringTransactionService.resume(id);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Transaksi berulang dilanjutkan");
        return REDIRECT_RECURRING + "/" + id;
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('" + Permission.RECURRING_EDIT + "')")
    public String complete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        recurringTransactionService.complete(id);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Transaksi berulang diselesaikan");
        return REDIRECT_RECURRING + "/" + id;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('" + Permission.RECURRING_DELETE + "')")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        recurringTransactionService.delete(id);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Transaksi berulang dihapus");
        return REDIRECT_RECURRING;
    }

    private Map<UUID, UUID> extractAccountMappings(HttpServletRequest request) {
        Map<UUID, UUID> mappings = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (key.startsWith("accountMapping_") && values.length > 0 && !values[0].isBlank()) {
                String lineId = key.substring("accountMapping_".length());
                mappings.put(UUID.fromString(lineId), UUID.fromString(values[0]));
            }
        });
        return mappings;
    }
}
