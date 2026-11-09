package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.Document;
import com.artivisi.accountingfinance.entity.TaxInquiryEntry;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.enums.SelfTestLine;
import com.artivisi.accountingfinance.enums.TaxInquiryEntryType;
import com.artivisi.accountingfinance.enums.TaxInquiryStatus;
import com.artivisi.accountingfinance.enums.TaxInquiryType;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.DocumentService;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import com.artivisi.accountingfinance.service.TaxInquiryService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.ATTR_CURRENT_PAGE;
import static com.artivisi.accountingfinance.controller.ViewConstants.PAGE_TAX_INQUIRY;

/** Inquiry case record (issue #51). Every write appends; nothing recorded is edited. */
@Controller
@RequestMapping("/tax-audit/cases")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.TAX_AUDIT_VIEW + "')")
public class TaxInquiryController {

    private static final String FLASH_SUCCESS = "successMessage";
    private static final String FLASH_ERROR = "errorMessage";
    private static final String REDIRECT_CASE = "redirect:/tax-audit/cases/";
    private static final String MANAGE = "hasAuthority('" + Permission.TAX_AUDIT_MANAGE + "')";

    private final TaxInquiryService inquiryService;
    private final DocumentService documentService;
    private final SecurityAuditService securityAuditService;

    @GetMapping
    public String list(@RequestParam(required = false) Integer year, Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_TAX_INQUIRY);
        model.addAttribute("year", year);
        model.addAttribute("cases", inquiryService.findByYear(year));
        return "tax-audit/cases";
    }

    @GetMapping("/new")
    @PreAuthorize(MANAGE)
    public String newForm(@RequestParam(required = false) Integer year, Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_TAX_INQUIRY);
        model.addAttribute("year", year);
        model.addAttribute("types", TaxInquiryType.values());
        model.addAttribute("letters", year != null ? inquiryService.letterDocuments(year) : List.of());
        return "tax-audit/case-form";
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    public String create(@RequestParam(required = false) TaxInquiryType inquiryType,
                         @RequestParam(required = false) Integer taxYear,
                         @RequestParam(required = false) String letterNumber,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate letterDate,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedDate,
                         @RequestParam(required = false) String taxOffice,
                         @RequestParam(required = false) String subject,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadline,
                         @RequestParam(required = false) UUID taxDocumentId,
                         @RequestParam(required = false) MultipartFile letterFile,
                         Authentication authentication, RedirectAttributes redirectAttributes) throws IOException {
        try {
            var c = inquiryService.create(new TaxInquiryService.NewCase(inquiryType, taxYear, letterNumber, letterDate,
                    receivedDate, taxOffice, subject, deadline, null, taxDocumentId), letterFile, authentication.getName());
            redirectAttributes.addFlashAttribute(FLASH_SUCCESS, "Kasus " + c.getTitle() + " dibuat");
            return REDIRECT_CASE + c.getId();
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute(FLASH_ERROR, e.getMessage());
            return "redirect:/tax-audit/cases/new" + (taxYear != null ? "?year=" + taxYear : "");
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_TAX_INQUIRY);
        model.addAttribute("view", inquiryService.view(id));
        model.addAttribute("entryTypes", List.of(TaxInquiryEntryType.REPLY, TaxInquiryEntryType.MEETING, TaxInquiryEntryType.NOTE));
        model.addAttribute("statuses", List.of(TaxInquiryStatus.OPEN, TaxInquiryStatus.RESPONDED));
        model.addAttribute("selfTestLines", SelfTestLine.values());
        model.addAttribute("today", LocalDate.now());
        return "tax-audit/case-detail";
    }

    @PostMapping("/{id}/items")
    @PreAuthorize(MANAGE)
    public String addItem(@PathVariable UUID id, @RequestParam(required = false) String description,
                          @RequestParam(required = false) SelfTestLine selfTestLine,
                          @RequestParam(required = false) Integer periodMonth,
                          Authentication authentication, RedirectAttributes redirectAttributes) {
        return run(id, redirectAttributes, () -> {
            var item = inquiryService.addItem(id, description, selfTestLine, selfTestLine != null ? periodMonth : null,
                    authentication.getName());
            return "Permintaan " + item.getItemNo() + " dicatat";
        });
    }

    @PostMapping("/{id}/entries")
    @PreAuthorize(MANAGE)
    public String addEntry(@PathVariable UUID id, @RequestParam(required = false) TaxInquiryEntryType entryType,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate entryDate,
                           @RequestParam(required = false) String content, @RequestParam(required = false) UUID itemId,
                           @RequestParam(required = false) MultipartFile file,
                           Authentication authentication, RedirectAttributes redirectAttributes) {
        return run(id, redirectAttributes, () -> {
            TaxInquiryEntry e = inquiryService.addEntry(id, entryType, entryDate, content, itemId, file, authentication.getName());
            return e.getEntryType().getIndonesianName() + " dicatat";
        });
    }

    @PostMapping("/{id}/entries/{entryId}/correct")
    @PreAuthorize(MANAGE)
    public String correct(@PathVariable UUID id, @PathVariable UUID entryId, @RequestParam(required = false) String content,
                          Authentication authentication, RedirectAttributes redirectAttributes) {
        return run(id, redirectAttributes, () -> {
            inquiryService.correct(id, entryId, content, authentication.getName());
            return "Koreksi dicatat";
        });
    }

    @PostMapping("/{id}/deadline")
    @PreAuthorize(MANAGE)
    public String deadline(@PathVariable UUID id,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadline,
                           @RequestParam(required = false) String reason,
                           Authentication authentication, RedirectAttributes redirectAttributes) {
        return run(id, redirectAttributes, () -> {
            inquiryService.changeDeadline(id, deadline, reason, authentication.getName());
            return "Tenggat diubah menjadi " + deadline;
        });
    }

    @PostMapping("/{id}/status")
    @PreAuthorize(MANAGE)
    public String status(@PathVariable UUID id, @RequestParam(required = false) TaxInquiryStatus status,
                         @RequestParam(required = false) String reason,
                         Authentication authentication, RedirectAttributes redirectAttributes) {
        return run(id, redirectAttributes, () -> {
            inquiryService.changeStatus(id, status, reason, authentication.getName());
            return "Status diubah menjadi " + status.getIndonesianName();
        });
    }

    @PostMapping("/{id}/decision")
    @PreAuthorize(MANAGE)
    public String decision(@PathVariable UUID id,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate decidedOn,
                           @RequestParam(required = false) String decision,
                           Authentication authentication, RedirectAttributes redirectAttributes) {
        return run(id, redirectAttributes, () -> {
            inquiryService.recordDecision(id, decidedOn, decision, authentication.getName());
            return "Keputusan dicatat, kasus selesai";
        });
    }

    /** Candidate files for a hand-over: files of the listed transactions and archive hits. */
    @GetMapping("/{id}/export")
    @PreAuthorize(MANAGE)
    public String exportForm(@PathVariable UUID id, @RequestParam(required = false) String transactions,
                             @RequestParam(required = false) String q, @RequestParam(required = false) Integer archiveYear,
                             Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_TAX_INQUIRY);
        model.addAttribute("view", inquiryService.view(id));
        model.addAttribute("transactions", transactions);
        model.addAttribute("q", q);
        model.addAttribute("archiveYear", archiveYear);
        model.addAttribute("today", LocalDate.now());
        List<String> numbers = transactions == null ? List.of() : Arrays.stream(transactions.split("[,\\s]+"))
                .map(String::strip).filter(s -> !s.isEmpty()).toList();
        try {
            model.addAttribute("candidates", inquiryService.candidates(numbers, q, archiveYear));
        } catch (IllegalArgumentException e) {
            model.addAttribute(FLASH_ERROR, e.getMessage());
            model.addAttribute("candidates", List.of());
        }
        return "tax-audit/case-export";
    }

    /** Records the hand-over: documentIds selected, item_{documentId} the item each one answers. */
    @PostMapping("/{id}/export")
    @PreAuthorize(MANAGE)
    public String disclose(@PathVariable UUID id, @RequestParam MultiValueMap<String, String> params,
                           Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            Map<UUID, UUID> selection = new LinkedHashMap<>();
            for (String documentId : params.getOrDefault("documentIds", List.of())) {
                String item = params.getFirst("item_" + documentId);
                selection.put(UUID.fromString(documentId), item == null || item.isBlank() ? null : UUID.fromString(item));
            }
            String date = params.getFirst("disclosedOn");
            TaxInquiryEntry entry = inquiryService.disclose(id, selection,
                    date == null || date.isBlank() ? null : LocalDate.parse(date), params.getFirst("note"), authentication.getName());
            redirectAttributes.addFlashAttribute(FLASH_SUCCESS, "Penyerahan " + selection.size()
                    + " dokumen dicatat. Unduh file ZIP penyerahan di bawah.");
            redirectAttributes.addFlashAttribute("disclosureEntryId", entry.getId());
        } catch (IllegalArgumentException | IllegalStateException | java.time.format.DateTimeParseException e) {
            redirectAttributes.addFlashAttribute(FLASH_ERROR, e.getMessage());
            return REDIRECT_CASE + id + "/export";
        }
        return REDIRECT_CASE + id;
    }

    @GetMapping("/{id}/disclosures/{entryId}/download")
    public void downloadDisclosure(@PathVariable UUID id, @PathVariable UUID entryId, HttpServletResponse response)
            throws IOException {
        String filename = inquiryService.exportFilename(id, entryId);
        securityAuditService.log(AuditEventType.DATA_EXPORT, "Tax inquiry disclosure " + entryId + " of case " + id);
        java.io.ByteArrayOutputStream zip = new java.io.ByteArrayOutputStream();
        inquiryService.exportDisclosure(id, entryId, zip);
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString());
        response.getOutputStream().write(zip.toByteArray());
        response.flushBuffer();
    }

    @GetMapping("/{id}/documents/{documentId}")
    @ResponseBody
    public ResponseEntity<Resource> document(@PathVariable UUID id, @PathVariable UUID documentId) {
        Document document = inquiryService.caseDocument(id, documentId);
        securityAuditService.log(AuditEventType.DOCUMENT_DOWNLOAD,
                "Tax inquiry document " + document.getOriginalFilename() + " (id: " + documentId + ")");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(document.getOriginalFilename(), StandardCharsets.UTF_8).build().toString())
                .body(documentService.loadAsResource(documentId));
    }

    private interface Action {
        String run() throws IOException;
    }

    private String run(UUID id, RedirectAttributes redirectAttributes, Action action) {
        try {
            redirectAttributes.addFlashAttribute(FLASH_SUCCESS, action.run());
        } catch (IllegalArgumentException | IllegalStateException | IOException e) {
            redirectAttributes.addFlashAttribute(FLASH_ERROR, e.getMessage());
        }
        return REDIRECT_CASE + id;
    }
}
