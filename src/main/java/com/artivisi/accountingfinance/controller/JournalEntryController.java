package com.artivisi.accountingfinance.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/journals")
public class JournalEntryController {

    @GetMapping
    public String list(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String search,
            Model model) {
        model.addAttribute("currentPage", "journals");
        model.addAttribute("selectedAccount", accountId);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("searchQuery", search);
        return "journals/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model) {
        model.addAttribute("currentPage", "journals");
        model.addAttribute("journalId", id);
        return "journals/detail";
    }

    @GetMapping("/ledger/{accountId}")
    public String accountLedger(@PathVariable String accountId, Model model) {
        model.addAttribute("currentPage", "journals");
        model.addAttribute("accountId", accountId);
        return "journals/ledger";
    }
}
