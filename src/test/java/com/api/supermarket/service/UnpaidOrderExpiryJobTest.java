package com.api.supermarket.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.api.supermarket.repository.SalesOrderRepository;

@ExtendWith(MockitoExtension.class)
public class UnpaidOrderExpiryJobTest {

    @Mock
    private SalesOrderRepository orderRepository;

    @Mock
    private SalesOrderService orderService;

    @InjectMocks
    private UnpaidOrderExpiryJob job;

    @Test
    void releaseExpiredReservations_Success() {
        when(orderRepository.findExpiredPaymentOrderIds(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(100L, 101L));

        job.releaseExpiredReservations();

        verify(orderService).expireUnpaidOrder(100L);
        verify(orderService).expireUnpaidOrder(101L);
    }

    @Test
    void releaseExpiredReservations_HandlesExceptionGracefully() {
        when(orderRepository.findExpiredPaymentOrderIds(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(200L, 201L));

        doThrow(new RuntimeException("DB lock failure")).when(orderService).expireUnpaidOrder(200L);

        job.releaseExpiredReservations();

        verify(orderService).expireUnpaidOrder(200L);
        verify(orderService).expireUnpaidOrder(201L);
    }
}
