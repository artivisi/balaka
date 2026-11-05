package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.ArchiveDocument;
import com.artivisi.accountingfinance.enums.ArchiveDocumentType;
import com.artivisi.accountingfinance.enums.ArchiveSource;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.DocumentArchiveService;
import com.artivisi.accountingfinance.service.DocumentArchiveService.ArchiveImportException;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.ATTR_CURRENT_PAGE;
import static com.artivisi.accountingfinance.controller.ViewConstants.PAGE_DOCUMENT_ARCHIVE;

/** Historical document archive (issue #51): search, bulk import from a ZIP with manifest, ledger link. */
@Controller
@RequestMapping("/tax-audit/archive")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.TAX_AUDIT_VIEW + "')")
public class DocumentArchiveController {

    private static final String FLASH_SUCCESS = "successMessage";
    private static final String FLASH_ERROR = "errorMessage";
    private static final String MANAGE = "hasAuthority('" + Permission.TAX_AUDIT_MANAGE + "')";

    private final DocumentArchiveService archiveService;
    private final SecurityAuditService securityAuditService;

    @GetMapping
    public String search(@RequestParam(required = false) String q, @RequestParam(required = false) Integer year,
                         @RequestParam(required = false) ArchiveDocumentType type,
                         @RequestParam(required = false) ArchiveSource source, Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_DOCUMENT_ARCHIVE);
        model.addAttribute("q", q);
        model.addAttribute("year", year);
        model.addAttribute("type", type);
        model.addAttribute("source", source);
        model.addAttribute("types", ArchiveDocumentType.values());
        model.addAttribute("sources", ArchiveSource.values());
        model.addAttribute("searched", true);
        model.addAttribute("hits", archiveService.search(q, year, type, source));
        return "tax-audit/archive";
    }

    @GetMapping("/import")
    @PreAuthorize(MANAGE)
    public String importForm(Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_DOCUMENT_ARCHIVE);
        model.addAttribute("requiredColumns", DocumentArchiveService.REQUIRED_COLUMNS);
        model.addAttribute("optionalColumns", DocumentArchiveService.OPTIONAL_COLUMNS);
        model.addAttribute("types", ArchiveDocumentType.values());
        model.addAttribute("sources", ArchiveSource.values());
        return "tax-audit/archive-import";
    }

    @PostMapping("/import")
    @PreAuthorize(MANAGE)
    public String importZip(@RequestParam("file") MultipartFile file, Authentication authentication,
                            RedirectAttributes redirectAttributes) throws IOException {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute(FLASH_ERROR, "Pilih file ZIP");
            return "redirect:/tax-audit/archive/import";
        }
        try (InputStream in = file.getInputStream()) {
            DocumentArchiveService.ImportResult result = archiveService.importZip(file.getOriginalFilename(), in,
                    authentication.getName(), LocalDate.now());
            securityAuditService.log(AuditEventType.DATA_IMPORT, "Archive import " + file.getOriginalFilename()
                    + ": " + result.documentCount() + " documents");
            redirectAttributes.addFlashAttribute(FLASH_SUCCESS, result.documentCount() + " dokumen diimpor ke arsip");
            return "redirect:/tax-audit/archive";
        } catch (ArchiveImportException e) {
            redirectAttributes.addFlashAttribute(FLASH_ERROR, "Impor ditolak, tidak ada dokumen yang disimpan.");
            redirectAttributes.addFlashAttribute("importErrors", e.getErrors());
            return "redirect:/tax-audit/archive/import";
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute(FLASH_ERROR, e.getMessage());
            return "redirect:/tax-audit/archive/import";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_DOCUMENT_ARCHIVE);
        model.addAttribute("doc", archiveService.find(id));
        return "tax-audit/archive-detail";
    }

    @PostMapping("/{id}/link")
    @PreAuthorize(MANAGE)
    public String link(@PathVariable UUID id, @RequestParam(required = false) String transactionNumber,
                       @RequestParam(required = false) String accountCode, RedirectAttributes redirectAttributes) {
        try {
            ArchiveDocument doc = archiveService.link(id, transactionNumber, accountCode);
            redirectAttributes.addFlashAttribute(FLASH_SUCCESS, doc.getTransaction() == null ? "Tautan ke buku besar dihapus"
                    : "Ditautkan ke " + doc.getTransaction().getTransactionNumber());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(FLASH_ERROR, e.getMessage());
        }
        return "redirect:/tax-audit/archive/" + id;
    }

    @GetMapping("/{id}/file")
    @ResponseBody
    public ResponseEntity<Resource> file(@PathVariable UUID id) {
        ArchiveDocument doc = archiveService.find(id);
        securityAuditService.log(AuditEventType.DOCUMENT_DOWNLOAD,
                "Archive document " + doc.getDocument().getOriginalFilename() + " (id: " + id + ")");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getDocument().getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(doc.getDocument().getOriginalFilename(), StandardCharsets.UTF_8).build().toString())
                .body(archiveService.load(id));
    }
}
