package com.ecommerce.order_service.controller;

import com.ecommerce.order_service.client.PaymentResponse;
import com.ecommerce.order_service.dto.request.PayOrderRequest;
import com.ecommerce.order_service.dto.response.OrderResponse;
import com.ecommerce.order_service.entity.OrderStatus;
import com.ecommerce.order_service.service.OrderService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        OrderResponse response = orderService.placeOrder(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderById(
            @PathVariable Long orderId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(orderService.getOrderById(orderId, userId));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getMyOrders(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(orderService.getOrdersByUser(userId));
    }

    @PutMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestParam OrderStatus status) {
        return ResponseEntity.ok(orderService.updateOrderStatus(orderId, status));
    }

    @PostMapping("/{orderId}/pay")
    public ResponseEntity<PaymentResponse> payForOrder(
            @PathVariable Long orderId,
            @Valid 
            @RequestBody PayOrderRequest request,
            Authentication authentication) {

            Long userId = (Long) authentication.getPrincipal();
            PaymentResponse response = orderService.payForOrder(orderId, userId, request);
        return ResponseEntity.ok(response);
    }
}