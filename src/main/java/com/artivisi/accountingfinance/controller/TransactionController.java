package com.artivisi.accountingfinance.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/transactions")
public class TransactionController {

    @GetMapping
    public String list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            Model model) {
        model.addAttribute("currentPage", "transactions");
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("searchQuery", search);
        return "transactions/list";
    }

    @GetMapping("/new")
    public String create(@RequestParam(required = false) String templateId, Model model) {
        model.addAttribute("currentPage", "transactions");
        model.addAttribute("isEdit", false);
        model.addAttribute("templateId", templateId);
        return "transactions/form";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model) {
        model.addAttribute("currentPage", "transactions");
        model.addAttribute("transactionId", id);
        return "transactions/detail";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable String id, Model model) {
        model.addAttribute("currentPage", "transactions");
        model.addAttribute("isEdit", true);
        model.addAttribute("transactionId", id);
        return "transactions/form";
    }

    @GetMapping("/{id}/void")
    public String voidForm(@PathVariable String id, Model model) {
        model.addAttribute("currentPage", "transactions");
        model.addAttribute("transactionId", id);
        return "transactions/void";
    }
}
