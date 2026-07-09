package com.ecommerce.order_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
public class OrderItemResponse {
    private Long productId;
    private String productName;
    private BigDecimal priceAtPurchase;
    private Integer quantity;
    private BigDecimal subtotal;
}