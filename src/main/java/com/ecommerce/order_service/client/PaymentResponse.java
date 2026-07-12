package com.ecommerce.order_service.client;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private Long id;
    private Long orderId;
    private BigDecimal amount;
    private String currency;
    private String method;
    private String status;
    private String gatewayReferenceId;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}