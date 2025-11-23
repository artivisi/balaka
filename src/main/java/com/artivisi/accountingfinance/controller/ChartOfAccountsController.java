package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.dto.ChartOfAccountDto;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.service.ChartOfAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class ChartOfAccountsController {

    private final ChartOfAccountService chartOfAccountService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("currentPage", "accounts");
        model.addAttribute("accounts", chartOfAccountService.findRootAccounts());
        return "accounts/list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("currentPage", "accounts");
        model.addAttribute("isEdit", false);
        model.addAttribute("accountTypes", AccountType.values());
        return "accounts/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        model.addAttribute("currentPage", "accounts");
        model.addAttribute("isEdit", true);
        model.addAttribute("account", chartOfAccountService.findById(id));
        model.addAttribute("accountTypes", AccountType.values());
        return "accounts/form";
    }

    // REST API Endpoints

    @GetMapping("/api")
    @ResponseBody
    public ResponseEntity<List<ChartOfAccount>> apiList(
            @RequestParam(required = false) AccountType type,
            @RequestParam(required = false, defaultValue = "true") Boolean active) {
        List<ChartOfAccount> accounts;
        if (type != null) {
            accounts = chartOfAccountService.findByAccountType(type);
        } else if (active) {
            accounts = chartOfAccountService.findAll();
        } else {
            accounts = chartOfAccountService.findAllIncludingInactive();
        }
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/api/transactable")
    @ResponseBody
    public ResponseEntity<List<ChartOfAccount>> apiTransactable() {
        return ResponseEntity.ok(chartOfAccountService.findTransactableAccounts());
    }

    @GetMapping("/api/search")
    @ResponseBody
    public ResponseEntity<Page<ChartOfAccount>> apiSearch(
            @RequestParam String q,
            @RequestParam(required = false, defaultValue = "true") Boolean active,
            Pageable pageable) {
        return ResponseEntity.ok(chartOfAccountService.search(q, active, pageable));
    }

    @GetMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<ChartOfAccount> apiGet(@PathVariable UUID id) {
        return ResponseEntity.ok(chartOfAccountService.findById(id));
    }

    @GetMapping("/api/{id}/children")
    @ResponseBody
    public ResponseEntity<List<ChartOfAccount>> apiGetChildren(@PathVariable UUID id) {
        return ResponseEntity.ok(chartOfAccountService.findByParentId(id));
    }

    @PostMapping("/api")
    @ResponseBody
    public ResponseEntity<ChartOfAccount> apiCreate(@Valid @RequestBody ChartOfAccountDto dto) {
        ChartOfAccount account = mapDtoToEntity(dto);
        return ResponseEntity.ok(chartOfAccountService.create(account));
    }

    @PutMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<ChartOfAccount> apiUpdate(@PathVariable UUID id, @Valid @RequestBody ChartOfAccountDto dto) {
        ChartOfAccount account = mapDtoToEntity(dto);
        return ResponseEntity.ok(chartOfAccountService.update(id, account));
    }

    @PostMapping("/api/{id}/activate")
    @ResponseBody
    public ResponseEntity<Void> apiActivate(@PathVariable UUID id) {
        chartOfAccountService.activate(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/{id}/deactivate")
    @ResponseBody
    public ResponseEntity<Void> apiDeactivate(@PathVariable UUID id) {
        chartOfAccountService.deactivate(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<Void> apiDelete(@PathVariable UUID id) {
        chartOfAccountService.delete(id);
        return ResponseEntity.ok().build();
    }

    private ChartOfAccount mapDtoToEntity(ChartOfAccountDto dto) {
        ChartOfAccount account = new ChartOfAccount();
        account.setAccountCode(dto.accountCode());
        account.setAccountName(dto.accountName());
        account.setAccountType(dto.accountType());
        account.setNormalBalance(dto.normalBalance());
        account.setIsHeader(dto.isHeader() != null ? dto.isHeader() : false);
        account.setActive(dto.active() != null ? dto.active() : true);
        account.setDescription(dto.description());

        if (dto.parentId() != null) {
            ChartOfAccount parent = new ChartOfAccount();
            parent.setId(dto.parentId());
            account.setParent(parent);
        }

        return account;
    }
}
