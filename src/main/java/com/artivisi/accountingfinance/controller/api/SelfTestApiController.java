package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.enums.SelfTestLine;
import com.artivisi.accountingfinance.service.SelfTestService;
import com.artivisi.accountingfinance.service.SelfTestService.Report;
import com.artivisi.accountingfinance.service.SelfTestService.Trace;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only tax-office self-test (issue #45). Explanations are written on the web page. */
@RestController
@RequestMapping("/api/self-test")
@Tag(name = "Tax Self-Test", description = "Yearly cross-checks a tax office runs from the annual return: book figure, "
        + "tax figure, difference and explanation per line; rows behind each figure")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SCOPE_self-test:read')")
public class SelfTestApiController {

    private final SelfTestService selfTestService;

    @GetMapping
    @Operation(summary = "Self-test of a year",
            description = "lines[] in fixed order; per-masa lines have a month 0 total followed by months 1-12. "
                    + "taxFigure null: no counterpart in the return, or NOT_CONFIGURED.")
    public ResponseEntity<Report> report(@RequestParam int year) {
        return ResponseEntity.ok(selfTestService.report(year));
    }

    @GetMapping("/trace")
    @Operation(summary = "Rows behind one line",
            description = "bookRows add up to bookFigure and taxRows to taxFigure. month 0 is the whole year; 1-12 only "
                    + "on per-masa lines.")
    @ApiResponse(responseCode = "400", description = "Month not valid for the line")
    @ApiResponse(responseCode = "409", description = "No account carries the role the line needs")
    public ResponseEntity<Trace> trace(@RequestParam int year, @RequestParam SelfTestLine line, @RequestParam int month) {
        return ResponseEntity.ok(selfTestService.trace(year, line, month));
    }
}
