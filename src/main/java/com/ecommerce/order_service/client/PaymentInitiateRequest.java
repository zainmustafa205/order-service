package com.ecommerce.order_service.client;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitiateRequest {
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private String currency;
    private String method;
    private String idempotencyKey;
}