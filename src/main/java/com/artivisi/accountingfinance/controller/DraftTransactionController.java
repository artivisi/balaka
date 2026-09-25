package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.DraftTransaction;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.DraftTransactionService;
import com.artivisi.accountingfinance.service.JournalTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@io.swagger.v3.oas.annotations.Hidden
@Controller
@RequestMapping("/drafts")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.DRAFT_VIEW + "')")
public class DraftTransactionController {

    private static final String USER_SYSTEM = "system";
    private static final String REDIRECT_DRAFTS = "redirect:/drafts";

    private final DraftTransactionService draftService;
    private final JournalTemplateService templateService;

    @GetMapping
    public String list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_DRAFTS);

        DraftTransaction.Status statusEnum = null;
        if (status != null && !status.isEmpty()) {
            statusEnum = DraftTransaction.Status.valueOf(status);
        }

        Page<DraftTransaction> draftPage = draftService.findByFilters(statusEnum, PageRequest.of(page, size));
        model.addAttribute("drafts", draftPage.getContent());
        model.addAttribute("page", draftPage);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", DraftTransaction.Status.values());
        model.addAttribute("pendingCount", draftService.countPending());

        if ("true".equals(hxRequest)) {
            return "fragments/draft-table :: table";
        }
        return "drafts/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        DraftTransaction draft = draftService.findById(id);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_DRAFTS);
        model.addAttribute("draft", draft);
        model.addAttribute("templates", templateService.findAll());
        return "drafts/detail";
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('" + Permission.DRAFT_APPROVE + "')")
    public String approve(
            @PathVariable UUID id,
            @RequestParam UUID templateId,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) BigDecimal amount,
            Authentication authentication,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        String username = authentication != null ? authentication.getName() : USER_SYSTEM;

        DraftTransaction approvedDraft = draftService.approve(id, templateId, description, amount, username);

        if ("true".equals(hxRequest)) {
            return REDIRECT_DRAFTS;
        }
        // Redirect to edit page to show journal preview
        return "redirect:/transactions/" + approvedDraft.getTransaction().getId() + "/edit";
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('" + Permission.DRAFT_REJECT + "')")
    public String reject(
            @PathVariable UUID id,
            @RequestParam String reason,
            Authentication authentication,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        String username = authentication != null ? authentication.getName() : USER_SYSTEM;
        draftService.reject(id, reason, username);

        if ("true".equals(hxRequest)) {
            return REDIRECT_DRAFTS;
        }
        return REDIRECT_DRAFTS;
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('" + Permission.DRAFT_REJECT + "')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        draftService.delete(id);
        return ResponseEntity.ok().build();
    }

    // REST API Endpoints

    @GetMapping("/api")
    @ResponseBody
    public ResponseEntity<Page<DraftTransaction>> apiList(
            @RequestParam(required = false) DraftTransaction.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(draftService.findByFilters(status, PageRequest.of(page, size)));
    }

    @GetMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<DraftTransaction> apiGet(@PathVariable UUID id) {
        return ResponseEntity.ok(draftService.findById(id));
    }

    @PostMapping("/api/{id}/approve")
    @ResponseBody
    @PreAuthorize("hasAuthority('" + Permission.DRAFT_APPROVE + "')")
    public ResponseEntity<DraftTransaction> apiApprove(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : USER_SYSTEM;
        UUID templateId = UUID.fromString((String) body.get("templateId"));
        String description = (String) body.get("description");
        BigDecimal amount = body.get("amount") != null ? new BigDecimal(body.get("amount").toString()) : null;

        DraftTransaction approvedDraft = draftService.approve(id, templateId, description, amount, username);
        return ResponseEntity.ok(approvedDraft);
    }

    @PostMapping("/api/{id}/reject")
    @ResponseBody
    @PreAuthorize("hasAuthority('" + Permission.DRAFT_REJECT + "')")
    public ResponseEntity<DraftTransaction> apiReject(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : USER_SYSTEM;
        String reason = body.get("reason");

        return ResponseEntity.ok(draftService.reject(id, reason, username));
    }
}
