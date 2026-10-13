package com.api.supermarket.repository;

import com.api.supermarket.entity.DataSyncOutbox;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DataSyncOutboxRepository extends JpaRepository<DataSyncOutbox, Long> {
    boolean existsByEventKey(String eventKey);
    long countByDestinationAndStatus(String destination, String status);

    @Query("select o.id from DataSyncOutbox o where o.status = 'PENDING' "
            + "and o.nextAttemptAt <= :now and o.destination in :destinations order by o.id")
    List<Long> findReadyIds(@Param("now") LocalDateTime now,
            @Param("destinations") List<String> destinations, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from DataSyncOutbox o where o.id = :id")
    Optional<DataSyncOutbox> lockById(@Param("id") Long id);
}
