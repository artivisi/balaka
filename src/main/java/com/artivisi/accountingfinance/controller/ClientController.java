package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.TaxTransactionDetail;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.TaxTransactionDetailRepository;
import com.artivisi.accountingfinance.service.ClientService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequestMapping("/clients")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.CLIENT_VIEW + "')")
public class ClientController {

    private static final String ATTR_CLIENT = "client";
    private static final String ATTR_CLIENT_CODE = "clientCode";
    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String REDIRECT_CLIENTS_PREFIX = "redirect:/clients/";
    private static final String VIEW_FORM = "clients/form";

    private final ClientService clientService;
    private final InvoiceRepository invoiceRepository;
    private final TaxTransactionDetailRepository taxTransactionDetailRepository;

    /**
     * Typeahead search for client pickers. Returns at most 10 active clients
     * matching q (by code or name). Used by every form that picks a client so
     * the picker never grows past a usable size.
     */
    @GetMapping("/search")
    @org.springframework.web.bind.annotation.ResponseBody
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public List<java.util.Map<String, Object>> search(
            @RequestParam(value = "q", required = false) String q) {
        Page<Client> page = clientService.findByFilters(
                Boolean.TRUE,
                q == null ? "" : q,
                org.springframework.data.domain.PageRequest.of(0, 10));
        List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
        for (Client c : page.getContent()) {
            results.add(java.util.Map.of(
                    "id", c.getId().toString(),
                    "code", c.getCode() == null ? "" : c.getCode(),
                    "name", c.getName() == null ? "" : c.getName()));
        }
        return results;
    }

    @Getter
    @Setter
    static class ClientForm {
        private UUID id;

        @NotBlank(message = "Kode klien wajib diisi")
        @Size(max = 50, message = "Kode klien maksimal 50 karakter")
        private String code;

        @NotBlank(message = "Nama klien wajib diisi")
        @Size(max = 255, message = "Nama klien maksimal 255 karakter")
        private String name;

        @Size(max = 255, message = "Nama kontak maksimal 255 karakter")
        private String contactPerson;

        @Email(message = "Format email tidak valid")
        @Size(max = 255, message = "Email maksimal 255 karakter")
        private String email;

        @Size(max = 50, message = "Nomor telepon maksimal 50 karakter")
        private String phone;

        private String address;
        private String notes;

        @Size(max = 10, message = "Tipe ID maksimal 10 karakter")
        private String idType;

        @Pattern(regexp = "^$|^\\d{2}\\.\\d{3}\\.\\d{3}\\.\\d-\\d{3}\\.\\d{3}$|^\\d{16}$",
                message = "Format NPWP tidak valid (XX.XXX.XXX.X-XXX.XXX atau 16 digit)")
        @Size(max = 20, message = "NPWP maksimal 20 karakter")
        private String npwp;

        @Size(max = 22, message = "NITKU maksimal 22 karakter")
        private String nitku;

        @Size(max = 16, message = "NIK maksimal 16 karakter")
        private String nik;

        private Boolean active;
    }

    private Client toEntity(ClientForm form) {
        Client entity = new Client();
        BeanUtils.copyProperties(form, entity, "id");
        return entity;
    }

    private ClientForm toForm(Client entity) {
        ClientForm form = new ClientForm();
        BeanUtils.copyProperties(entity, form);
        return form;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            @PageableDefault(size = 20) Pageable pageable,
            Model model) {

        Page<Client> clients = clientService.findByFilters(active, search, pageable);

        model.addAttribute("clients", clients);
        model.addAttribute("active", active);
        model.addAttribute("search", search);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_CLIENTS);

        if ("true".equals(hxRequest)) {
            return "clients/fragments/client-table :: table";
        }

