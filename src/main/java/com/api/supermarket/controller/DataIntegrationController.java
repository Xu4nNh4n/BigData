package com.api.supermarket.controller;

import com.api.supermarket.integration.*;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integrations")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class DataIntegrationController {
    private final DataSyncOperations operations;
    private final DataSyncDispatcher dispatcher;
    private final AstraAnalyticsService analytics;

    public DataIntegrationController(DataSyncOperations operations, DataSyncDispatcher dispatcher,
            AstraAnalyticsService analytics) {
        this.operations = operations;
        this.dispatcher = dispatcher;
        this.analytics = analytics;
    }

    @GetMapping("/status")
    public Map<String, Object> status() { return operations.status(); }

    @PostMapping("/catalog/sync")
    public Map<String, Object> syncCatalog() { return Map.of("queued", operations.enqueueCatalog()); }

    @PostMapping("/dispatch")
    public Map<String, Object> dispatch() { return dispatcher.dispatch(); }

    @PostMapping("/astra/events/export")
    public Map<String, Object> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return analytics.export(date);
    }
}
