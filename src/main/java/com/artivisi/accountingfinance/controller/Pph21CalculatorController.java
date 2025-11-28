package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.PtkpStatus;
import com.artivisi.accountingfinance.service.Pph21CalculationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/pph21-calculator")
public class Pph21CalculatorController {

    private final Pph21CalculationService pph21CalculationService;

    public Pph21CalculatorController(Pph21CalculationService pph21CalculationService) {
        this.pph21CalculationService = pph21CalculationService;
    }

    @GetMapping
    public String showCalculator(Model model) {
        model.addAttribute("ptkpStatuses", Arrays.asList(PtkpStatus.values()));
        model.addAttribute("selectedPtkpStatus", PtkpStatus.TK_0.name());
        return "pph21-calculator/index";
    }

    @PostMapping("/calculate")
    public String calculate(
            @RequestParam BigDecimal salary,
            @RequestParam(defaultValue = "TK_0") String ptkpStatus,
            @RequestParam(defaultValue = "true") boolean hasNpwp,
            Model model
    ) {
        PtkpStatus status = PtkpStatus.valueOf(ptkpStatus);
        var result = pph21CalculationService.calculate(salary, status, hasNpwp);

        model.addAttribute("ptkpStatuses", Arrays.asList(PtkpStatus.values()));
        model.addAttribute("salary", salary);
        model.addAttribute("selectedPtkpStatus", ptkpStatus);
        model.addAttribute("hasNpwp", hasNpwp);
        model.addAttribute("result", result);
        model.addAttribute("selectedPtkpStatusEnum", status);

        // Tax bracket information for display
        model.addAttribute("taxBrackets", getTaxBrackets());

        return "pph21-calculator/index";
    }

    private List<TaxBracketInfo> getTaxBrackets() {
        return List.of(
            new TaxBracketInfo("0 - 60,000,000", "5%"),
            new TaxBracketInfo("60,000,001 - 250,000,000", "15%"),
            new TaxBracketInfo("250,000,001 - 500,000,000", "25%"),
            new TaxBracketInfo("500,000,001 - 5,000,000,000", "30%"),
            new TaxBracketInfo("> 5,000,000,000", "35%")
        );
    }

    public record TaxBracketInfo(String range, String rate) {}
}
