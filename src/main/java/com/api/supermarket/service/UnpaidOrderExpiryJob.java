package com.api.supermarket.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.api.supermarket.repository.SalesOrderRepository;

@Component
public class UnpaidOrderExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(UnpaidOrderExpiryJob.class);
    private final SalesOrderRepository orderRepository;
    private final SalesOrderService orderService;

    public UnpaidOrderExpiryJob(SalesOrderRepository orderRepository, SalesOrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${business.payment-expiry-scan-ms:60000}")
    public void releaseExpiredReservations() {
        for (Long orderId : orderRepository.findExpiredPaymentOrderIds(LocalDateTime.now(),
                org.springframework.data.domain.PageRequest.of(0, 100))) {
            try {
                // Mỗi đơn có transaction riêng; khóa đơn tránh đua với xác nhận thanh toán.
                orderService.expireUnpaidOrder(orderId);
            } catch (RuntimeException ex) {
                log.error("Không thể giải phóng giữ hàng cho đơn hết hạn {}", orderId, ex);
            }
        }
    }
}
