package com.ecommerce.order_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
public class CartItemResponse {
    private Long id;
    private Long productId;
    private String productName;   // fetched live via Feign
    private BigDecimal price;     // fetched live via Feign
    private Integer quantity;
    private BigDecimal subtotal;  // price * quantity, calculated on the fly
}