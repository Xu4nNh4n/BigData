package com.api.supermarket.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CheckoutCartRequest {
    @NotBlank
    @Size(max = 50)
    private String paymentMethod;
    @Size(max = 100)
    private String paymentReference;

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
    @Size(max = 255)
    private String note;
    @Size(max = 255)
    private String shippingAddress;
    @Size(max = 150)
    private String receiverName;
    @Size(max = 20)
    private String receiverPhone;

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }
    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }
    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }
}
