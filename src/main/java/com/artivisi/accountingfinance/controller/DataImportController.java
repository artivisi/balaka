package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.dto.dataimport.COAImportFileDto;
import com.artivisi.accountingfinance.dto.dataimport.ImportPreview;
import com.artivisi.accountingfinance.dto.dataimport.ImportResult;
import com.artivisi.accountingfinance.dto.dataimport.TemplateImportFileDto;
import com.artivisi.accountingfinance.service.DataImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
@RequestMapping("/import")
@RequiredArgsConstructor
@Slf4j
public class DataImportController {

    private final DataImportService dataImportService;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("currentPage", "import");
        model.addAttribute("canClearAccounts", dataImportService.canClearData());
        model.addAttribute("canClearTemplates", dataImportService.canClearTemplates());
        return "import/index";
    }

    // ========================= COA Import =========================

    @GetMapping("/coa")
    public String coaImportPage(Model model) {
        model.addAttribute("currentPage", "import");
        model.addAttribute("canClearAccounts", dataImportService.canClearData());
        return "import/coa-import";
    }

    @PostMapping("/coa/preview")
    public String previewCOA(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
            Model model) {

        if (file.isEmpty()) {
            model.addAttribute("errorMessage", "File tidak boleh kosong");
            return htmxRequest != null ? "import/fragments :: preview-error" : "import/coa-import";
        }

        String filename = file.getOriginalFilename();
        if (filename == null ||
            (!filename.toLowerCase().endsWith(".json") && !filename.toLowerCase().endsWith(".xlsx"))) {
            model.addAttribute("errorMessage", "Format file tidak didukung. Gunakan JSON atau XLSX");
            return htmxRequest != null ? "import/fragments :: preview-error" : "import/coa-import";
        }

        try {
            COAImportFileDto importFile;
            if (filename.toLowerCase().endsWith(".json")) {
                importFile = dataImportService.parseCOAJsonFile(file);
            } else {
                importFile = dataImportService.parseCOAExcelFile(file);
            }

            ImportPreview preview = dataImportService.previewCOA(importFile);
            model.addAttribute("preview", preview);
            model.addAttribute("canClearAccounts", dataImportService.canClearData());

            return htmxRequest != null ? "import/fragments :: coa-preview" : "import/coa-import";
        } catch (IOException e) {
            log.error("Error parsing COA file: {}", e.getMessage());
            model.addAttribute("errorMessage", "Error membaca file: " + e.getMessage());
            return htmxRequest != null ? "import/fragments :: preview-error" : "import/coa-import";
        }
    }

    @PostMapping("/coa")
    public String importCOA(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "clearExisting", defaultValue = "false") boolean clearExisting,
            RedirectAttributes redirectAttributes) {

        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "File tidak boleh kosong");
            return "redirect:/import/coa";
        }

        String filename = file.getOriginalFilename();
        if (filename == null ||
            (!filename.toLowerCase().endsWith(".json") && !filename.toLowerCase().endsWith(".xlsx"))) {
            redirectAttributes.addFlashAttribute("errorMessage", "Format file tidak didukung. Gunakan JSON atau XLSX");
            return "redirect:/import/coa";
        }

        try {
            COAImportFileDto importFile;
            if (filename.toLowerCase().endsWith(".json")) {
                importFile = dataImportService.parseCOAJsonFile(file);
            } else {
                importFile = dataImportService.parseCOAExcelFile(file);
            }

            ImportResult result = dataImportService.importCOA(importFile, clearExisting);
            redirectAttributes.addFlashAttribute("importResult", result);

            if (result.success()) {
                redirectAttributes.addFlashAttribute("successMessage", result.message());
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", result.message());
            }

            return "redirect:/import/result";
        } catch (IllegalStateException e) {
            log.error("Cannot clear COA: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/import/coa";
        } catch (IOException e) {
            log.error("Error importing COA: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error import: " + e.getMessage());
            return "redirect:/import/coa";
        }
    }

    @PostMapping("/coa/clear")
    public String clearCOA(RedirectAttributes redirectAttributes) {
        try {
            dataImportService.clearAllData();
            redirectAttributes.addFlashAttribute("successMessage", "Semua bagan akun berhasil dihapus");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/import";
    }

    // ========================= Template Import =========================

    @GetMapping("/templates")
    public String templateImportPage(Model model) {
        model.addAttribute("currentPage", "import");
        model.addAttribute("canClearTemplates", dataImportService.canClearTemplates());
        return "import/template-import";
    }

    @PostMapping("/templates/preview")
    public String previewTemplates(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
            Model model) {

        if (file.isEmpty()) {
            model.addAttribute("errorMessage", "File tidak boleh kosong");
            return htmxRequest != null ? "import/fragments :: preview-error" : "import/template-import";
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".json")) {
            model.addAttribute("errorMessage", "Format file tidak didukung. Gunakan JSON");
            return htmxRequest != null ? "import/fragments :: preview-error" : "import/template-import";
        }

        try {
            TemplateImportFileDto importFile = dataImportService.parseTemplateJsonFile(file);
            ImportPreview preview = dataImportService.previewTemplate(importFile);
            model.addAttribute("preview", preview);
            model.addAttribute("canClearTemplates", dataImportService.canClearTemplates());

            return htmxRequest != null ? "import/fragments :: template-preview" : "import/template-import";
        } catch (IOException e) {
            log.error("Error parsing template file: {}", e.getMessage());
            model.addAttribute("errorMessage", "Error membaca file: " + e.getMessage());
            return htmxRequest != null ? "import/fragments :: preview-error" : "import/template-import";
        }
    }

    @PostMapping("/templates")
    public String importTemplates(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "clearExisting", defaultValue = "false") boolean clearExisting,
            RedirectAttributes redirectAttributes) {

        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "File tidak boleh kosong");
            return "redirect:/import/templates";
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".json")) {
            redirectAttributes.addFlashAttribute("errorMessage", "Format file tidak didukung. Gunakan JSON");
            return "redirect:/import/templates";
        }

        try {
            TemplateImportFileDto importFile = dataImportService.parseTemplateJsonFile(file);
            ImportResult result = dataImportService.importTemplate(importFile, clearExisting);
            redirectAttributes.addFlashAttribute("importResult", result);

            if (result.success()) {
                redirectAttributes.addFlashAttribute("successMessage", result.message());
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", result.message());
            }

            return "redirect:/import/result";
        } catch (IllegalStateException e) {
            log.error("Cannot clear templates: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/import/templates";
        } catch (IOException e) {
            log.error("Error importing templates: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error import: " + e.getMessage());
            return "redirect:/import/templates";
        }
    }

    @PostMapping("/templates/clear")
    public String clearTemplates(RedirectAttributes redirectAttributes) {
        try {
            dataImportService.clearAllTemplates();
            redirectAttributes.addFlashAttribute("successMessage", "Semua template berhasil dihapus");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/import";
    }

    // ========================= Result & Downloads =========================

    @GetMapping("/result")
    public String resultPage(Model model) {
        model.addAttribute("currentPage", "import");
        return "import/result";
    }

    @GetMapping("/download/coa-sample")
    public ResponseEntity<Resource> downloadCOASample() {
        Resource resource = new ClassPathResource("import-templates/coa-sample.json");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"coa-sample.json\"")
                .body(resource);
    }

    @GetMapping("/download/template-sample")
    public ResponseEntity<Resource> downloadTemplateSample() {
        Resource resource = new ClassPathResource("import-templates/template-sample.json");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"template-sample.json\"")
                .body(resource);
    }
}
