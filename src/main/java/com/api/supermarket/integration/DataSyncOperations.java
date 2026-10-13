package com.api.supermarket.integration;

import com.api.supermarket.repository.*;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DataSyncOperations {
    private final ProductRepository products;
    private final PaymentRecordRepository payments;
    private final StockMovementRepository movements;
    private final DataSyncOutboxRepository outbox;
    private final DataSyncPublisher publisher;
    private final TransactionTemplate transactions;

    public DataSyncOperations(ProductRepository products, PaymentRecordRepository payments,
            StockMovementRepository movements, DataSyncOutboxRepository outbox,
            DataSyncPublisher publisher, PlatformTransactionManager manager) {
        this.products = products;
        this.payments = payments;
        this.movements = movements;
        this.outbox = outbox;
        this.publisher = publisher;
        transactions = new TransactionTemplate(manager);
    }

    public int enqueueCatalog() {
        int total = 0;
        for (int page = 0; ; page++) {
            int current = page;
            Integer count = transactions.execute(status -> {
                var result = products.findAll(PageRequest.of(current, 200, Sort.by("productId")));
                result.forEach(product -> publisher.catalogSnapshot(product.getProductId()));
                return result.getNumberOfElements();
            });
            total += count;
            if (count < 200) return total;
        }
    }

    // Only source records that really exist are backfilled; past order transitions are not invented.
    public void enqueueHistory() {
        for (int page = 0; ; page++) {
            int current = page;
            Integer count = transactions.execute(status -> {
                var result = payments.findAll(PageRequest.of(current, 200, Sort.by("paymentRecordId")));
                result.forEach(publisher::payment);
                return result.getNumberOfElements();
            });
            if (count < 200) break;
        }
        for (int page = 0; ; page++) {
            int current = page;
            Integer count = transactions.execute(status -> {
                var result = movements.findAll(PageRequest.of(current, 200, Sort.by("movementId")));
                result.forEach(movement -> publisher.movement(movement, false));
                return result.getNumberOfElements();
            });
            if (count < 200) break;
        }
    }

    public Map<String, Object> status() {
        Map<String, Long> mongo = counts("MONGODB");
        Map<String, Long> astra = counts("ASTRA");
        return Map.of(
                "mongodb", mongo,
                "astra", astra,
                "mongodbPendingOutbox", mongo.get("pending"),
                "mongodbSentOutbox", mongo.get("sent"),
                "astraPendingOutbox", astra.get("pending"),
                "astraSentOutbox", astra.get("sent"),
                "mysqlConnected", true,
                "drillStatus", "Sẵn sàng"
        );
    }

    private Map<String, Long> counts(String destination) {
        return Map.of("pending", outbox.countByDestinationAndStatus(destination, "PENDING"),
                "sent", outbox.countByDestinationAndStatus(destination, "SENT"));
    }
}
