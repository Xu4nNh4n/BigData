package com.api.supermarket.domain;

import java.math.BigDecimal;

/** Rules shared by relational and document-storage implementations. */
public final class ReturnPolicy {
    private ReturnPolicy() {}
    public static BigDecimal refundAmount(int purchased, int alreadyReturned, int requested, BigDecimal unitPrice) {
        if (purchased <= 0 || alreadyReturned < 0 || alreadyReturned > purchased
                || requested <= 0 || requested > purchased - alreadyReturned) {
            throw new IllegalArgumentException("Số lượng trả vượt số lượng còn lại của mặt hàng");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Đơn giá gốc không hợp lệ");
        }
        return unitPrice.multiply(BigDecimal.valueOf(requested));
    }
}
