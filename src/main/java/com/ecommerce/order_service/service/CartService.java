package com.ecommerce.order_service.service;

import com.ecommerce.order_service.client.ProductClient;
import com.ecommerce.order_service.client.ProductClientResponse;
import com.ecommerce.order_service.dto.request.AddToCartRequest;
import com.ecommerce.order_service.dto.request.UpdateCartItemRequest;
import com.ecommerce.order_service.dto.response.CartItemResponse;
import com.ecommerce.order_service.dto.response.CartResponse;
import com.ecommerce.order_service.entity.Cart;
import com.ecommerce.order_service.entity.CartItem;
import com.ecommerce.order_service.exception.ResourceNotFoundException;
import com.ecommerce.order_service.repository.CartRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductClient productClient;

    public CartService(CartRepository cartRepository, ProductClient productClient) {
        this.cartRepository = cartRepository;
        this.productClient = productClient;
    }

    // Fetch existing cart or create a new empty one for the user
    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Cart newCart = new Cart();
                    newCart.setUserId(userId);
                    return cartRepository.save(newCart);
                });
    }

    public CartResponse getCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        return buildCartResponse(cart);
    }

    public CartResponse addToCart(Long userId, AddToCartRequest request) {
        // Verify product exists and is active via Feign call
        ProductClientResponse product = productClient.getProductById(request.getProductId());

        if (product == null || Boolean.FALSE.equals(product.getActive())) {
            throw new ResourceNotFoundException("Product not found or inactive with id: " + request.getProductId());
        }

        Cart cart = getOrCreateCart(userId);

        // If product already in cart, increment quantity instead of duplicating
        CartItem existingItem = cart.getCartItems().stream()
                .filter(item -> item.getProductId().equals(request.getProductId()))
                .findFirst()
                .orElse(null);

        if (existingItem != null) {
            existingItem.setQuantity(existingItem.getQuantity() + request.getQuantity());
        } else {
            CartItem newItem = new CartItem();
            newItem.setProductId(request.getProductId());
            newItem.setQuantity(request.getQuantity());
            cart.addCartItem(newItem);
        }

        Cart savedCart = cartRepository.save(cart);
        return buildCartResponse(savedCart);
    }

    public CartResponse updateCartItem(Long userId, Long cartItemId, UpdateCartItemRequest request) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user: " + userId));

        CartItem item = cart.getCartItems().stream()
                .filter(ci -> ci.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with id: " + cartItemId));

        item.setQuantity(request.getQuantity());

        Cart savedCart = cartRepository.save(cart);
        return buildCartResponse(savedCart);
    }

    public CartResponse removeCartItem(Long userId, Long cartItemId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user: " + userId));

        boolean removed = cart.getCartItems().removeIf(item -> item.getId().equals(cartItemId));

        if (!removed) {
            throw new ResourceNotFoundException("Cart item not found with id: " + cartItemId);
        }

        Cart savedCart = cartRepository.save(cart);
        return buildCartResponse(savedCart);
    }

    public void clearCart(Long userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user: " + userId));

        cart.getCartItems().clear();
        cartRepository.save(cart);
    }

    // Builds the response DTO with LIVE price/name fetched from product-service
    private CartResponse buildCartResponse(Cart cart) {
        List<CartItemResponse> itemResponses = cart.getCartItems().stream()
                .map(item -> {
                    ProductClientResponse product = productClient.getProductById(item.getProductId());
                    BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

                    return new CartItemResponse(
                            item.getId(),
                            item.getProductId(),
                            product.getName(),
                            product.getPrice(),
                            item.getQuantity(),
                            subtotal
                    );
                })
                .collect(Collectors.toList());

        BigDecimal total = itemResponses.stream()
                .map(CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(cart.getId(), cart.getUserId(), itemResponses, total);
    }
}