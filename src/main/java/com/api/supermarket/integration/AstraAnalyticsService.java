package com.api.supermarket.integration;

import com.api.supermarket.exception.BadRequestException;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AstraAnalyticsService {
    private static final Map<String, String> LEGACY_PRODUCT_CODES = Map.ofEntries(
            Map.entry("SKU-WATER-500", "1"), Map.entry("SKU-MILK-1L", "2"),
            Map.entry("SKU-NOODLE-BEEF", "3"), Map.entry("SKU-POT-18CM", "4"),
            Map.entry("SKU-APPLE-RED", "5"), Map.entry("SKU-COLA-330", "6"),
            Map.entry("SKU-ORANGE-JUICE-1L", "7"), Map.entry("SKU-GREEN-TEA-455", "8"),
            Map.entry("SKU-COFFEE-3IN1-20", "9"), Map.entry("SKU-RICE-JASMINE-5KG", "10"),
            Map.entry("SKU-SOYOIL-1L", "11"), Map.entry("SKU-SUGAR-1KG", "12"),
            Map.entry("SKU-BUTTER-COOKIE-300", "13"), Map.entry("SKU-YOGURT-SWEET-4", "14"),
            Map.entry("SKU-CHEDDAR-SLICE-200", "15"), Map.entry("SKU-WALNUT-MILK-1L", "16"),
            Map.entry("SKU-BUTTER-UNSALTED-200", "17"), Map.entry("SKU-DISHWASH-750", "18"),
            Map.entry("SKU-TOILET-PAPER-10", "19"), Map.entry("SKU-LAUNDRY-3.2KG", "20"),
            Map.entry("SKU-LED-BULB-9W", "21"), Map.entry("SKU-BANANA-1KG", "22"),
            Map.entry("SKU-CARROT-1KG", "23"), Map.entry("SKU-POTATO-1KG", "24"),
            Map.entry("SKU-ORANGE-1KG", "25"));
    private static final Map<String, String> LEGACY_BATCH_CODES = Map.ofEntries(
            Map.entry("SEED-WATER-20261001", "1"), Map.entry("SEED-WATER-20260901", "2"),
            Map.entry("SEED-MILK-20261001", "3"), Map.entry("SEED-NOODLE-20261001", "4"),
            Map.entry("SEED-POT-20261001", "5"), Map.entry("SEED-APPLE-20261001", "6"),
            Map.entry("BIGDATA-WATER-500-20261008-1", "7"), Map.entry("BIGDATA-WATER-500-20261008-2", "8"),
            Map.entry("BIGDATA-MILK-1L-20261008-1", "9"), Map.entry("BIGDATA-MILK-1L-20261008-2", "10"),
            Map.entry("BIGDATA-NOODLE-BEEF-20261008-1", "11"), Map.entry("BIGDATA-NOODLE-BEEF-20261008-2", "12"),
            Map.entry("BIGDATA-POT-18CM-20261008-1", "13"), Map.entry("BIGDATA-POT-18CM-20261008-2", "14"),
            Map.entry("BIGDATA-APPLE-RED-20261008-1", "15"), Map.entry("BIGDATA-APPLE-RED-20261008-2", "16"),
            Map.entry("OPENING-12", "17"), Map.entry("OPENING-13", "18"), Map.entry("OPENING-14", "19"),
            Map.entry("OPENING-15", "20"), Map.entry("OPENING-16", "21"), Map.entry("OPENING-17", "22"),
            Map.entry("OPENING-18", "23"), Map.entry("OPENING-19", "24"), Map.entry("OPENING-20", "25"),
            Map.entry("OPENING-21", "26"), Map.entry("OPENING-22", "27"), Map.entry("OPENING-23", "28"),
            Map.entry("OPENING-24", "29"), Map.entry("OPENING-25", "30"), Map.entry("OPENING-26", "31"),
            Map.entry("OPENING-27", "32"), Map.entry("OPENING-28", "33"), Map.entry("OPENING-29", "34"),
            Map.entry("OPENING-30", "35"), Map.entry("OPENING-31", "36"));
    private final ObjectProvider<CqlSession> sessions;
    private final ObjectMapper json;
    private final Path directory;

    public AstraAnalyticsService(ObjectProvider<CqlSession> sessions, ObjectMapper json,
            @Value("${integration.analytics-directory:analytics/cassandra}") String directory) {
        this.sessions = sessions;
        this.json = json;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public synchronized Map<String, Object> export(LocalDate date) {
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            String filename = "events-" + date + ".json";
            Path target = directory.resolve(filename);
            temporary = Files.createTempFile(directory, "events-", ".tmp");
            long count = 0;
            var statement = SimpleStatement.builder("SELECT * FROM business_events_by_day WHERE event_date = ?")
                    .addPositionalValue(date).setPageSize(500).build();
            // Each JSON line is one real Astra row; paging avoids loading an entire day in memory.
            try (var writer = Files.newBufferedWriter(temporary)) {
                for (Row row : session().execute(statement)) {
                    writer.write(json.writeValueAsString(map(row)));
                    writer.newLine();
                    count++;
                }
            }
            if (count == 0) {
                Files.deleteIfExists(temporary);
                throw new BadRequestException("Ngày này chưa có sự kiện trong Astra; không tạo file JSON rỗng");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return Map.of("date", date.toString(), "rows", count,
                    "file", "/analytics/cassandra/" + filename, "source", "ASTRA_DB");
        } catch (IOException ex) {
            throw new IllegalStateException("Không thể xuất dữ liệu Astra ra JSON", ex);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }

    private CqlSession session() {
        CqlSession session = sessions.getIfAvailable();
        if (session == null) throw new BadRequestException("Chưa bật ASTRA_ENABLED");
        return session;
    }

    private Map<String, Object> map(Row row) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_date", row.getLocalDate("event_date").toString());
        event.put("event_time", row.getInstant("event_time").toString());
        event.put("event_id", row.getUuid("event_id").toString());
        event.put("event_type", row.getString("event_type"));
        Long orderId = row.isNull("order_id") ? null : row.getLong("order_id");
        event.put("order_id", orderId);
        String productSku = row.isNull("product_sku") ? null : row.getString("product_sku");
        event.put("product_sku", productSku == null ? null : LEGACY_PRODUCT_CODES.getOrDefault(productSku, productSku));
        event.put("quantity", row.isNull("quantity") ? null : row.getInt("quantity"));
        event.put("amount", row.getBigDecimal("amount"));
        event.put("payment_method", row.getString("payment_method"));
        event.put("status", row.getString("status"));
        event.put("metadata", normalizeMetadata(row.getString("metadata"), orderId));
        return event;
    }

    private String normalizeMetadata(String value, Long orderId) {
        if (value == null || value.isBlank()) return value;
        try {
            JsonNode node = json.readTree(value);
            if (!(node instanceof ObjectNode object)) return value;
            if (orderId != null && object.has("orderCode")) object.put("orderCode", String.valueOf(orderId));
            if (object.has("batchCode")) {
                String batchCode = object.path("batchCode").asText();
                object.put("batchCode", LEGACY_BATCH_CODES.getOrDefault(batchCode, batchCode));
            }
            return json.writeValueAsString(object);
        } catch (Exception ignored) {
            return value;
        }
    }
}
