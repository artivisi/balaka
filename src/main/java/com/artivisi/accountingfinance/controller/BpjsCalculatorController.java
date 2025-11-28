package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.service.BpjsCalculationService;
import com.artivisi.accountingfinance.service.BpjsCalculationService.BpjsCalculationResult;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@Controller
@RequestMapping("/bpjs-calculator")
public class BpjsCalculatorController {

    private final BpjsCalculationService bpjsCalculationService;

    public BpjsCalculatorController(BpjsCalculationService bpjsCalculationService) {
        this.bpjsCalculationService = bpjsCalculationService;
    }

    @GetMapping
    public String showCalculator(Model model) {
        model.addAttribute("currentPage", "bpjs-calculator");
        model.addAttribute("riskClasses", getRiskClassOptions());
        return "bpjs-calculator/index";
    }

    @PostMapping("/calculate")
    public String calculate(
            @RequestParam BigDecimal salary,
            @RequestParam(defaultValue = "1") Integer riskClass,
            Model model) {

        BpjsCalculationResult result = bpjsCalculationService.calculate(salary, riskClass);

        model.addAttribute("currentPage", "bpjs-calculator");
        model.addAttribute("riskClasses", getRiskClassOptions());
        model.addAttribute("salary", salary);
        model.addAttribute("selectedRiskClass", riskClass);
        model.addAttribute("result", result);

        // Add ceiling info
        model.addAttribute("kesehatanCeiling", BpjsCalculationService.BPJS_KESEHATAN_CEILING);
        model.addAttribute("jpCeiling", BpjsCalculationService.BPJS_JP_CEILING);
        model.addAttribute("salaryExceedsKesehatanCeiling",
            salary.compareTo(BpjsCalculationService.BPJS_KESEHATAN_CEILING) > 0);
        model.addAttribute("salaryExceedsJpCeiling",
            salary.compareTo(BpjsCalculationService.BPJS_JP_CEILING) > 0);

        return "bpjs-calculator/index";
    }

    private record RiskClassOption(int value, String label, String rate) {}

    private RiskClassOption[] getRiskClassOptions() {
        return new RiskClassOption[] {
            new RiskClassOption(1, "Kelas 1 - Risiko Sangat Rendah (IT, Jasa)", "0.24%"),
            new RiskClassOption(2, "Kelas 2 - Risiko Rendah (Retail, Perdagangan)", "0.54%"),
            new RiskClassOption(3, "Kelas 3 - Risiko Sedang (Manufaktur Ringan)", "0.89%"),
            new RiskClassOption(4, "Kelas 4 - Risiko Tinggi (Konstruksi)", "1.27%"),
            new RiskClassOption(5, "Kelas 5 - Risiko Sangat Tinggi (Pertambangan)", "1.74%")
        };
    }
}