        return "clients/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute(ATTR_CLIENT, new ClientForm());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_CLIENTS);
        return VIEW_FORM;
    }

    @PostMapping("/new")
    public String create(
            @Valid @ModelAttribute(ATTR_CLIENT) ClientForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_CLIENTS);
            return VIEW_FORM;
        }

        try {
            Client client = toEntity(form);
            Client saved = clientService.create(client);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Klien berhasil ditambahkan");
            return REDIRECT_CLIENTS_PREFIX + saved.getCode();
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("code", "duplicate", e.getMessage());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_CLIENTS);
            return VIEW_FORM;
        }
    }

    @GetMapping("/{code}")
    public String detail(@PathVariable String code, Model model) {
        Client client = clientService.findByCode(code);
        model.addAttribute(ATTR_CLIENT, client);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_CLIENTS);

        // Fetch invoices separately (avoids MultipleBagFetchException with projects)
        List<Invoice> invoices = invoiceRepository.findByClientId(client.getId());
        model.addAttribute("invoices", invoices);

        if (client.getNpwp() != null && !client.getNpwp().isBlank()) {
            List<TaxTransactionDetail> taxDetails = taxTransactionDetailRepository
                    .findByCounterpartyNpwp(client.getNpwp());
            long efakturCount = taxDetails.stream().filter(TaxTransactionDetail::isEFaktur).count();
            long ebupotCount = taxDetails.stream().filter(TaxTransactionDetail::isEBupot).count();
            BigDecimal totalDpp = taxDetails.stream()
                    .map(t -> t.getDpp() != null ? t.getDpp() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalPpn = taxDetails.stream()
                    .map(t -> t.getPpn() != null ? t.getPpn() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalPph = taxDetails.stream()
                    .map(t -> t.getTaxAmount() != null ? t.getTaxAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            model.addAttribute("taxDetails", taxDetails);
            model.addAttribute("efakturCount", efakturCount);
            model.addAttribute("ebupotCount", ebupotCount);
            model.addAttribute("totalDpp", totalDpp);
            model.addAttribute("totalPpn", totalPpn);
            model.addAttribute("totalPph", totalPph);
        }

        return "clients/detail";
    }

    @GetMapping("/{code}/edit")
    public String editForm(@PathVariable String code, Model model) {
        Client existing = clientService.findByCode(code);
        model.addAttribute(ATTR_CLIENT, toForm(existing));
        model.addAttribute(ATTR_CLIENT_CODE, code);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_CLIENTS);
        return VIEW_FORM;
    }

    @PostMapping("/{code}")
    public String update(
            @PathVariable String code,
            @Valid @ModelAttribute(ATTR_CLIENT) ClientForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            Client existing = clientService.findByCode(code);
            form.setId(existing.getId());
            model.addAttribute(ATTR_CLIENT_CODE, code);
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_CLIENTS);
            return VIEW_FORM;
        }

        try {
            Client existing = clientService.findByCode(code);
            Client updated = toEntity(form);
            clientService.update(existing.getId(), updated);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Klien berhasil diperbarui");
            return REDIRECT_CLIENTS_PREFIX + form.getCode();
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("code", "duplicate", e.getMessage());
            Client existing = clientService.findByCode(code);
            form.setId(existing.getId());
            model.addAttribute(ATTR_CLIENT_CODE, code);
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_CLIENTS);
            return VIEW_FORM;
        }
    }

    @PostMapping("/{code}/deactivate")
    public String deactivate(
            @PathVariable String code,
            RedirectAttributes redirectAttributes) {

        Client client = clientService.findByCode(code);
        clientService.deactivate(client.getId());
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Klien berhasil dinonaktifkan");
        return REDIRECT_CLIENTS_PREFIX + code;
    }

    @PostMapping("/{code}/activate")
    public String activate(
            @PathVariable String code,
            RedirectAttributes redirectAttributes) {

        Client client = clientService.findByCode(code);
        clientService.activate(client.getId());
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Klien berhasil diaktifkan");
        return REDIRECT_CLIENTS_PREFIX + code;
    }
}
