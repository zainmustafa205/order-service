package com.ecommerce.order_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class CartResponse {
    private Long id;
    private Long userId;
    private List<CartItemResponse> cartItems;
    private BigDecimal totalAmount; // sum of all subtotals
}