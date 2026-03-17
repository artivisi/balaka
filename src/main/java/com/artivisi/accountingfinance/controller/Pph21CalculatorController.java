package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.PtkpStatus;
import com.artivisi.accountingfinance.entity.TerCategory;
import com.artivisi.accountingfinance.service.Pph21CalculationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/pph21-calculator")
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.CALCULATOR_USE + "')")
public class Pph21CalculatorController {

    private final Pph21CalculationService pph21CalculationService;

    public Pph21CalculatorController(Pph21CalculationService pph21CalculationService) {
        this.pph21CalculationService = pph21CalculationService;
    }

    @GetMapping
    public String showCalculator(Model model) {
        model.addAttribute("ptkpStatuses", List.of(PtkpStatus.values()));
        model.addAttribute("selectedPtkpStatus", PtkpStatus.TK_0.name());
        return "pph21-calculator/index";
    }

    @PostMapping("/calculate")
    public String calculate(
            @RequestParam BigDecimal salary,
            @RequestParam(defaultValue = "TK_0") String ptkpStatus,
            Model model
    ) {
        PtkpStatus status = PtkpStatus.valueOf(ptkpStatus);
        var terResult = pph21CalculationService.calculateTer(salary, status);

        model.addAttribute("ptkpStatuses", List.of(PtkpStatus.values()));
        model.addAttribute("salary", salary);
        model.addAttribute("selectedPtkpStatus", ptkpStatus);
        model.addAttribute("terResult", terResult);
        model.addAttribute("selectedPtkpStatusEnum", status);
        model.addAttribute("terCategory", TerCategory.fromPtkpStatus(status));

        return "pph21-calculator/index";
    }
}
