package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.AssetDisposal;
import com.artivisi.accountingfinance.entity.DisposalType;
import com.artivisi.accountingfinance.entity.FixedAsset;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.AssetDisposalService;
import com.artivisi.accountingfinance.service.AssetRegisterQueryService;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import com.artivisi.accountingfinance.service.FixedAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.ATTR_CURRENT_PAGE;
import static com.artivisi.accountingfinance.controller.ViewConstants.PAGE_ASSETS;

@Controller
@RequestMapping("/assets")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.ASSET_VIEW + "')")
public class AssetDisposalController {

    private static final String REDIRECT_DISPOSALS = "redirect:/assets/disposals/";
    private static final String ATTR_ERROR = "errorMessage";
    private static final String ATTR_SUCCESS = "successMessage";

    private final AssetDisposalService disposalService;
    private final AssetRegisterQueryService queryService;
    private final FixedAssetService fixedAssetService;
    private final ChartOfAccountService chartOfAccountService;

    @GetMapping("/{id}/dispose")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_DISPOSE + "')")
    public String form(@PathVariable UUID id, Model model) {
        FixedAsset asset = fixedAssetService.findByIdWithDetails(id);
        if (asset.isDisposed() || fixedAssetService.hasDraftDisposal(id)) {
            return "redirect:/assets/" + id;
        }
        model.addAttribute("disposalDate", LocalDate.now());
        return formView(asset, model);
    }

    /** HTMX: the tax effect from the single calculator, so the form never does its own arithmetic. */
    @GetMapping("/{id}/dispose/preview")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_DISPOSE + "')")
    public String preview(@PathVariable UUID id,
                          @RequestParam(required = false) DisposalType disposalType,
                          @RequestParam(required = false) BigDecimal proceeds,
                          Model model) {
        if (disposalType == DisposalType.SOLD && proceeds == null) {
            model.addAttribute("prompt", "Isi hasil penjualan untuk melihat laba/rugi.");
        } else if (disposalType == null) {
            model.addAttribute("prompt", "Pilih tipe pelepasan untuk melihat laba/rugi dan pengaruh pajak.");
        } else {
            try {
                model.addAttribute("effect", disposalService.preview(id, disposalType,
                        disposalType == DisposalType.SOLD ? proceeds : null));
            } catch (IllegalArgumentException e) {
                model.addAttribute("prompt", e.getMessage());
            }
        }
        return "assets/fragments/disposal-effect :: preview";
    }

    @PostMapping("/{id}/dispose")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_DISPOSE + "')")
    public String dispose(@PathVariable UUID id,
                          @RequestParam DisposalType disposalType,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate disposalDate,
                          @RequestParam(required = false) BigDecimal proceeds,
                          @RequestParam(required = false) UUID proceedsAccount,
                          @RequestParam(required = false) String counterparty,
                          @RequestParam(required = false) String reason,
                          Authentication authentication,
                          Model model,
                          RedirectAttributes redirectAttributes) {
        try {
            AssetDisposal disposal = disposalService.create(new AssetDisposalService.CreateDisposal(
                    id, disposalType, disposalDate, proceeds, proceedsAccount, counterparty, reason, authentication.getName()));
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS, "Catatan pelepasan " + disposal.getRecordNumber()
                    + " dibuat. Aset berstatus Dilepas setelah draf jurnalnya diposting.");
            return REDIRECT_DISPOSALS + disposal.getId();
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute(ATTR_ERROR, e.getMessage());
            model.addAttribute("disposalType", disposalType);
            model.addAttribute("disposalDate", disposalDate);
            model.addAttribute("proceeds", proceeds);
            model.addAttribute("counterparty", counterparty);
            model.addAttribute("reason", reason);
            if (proceedsAccount != null) {
                var account = chartOfAccountService.findById(proceedsAccount);
                model.addAttribute("proceedsAccount", proceedsAccount);
                model.addAttribute("proceedsAccountLabel", account.getAccountCode() + " - " + account.getAccountName());
            }
            return formView(fixedAssetService.findByIdWithDetails(id), model);
        }
    }

    @GetMapping("/disposals")
    public String list(@RequestParam(required = false) Integer year, Model model) {
        int selected = year != null ? year : LocalDate.now().getYear();
        model.addAttribute("year", selected);
        model.addAttribute("disposals", queryService.findDisposals(
                LocalDate.of(selected, 1, 1), LocalDate.of(selected, 12, 31), null));
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ASSETS);
        return "assets/disposals";
    }

    @GetMapping("/disposals/{disposalId}")
    public String record(@PathVariable UUID disposalId, Model model) {
        AssetDisposal disposal = disposalService.findById(disposalId);
        model.addAttribute("disposal", disposal);
        model.addAttribute("effect", disposalService.taxEffect(disposal));
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ASSETS);
        return "assets/disposal-record";
    }

    @PostMapping("/disposals/{disposalId}/cancel")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_DISPOSE + "')")
    public String cancel(@PathVariable UUID disposalId, RedirectAttributes redirectAttributes) {
        try {
            disposalService.cancel(disposalId);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS, "Pelepasan dibatalkan dan draf jurnalnya dihapus.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR, e.getMessage());
        }
        return REDIRECT_DISPOSALS + disposalId;
    }

    private String formView(FixedAsset asset, Model model) {
        model.addAttribute("asset", asset);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ASSETS);
        return "assets/dispose";
    }
}
