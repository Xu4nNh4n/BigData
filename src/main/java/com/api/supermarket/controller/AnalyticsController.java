package com.api.supermarket.controller;

import com.api.supermarket.integration.DrillAnalyticsService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class AnalyticsController {
    private final DrillAnalyticsService reports;

    public AnalyticsController(DrillAnalyticsService reports) { this.reports = reports; }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.dashboard(from, to);
    }

    @GetMapping("/inventory")
    public Map<String, Object> inventory(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return reports.inventory(page, size);
    }

    @GetMapping("/cash-flow")
    public Map<String, Object> cashFlow(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.cashFlow(from, to);
    }

    @GetMapping("/reconciliation")
    public Map<String, Object> reconciliation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.reconciliation(from, to);
    }

}
