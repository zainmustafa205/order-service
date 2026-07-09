package com.ecommerce.order_service.entity;

public enum OrderStatus {
    PENDING,
    PAYMENT_PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
