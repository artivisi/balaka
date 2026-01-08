package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequestMapping("/clients")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.CLIENT_VIEW + "')")
public class ClientController {

    private static final String ATTR_CLIENT = "client";
    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String REDIRECT_CLIENTS_PREFIX = "redirect:/clients/";
    private static final String VIEW_FORM = "clients/form";

    private final ClientService clientService;

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
        model.addAttribute(ATTR_CLIENT, new Client());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_CLIENTS);
        return VIEW_FORM;
    }

    @PostMapping("/new")
    public String create(
            @Valid @ModelAttribute(ATTR_CLIENT) Client client,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_CLIENTS);
            return VIEW_FORM;
        }

        try {
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
        return "clients/detail";
    }

    @GetMapping("/{code}/edit")
    public String editForm(@PathVariable String code, Model model) {
        Client client = clientService.findByCode(code);
        model.addAttribute(ATTR_CLIENT, client);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_CLIENTS);
        return VIEW_FORM;
    }

    @PostMapping("/{code}")
    public String update(
            @PathVariable String code,
            @Valid Client client,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            Client existing = clientService.findByCode(code);
            client.setId(existing.getId());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_CLIENTS);
            return VIEW_FORM;
        }

        try {
            Client existing = clientService.findByCode(code);
            clientService.update(existing.getId(), client);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Klien berhasil diperbarui");
            return REDIRECT_CLIENTS_PREFIX + client.getCode();
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("code", "duplicate", e.getMessage());
            Client existing = clientService.findByCode(code);
            client.setId(existing.getId());
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
