package com.ecommerce.order_service.service;

import com.ecommerce.order_service.client.ProductClient;
import com.ecommerce.order_service.client.ProductClientResponse;
import com.ecommerce.order_service.client.UserClient;
import com.ecommerce.order_service.client.UserClientResponse;
import com.ecommerce.order_service.dto.response.OrderItemResponse;
import com.ecommerce.order_service.dto.response.OrderResponse;
import com.ecommerce.order_service.entity.Cart;
import com.ecommerce.order_service.entity.CartItem;
import com.ecommerce.order_service.entity.Order;
import com.ecommerce.order_service.entity.OrderItem;
import com.ecommerce.order_service.entity.OrderStatus;
import com.ecommerce.order_service.exception.InsufficientStockException;
import com.ecommerce.order_service.exception.ResourceNotFoundException;
import com.ecommerce.order_service.repository.CartRepository;
import com.ecommerce.order_service.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductClient productClient;
    private final UserClient userClient;

    public OrderService(OrderRepository orderRepository, CartRepository cartRepository,
                         ProductClient productClient, UserClient userClient) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.productClient = productClient;
        this.userClient = userClient;
    }

    @Transactional
    public OrderResponse placeOrder(Long userId) {

        // Step 1: Verify user exists and is active
        UserClientResponse user = userClient.getUserById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("User not found or inactive with id: " + userId);
        }

        // Step 2: Fetch user's cart
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user: " + userId));

        if (cart.getCartItems().isEmpty()) {
            throw new IllegalStateException("Cannot place order with an empty cart");
        }

        // Step 3: Build the Order with price snapshots + reduce stock as we go
        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);

        List<Long> stockReducedProductIds = new ArrayList<>();
        List<Integer> stockReducedQuantities = new ArrayList<>();

        try {
            for (CartItem cartItem : cart.getCartItems()) {
                ProductClientResponse product = productClient.getProductById(cartItem.getProductId());

                if (product == null || Boolean.FALSE.equals(product.getActive())) {
                    throw new ResourceNotFoundException("Product not found or inactive with id: " + cartItem.getProductId());
                }

                if (product.getStockQuantity() < cartItem.getQuantity()) {
                    throw new InsufficientStockException(
                            "Insufficient stock for product: " + product.getName() +
                            " (available: " + product.getStockQuantity() + ", requested: " + cartItem.getQuantity() + ")"
                    );
                }

                // Reduce stock immediately (synchronous consistency)
                productClient.reduceStock(cartItem.getProductId(), cartItem.getQuantity());
                stockReducedProductIds.add(cartItem.getProductId());
                stockReducedQuantities.add(cartItem.getQuantity());

                // Snapshot price and name at the time of purchase
                OrderItem orderItem = new OrderItem();
                orderItem.setProductId(product.getId());
                orderItem.setProductName(product.getName());
                orderItem.setPriceAtPurchase(product.getPrice());
                orderItem.setQuantity(cartItem.getQuantity());
                orderItem.setSubtotal(product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));

                order.addOrderItem(orderItem);
            }
        } catch (RuntimeException ex) {
            // Rollback: restore any stock that was already reduced before the failure
            for (int i = 0; i < stockReducedProductIds.size(); i++) {
                productClient.restoreStock(stockReducedProductIds.get(i), stockReducedQuantities.get(i));
            }
            throw ex;
        }

        // Step 4: Calculate total
        BigDecimal totalAmount = order.getOrderItems().stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(totalAmount);

        Order savedOrder = orderRepository.save(order);

        // Step 5: Clear the cart after successful order placement
        cart.getCartItems().clear();
        cartRepository.save(cart);

        return buildOrderResponse(savedOrder);
    }

    public OrderResponse getOrderById(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (!order.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Order not found with id: " + orderId);
        }

        return buildOrderResponse(order);
    }

    public List<OrderResponse> getOrdersByUser(Long userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(this::buildOrderResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        // If cancelling, restore stock for all items
        if (newStatus == OrderStatus.CANCELLED && order.getStatus() != OrderStatus.CANCELLED) {
            for (OrderItem item : order.getOrderItems()) {
                productClient.restoreStock(item.getProductId(), item.getQuantity());
            }
        }

        order.setStatus(newStatus);
        Order savedOrder = orderRepository.save(order);
        return buildOrderResponse(savedOrder);
    }

    private OrderResponse buildOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getOrderItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getProductId(),
                        item.getProductName(),
                        item.getPriceAtPurchase(),
                        item.getQuantity(),
                        item.getSubtotal()
                ))
                .collect(Collectors.toList());

        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                itemResponses,
                order.getCreatedAt()
        );
    }
}